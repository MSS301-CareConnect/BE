package mss301.providerservice.service.Impl;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.AiClient;
import mss301.providerservice.client.MediaClient;
import mss301.providerservice.client.dto.CertificateCheckClientRequest;
import mss301.providerservice.client.dto.CertificateCheckClientResponse;
import mss301.providerservice.client.dto.CertificateImageClientRequest;
import mss301.providerservice.client.dto.DownloadUrlClientResponse;
import mss301.providerservice.client.dto.MediaClientResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.config.ProviderProperties;
import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.CertificateVerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificateAiExtractedResponse;
import mss301.providerservice.dto.response.CertificateAiWarningResponse;
import mss301.providerservice.dto.response.CertificatePendingAuditStatisticsResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.entity.ProviderMedia;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CertificateAiCheckStatus;
import mss301.providerservice.enums.CertificateRiskLevel;
import mss301.providerservice.enums.CertificateVerificationStatus;
import mss301.providerservice.enums.MediaOwnerType;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CertificateMapper;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.repository.projection.CertificateRiskCount;
import mss301.providerservice.repository.ProviderMediaRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.CertificateService;
import mss301.providerservice.util.ProviderTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CertificateServiceImpl implements CertificateService {

    // Lần quét nhận việc QUEUED đến hạn, và PROCESSING quá lease (service chết khi đang gọi AI).
    static Set<CertificateAiCheckStatus> DUE_AI_CHECK_STATUSES =
            Set.of(CertificateAiCheckStatus.QUEUED, CertificateAiCheckStatus.PROCESSING);
    // Admin duyệt mà không cần lý do chỉ khi AI đã trả LOW / MEDIUM.
    static Set<CertificateRiskLevel> ACCEPTABLE_RISK_LEVELS = Set.of(CertificateRiskLevel.LOW, CertificateRiskLevel.MEDIUM);
    // Caregiver chỉ xóa chứng chỉ chưa được xác nhận hoặc đã bị từ chối (chốt 2026-11-10): chứng chỉ đang chờ admin hay
    // đã xác minh không biến mất âm thầm khỏi hồ sơ.
    static Set<CertificateVerificationStatus> DELETABLE_STATUSES =
            Set.of(CertificateVerificationStatus.PENDING, CertificateVerificationStatus.REJECTED);
    static Duration FIRST_RETRY_DELAY = Duration.ofSeconds(30);
    static TypeReference<List<CertificateAiWarningResponse>> WARNING_LIST = new TypeReference<>() {
    };

    CertificateRepository certificateRepository;
    ProviderMediaRepository providerMediaRepository;
    Clock clock;
    CaregiverService caregiverService;
    ProviderAuthorization providerAuthorization;
    CertificateMapper certificateMapper;
    MediaClient mediaClient;
    AiClient aiClient;
    TransactionTemplate transactionTemplate;
    ObjectMapper objectMapper;
    ProviderProperties providerProperties;

    // Caregiver thêm chứng chỉ của mình.
    @Override
    @Transactional
    public APIResponse<CertificateResponse> create(UUID actorId, UUID caregiverId, CertificateRequest request) {
        CaregiverProfile caregiver = caregiverService.findActiveCaregiver(caregiverId);
        providerAuthorization.requireCaregiverSelf(caregiver, actorId);
        validateDates(request);
        Certificate certificate = certificateMapper.toCertificate(request, caregiver);
        normalizeFields(certificate);
        // Chứng chỉ mới luôn chờ trung tâm xác nhận (bước 3), rồi AI đọc ảnh (bước 4) và admin duyệt (bước 5).
        certificate.setVerificationStatus(CertificateVerificationStatus.PENDING);
        Certificate savedCertificate = certificateRepository.save(certificate);
        CertificateResponse certificateResponse = certificateMapper.toResponse(savedCertificate);
        return APIResponse.success("Tạo chứng chỉ thành công", certificateResponse);
    }

    // Danh sách chứng chỉ của caregiver (chính caregiver hoặc người quản lý).
    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CertificateResponse>> list(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = caregiverService.findActiveCaregiver(caregiverId);
        providerAuthorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        List<Certificate> certificates =
                certificateRepository.findByCaregiverIdAndDeletedAtIsNullOrderByIssuedDateDesc(caregiverId);
        // Kết quả AI chỉ dành cho ADMIN (chốt 2026-11-09); caregiver / manager chỉ thấy trạng thái kiểm tra.
        List<CertificateResponse> certificateResponses = providerAuthorization.isAdmin()
                ? toAdminResponseList(certificates)
                : certificateMapper.toResponseList(certificates);
        return APIResponse.success("Lấy danh sách chứng chỉ thành công", certificateResponses);
    }

    // Chứng chỉ đã duyệt của caregiver đã xác minh (công khai).
    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CertificateResponse>> listVerified(UUID caregiverId) {
        CaregiverProfile caregiver = caregiverService.findActiveCaregiver(caregiverId);
        if (caregiver.getVerificationStatus() != VerificationStatus.VERIFIED
                || caregiver.getCaregiverStatus() != CaregiverStatus.ACTIVE) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_FOUND);
        }
        List<Certificate> certificates =
                certificateRepository.findByCaregiverIdAndVerificationStatusAndDeletedAtIsNullOrderByIssuedDateDesc(
                        caregiverId, CertificateVerificationStatus.ADMIN_VERIFIED).stream()
                        .filter(certificate -> certificate.getExpiryDate() == null
                                || !certificate.getExpiryDate().isBefore(LocalDate.now(clock.withZone(ProviderTime.ZONE))))
                        .toList();
        List<CertificateResponse> certificateResponses = certificateMapper.toResponseList(certificates);
        return APIResponse.success("Lấy chứng chỉ đã xác minh thành công", certificateResponses);
    }

    // Caregiver cập nhật chứng chỉ của mình.
    @Override
    @Transactional
    public APIResponse<CertificateResponse> update(UUID actorId, UUID id, CertificateRequest request) {
        Certificate certificate = lockCertificate(id);
        providerAuthorization.requireCaregiverSelf(certificate.getCaregiver(), actorId);
        checkVersion(certificate, request.getVersion());
        validateDates(request);
        certificateMapper.update(request, certificate);
        normalizeFields(certificate);
        // Nội dung chứng chỉ đổi → kết quả xác minh và kiểm tra AI cũ không còn giá trị, quay về chờ trung tâm.
        certificate.restartReview();
        Certificate savedCertificate = certificateRepository.save(certificate);
        CertificateResponse certificateResponse = certificateMapper.toResponse(savedCertificate);
        return APIResponse.success("Cập nhật chứng chỉ thành công", certificateResponse);
    }

    // Admin duyệt / từ chối chứng chỉ có kiểm version.
    @Override
    @Transactional
    public APIResponse<CertificateResponse> verify(UUID actorId, UUID id, CertificateVerificationRequest request) {
        Certificate certificate = lockCertificate(id);
        checkVersion(certificate, request.getVersion());
        CertificateVerificationStatus from = certificate.getVerificationStatus();
        if (from != CertificateVerificationStatus.CENTER_VERIFIED
                && !(from == CertificateVerificationStatus.ADMIN_VERIFIED && Boolean.FALSE.equals(request.getApproved()))) {
            throw new AppException(ErrorCode.CERTIFICATE_INVALID_TRANSITION);
        }
        checkVerificationRequest(certificate, request);
        // Bước 5: duyệt khi AI chưa có kết quả LOW / MEDIUM (HIGH, không đọc được, đang kiểm tra, lỗi) phải ghi lý do.
        if (Boolean.TRUE.equals(request.getApproved()) && !hasAcceptableAiResult(certificate)
                && isBlank(request.getReason())) {
            throw new AppException(ErrorCode.CERTIFICATE_RISK_REASON_REQUIRED);
        }
        certificate.setVerificationStatus(Boolean.TRUE.equals(request.getApproved())
                ? CertificateVerificationStatus.ADMIN_VERIFIED : CertificateVerificationStatus.REJECTED);
        certificate.setVerificationReason(normalize(request.getReason()));
        certificate.setVerifiedBy(actorId);
        certificate.setVerifiedAt(clock.instant());
        CertificateResponse response = toAdminResponse(certificateRepository.saveAndFlush(certificate));
        return APIResponse.success("Xác minh chứng chỉ thành công", response);
    }

    // Manager của trung tâm xác nhận chứng chỉ, sau đó xếp AI kiểm tra.
    @Override
    @Transactional
    public APIResponse<CertificateResponse> verifyCenter(UUID actorId, UUID id, CertificateVerificationRequest request) {
        Certificate certificate = lockCertificate(id);
        if (!actorId.equals(certificate.getCaregiver().getCenter().getManagerUserId())) {
            throw new AppException(ErrorCode.CERTIFICATE_NOT_FOUND);
        }
        checkVersion(certificate, request.getVersion());
        if (certificate.getVerificationStatus() != CertificateVerificationStatus.PENDING) {
            throw new AppException(ErrorCode.CERTIFICATE_INVALID_TRANSITION);
        }
        checkVerificationRequest(certificate, request);
        boolean approved = Boolean.TRUE.equals(request.getApproved());
        // AI đối chiếu ảnh với thông tin khai báo → trung tâm chỉ xác nhận chứng chỉ đã có ảnh / file.
        if (approved && !providerMediaRepository.existsByOwnerTypeAndOwnerId(MediaOwnerType.CERTIFICATE, id)) {
            throw new AppException(ErrorCode.CERTIFICATE_IMAGE_REQUIRED);
        }
        certificate.setVerificationStatus(approved
                ? CertificateVerificationStatus.CENTER_VERIFIED : CertificateVerificationStatus.REJECTED);
        certificate.setCenterVerifiedBy(actorId);
        certificate.setCenterVerifiedAt(clock.instant());
        certificate.setCenterVerificationReason(normalize(request.getReason()));
        // Bước 4 chạy nền (CertificateAiCheckScheduler) — manager không phải chờ AI đọc ảnh.
        if (approved) {
            certificate.queueAiCheck(clock.instant());
        }
        CertificateResponse response = certificateMapper.toResponse(certificateRepository.saveAndFlush(certificate));
        return APIResponse.success("Trung tâm xác minh chứng chỉ thành công", response);
    }

    // Danh sách chứng chỉ chờ admin duyệt, lọc theo rủi ro / trạng thái AI.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CertificateResponse>> listPendingAudit(UUID centerId, CertificateRiskLevel riskLevel,
                                                                           CertificateAiCheckStatus aiCheckStatus,
                                                                           int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        Page<Certificate> certificates = certificateRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            predicates.add(builder.equal(root.get("verificationStatus"), CertificateVerificationStatus.CENTER_VERIFIED));
            if (centerId != null) {
                predicates.add(builder.equal(root.get("caregiver").get("center").get("id"), centerId));
            }
            if (riskLevel != null) {
                predicates.add(builder.equal(root.get("aiRiskLevel"), riskLevel));
            }
            // QUEUED = "AI chưa trả kết quả": gồm cả PROCESSING (khớp thẻ "AI đang kiểm tra" của thống kê).
            if (aiCheckStatus == CertificateAiCheckStatus.QUEUED) {
                predicates.add(root.get("aiCheckStatus").in(CertificateAiCheckStatus.QUEUED,
                        CertificateAiCheckStatus.PROCESSING));
            } else if (aiCheckStatus != null) {
                predicates.add(builder.equal(root.get("aiCheckStatus"), aiCheckStatus));
            }
            // Rủi ro cao lên đầu, rồi chứng chỉ trung tâm xác nhận sớm nhất. Không sắp xếp ở query đếm.
            if (query != null && !Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                Expression<Integer> riskOrder = builder.<Integer>selectCase()
                        .when(builder.equal(root.get("aiRiskLevel"), CertificateRiskLevel.HIGH), 0)
                        .when(builder.equal(root.get("aiRiskLevel"), CertificateRiskLevel.UNDETERMINED), 1)
                        .when(builder.equal(root.get("aiRiskLevel"), CertificateRiskLevel.MEDIUM), 2)
                        .when(builder.equal(root.get("aiRiskLevel"), CertificateRiskLevel.LOW), 3)
                        .otherwise(4);
                query.orderBy(builder.asc(riskOrder), builder.asc(root.get("centerVerifiedAt")));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(page, size));
        PageResponse<CertificateResponse> response = PageResponse.of(certificates,
                toAdminResponseList(certificates.getContent()));
        return APIResponse.success("Lấy chứng chỉ chờ duyệt thành công", response);
    }

    // Đếm chứng chỉ chờ duyệt theo mức rủi ro AI.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<CertificatePendingAuditStatisticsResponse> getPendingAuditStatistics(UUID centerId) {
        long total = 0;
        long high = 0;
        long medium = 0;
        long low = 0;
        long undetermined = 0;
        long checking = 0;
        long failed = 0;
        for (CertificateRiskCount count : certificateRepository.countPendingAuditByRisk(
                CertificateVerificationStatus.CENTER_VERIFIED, centerId)) {
            total += count.getTotal();
            if (count.getCheckStatus() == CertificateAiCheckStatus.QUEUED
                    || count.getCheckStatus() == CertificateAiCheckStatus.PROCESSING) {
                checking += count.getTotal();
            } else if (count.getCheckStatus() == CertificateAiCheckStatus.FAILED) {
                failed += count.getTotal();
            }
            if (count.getRiskLevel() == null) {
                continue;
            }
            switch (count.getRiskLevel()) {
                case HIGH -> high += count.getTotal();
                case MEDIUM -> medium += count.getTotal();
                case LOW -> low += count.getTotal();
                case UNDETERMINED -> undetermined += count.getTotal();
            }
        }
        CertificatePendingAuditStatisticsResponse response = certificateMapper.toStatisticsResponse(total, high, medium,
                low, undetermined, checking, failed);
        return APIResponse.success("Lấy thống kê chứng chỉ chờ duyệt thành công", response);
    }

    // Xếp lại AI kiểm tra cho chứng chỉ đã được trung tâm xác nhận.
    @Override
    @Transactional
    public APIResponse<CertificateResponse> startAiCheck(UUID actorId, UUID id) {
        Certificate certificate = lockCertificate(id);
        if (certificate.getVerificationStatus() != CertificateVerificationStatus.CENTER_VERIFIED) {
            throw new AppException(ErrorCode.CERTIFICATE_AI_CHECK_NOT_ALLOWED);
        }
        certificate.queueAiCheck(clock.instant());
        log.info("Admin {} queued AI check for certificate {}", actorId, id);
        CertificateResponse response = toAdminResponse(certificateRepository.saveAndFlush(certificate));
        return APIResponse.success("Đã xếp lịch kiểm tra chứng chỉ bằng AI", response);
    }

    // Caregiver xóa chứng chỉ của mình có kiểm version.
    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        Certificate certificate = lockCertificate(id);
        providerAuthorization.requireCaregiverSelf(certificate.getCaregiver(), actorId);
        checkVersion(certificate, version);
        if (!DELETABLE_STATUSES.contains(certificate.getVerificationStatus())) {
            throw new AppException(ErrorCode.CERTIFICATE_INVALID_TRANSITION);
        }
        certificate.setDeletedAt(Instant.now());
        certificateRepository.save(certificate);
        return APIResponse.success("Xóa chứng chỉ thành công");
    }

    // ─── Kiểm tra bằng AI (bước 4) ─────────────────────────────────────────────────────────────────────────────

    // Lấy id chứng chỉ tới hạn chạy AI kiểm tra.
    @Override
    public List<UUID> findDueAiCheckIds() {
        return certificateRepository.findDueAiCheckIds(DUE_AI_CHECK_STATUSES, clock.instant(),
                PageRequest.of(0, providerProperties.getAiCheck().getBatchSize()));
    }

    /**
     * Nhận việc trong một transaction ngắn (update có điều kiện), gọi MediaService + AIService NGOÀI transaction (có
     * thể mất vài chục giây), rồi ghi kết quả bằng update có điều kiện ai_check_id: nếu caregiver đã sửa chứng chỉ hoặc
     * admin đã chạy lại trong lúc chờ thì kết quả cũ bị bỏ.
     */
    @Override
    public void processAiCheck(UUID id) {
        AiCheckJob job = transactionTemplate.execute(status -> claimAiCheck(id));
        if (job == null) {
            return;
        }
        try {
            CertificateCheckClientRequest request = certificateMapper.toCheckRequest(job.certificate(),
                    loadCertificateFiles(job.mediaFiles()));
            CertificateCheckClientResponse result = requireAiResult(aiClient.checkCertificate(request));
            String warnings = objectMapper.writeValueAsString(result.getWarnings() == null ? List.of() : result.getWarnings());
            String extracted = result.getExtracted() == null ? null : objectMapper.writeValueAsString(result.getExtracted());
            Instant checkedAt = result.getCheckedAt() == null ? clock.instant() : result.getCheckedAt();
            Integer saved = transactionTemplate.execute(status -> certificateRepository.completeAiCheck(id,
                    job.checkId(), CertificateAiCheckStatus.PROCESSING, CertificateAiCheckStatus.COMPLETED,
                    result.getRiskLevel(), warnings, extracted, result.getModel(), checkedAt));
            if (saved == null || saved == 0) {
                log.info("AI check {} of certificate {} is stale, result discarded", job.checkId(), id);
            }
        } catch (RuntimeException exception) {
            failAiCheck(id, job, exception);
        }
    }

    // Nhận việc AI kiểm tra một chứng chỉ (chống chạy trùng).
    private AiCheckJob claimAiCheck(UUID id) {
        Certificate certificate = certificateRepository.findWithCaregiverById(id).orElse(null);
        if (certificate == null || certificate.getAiCheckId() == null
                || certificate.getVerificationStatus() != CertificateVerificationStatus.CENTER_VERIFIED) {
            return null;
        }
        Instant now = clock.instant();
        int claimed = certificateRepository.claimAiCheck(id, certificate.getAiCheckId(), DUE_AI_CHECK_STATUSES,
                CertificateAiCheckStatus.PROCESSING, now, now.plus(providerProperties.getAiCheck().getLease()));
        if (claimed != 1) {
            return null;
        }
        List<ProviderMedia> mediaFiles = providerMediaRepository
                .findAllByOwnerTypeAndOwnerIdOrderByDisplayOrderAsc(MediaOwnerType.CERTIFICATE, id);
        return new AiCheckJob(certificate, certificate.getAiCheckId(), certificate.getAiCheckAttempts(), mediaFiles);
    }

    // URL tải có hạn + loại file thật (MediaService) của từng file; AIService tự bỏ qua file không phải ảnh.
    private List<CertificateImageClientRequest> loadCertificateFiles(List<ProviderMedia> mediaFiles) {
        List<CertificateImageClientRequest> images = new ArrayList<>();
        for (ProviderMedia media : mediaFiles) {
            MediaClientResponse metadata = requireMediaResult(mediaClient.get(media.getMediaFileId()));
            DownloadUrlClientResponse download = requireMediaResult(mediaClient.createDownloadUrl(media.getMediaFileId()));
            images.add(certificateMapper.toImageRequest(download.getDownloadUrl(), metadata.getContentType()));
        }
        return images;
    }

    // Ghi lỗi AI kiểm tra, hẹn chạy lại hoặc đánh dấu thất bại hẳn.
    private void failAiCheck(UUID id, AiCheckJob job, RuntimeException exception) {
        int attempts = job.attempts() + 1;
        boolean permanent = exception instanceof AppException appException
                && appException.getErrorCode() == ErrorCode.REMOTE_COMMAND_REJECTED;
        boolean failed = permanent || attempts >= providerProperties.getAiCheck().getMaxAttempts();
        Instant nextAttemptAt = failed ? null : clock.instant().plus(retryDelay(attempts));
        String lastError = exception instanceof AppException appException
                ? appException.getErrorCode().name()
                : exception.getClass().getSimpleName();
        transactionTemplate.executeWithoutResult(status -> certificateRepository.failAiCheck(id, job.checkId(),
                CertificateAiCheckStatus.PROCESSING,
                failed ? CertificateAiCheckStatus.FAILED : CertificateAiCheckStatus.QUEUED,
                attempts, nextAttemptAt, truncate(lastError, 100)));
        log.warn("AI check of certificate {} failed (attempt {}, {}): {}", id, attempts,
                failed ? "stopped" : "will retry", lastError);
    }

    // 30s, 60s, 120s… tối đa provider.ai-check.max-backoff.
    private Duration retryDelay(int attempts) {
        Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << Math.min(attempts - 1, 10));
        Duration maxBackoff = providerProperties.getAiCheck().getMaxBackoff();
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    // AI đã chạy xong và mức rủi ro chấp nhận được.
    private boolean hasAcceptableAiResult(Certificate certificate) {
        return certificate.getAiCheckStatus() == CertificateAiCheckStatus.COMPLETED
                && ACCEPTABLE_RISK_LEVELS.contains(certificate.getAiRiskLevel());
    }

    // ─── Phản hồi cho ADMIN (kèm kết quả AI đọc từ cột JSON) ─────────────────────────────────────────────────

    // Map danh sách chứng chỉ sang response cho admin (kèm kết quả AI).
    private List<CertificateResponse> toAdminResponseList(List<Certificate> certificates) {
        List<CertificateResponse> responses = new ArrayList<>();
        for (Certificate certificate : certificates) {
            responses.add(toAdminResponse(certificate));
        }
        return responses;
    }

    // Map chứng chỉ sang response cho admin kèm cảnh báo và dữ liệu AI đọc được.
    private CertificateResponse toAdminResponse(Certificate certificate) {
        return certificateMapper.toAdminResponse(certificate, readWarnings(certificate.getAiWarnings()),
                readExtracted(certificate.getAiExtracted()));
    }

    // Đọc danh sách cảnh báo AI từ JSON đã lưu.
    private List<CertificateAiWarningResponse> readWarnings(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, WARNING_LIST);
        } catch (JacksonException exception) {
            log.warn("Stored AI warnings are not valid JSON ({})", exception.getClass().getSimpleName());
            return null;
        }
    }

    // Đọc thông tin AI trích từ chứng chỉ (JSON đã lưu).
    private CertificateAiExtractedResponse readExtracted(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CertificateAiExtractedResponse.class);
        } catch (JacksonException exception) {
            log.warn("Stored AI reading is not valid JSON ({})", exception.getClass().getSimpleName());
            return null;
        }
    }

    // ─── Kiểm tra chung ──────────────────────────────────────────────────────────────────────────────────────

    // Kiểm tra yêu cầu duyệt: bắt buộc kết quả, từ chối thì phải có lý do.
    private void checkVerificationRequest(Certificate certificate, CertificateVerificationRequest request) {
        if (request.getApproved() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (!request.getApproved() && isBlank(request.getReason())) {
            throw new AppException(ErrorCode.VERIFICATION_REASON_REQUIRED);
        }
        if (request.getApproved() && certificate.getExpiryDate() != null
                && certificate.getExpiryDate().isBefore(LocalDate.now(clock.withZone(ProviderTime.ZONE)))) {
            throw new AppException(ErrorCode.CERTIFICATE_EXPIRED);
        }
    }

    // Lấy kết quả từ MediaService, thiếu thì báo dịch vụ file không khả dụng.
    private <T> T requireMediaResult(APIResponse<T> response) {
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
        }
        return response.getResult();
    }

    // Lấy kết quả từ AIService, thiếu mức rủi ro thì báo AI không khả dụng.
    private CertificateCheckClientResponse requireAiResult(RemoteApiResponse<CertificateCheckClientResponse> response) {
        if (response == null || response.getResult() == null || response.getResult().getRiskLevel() == null) {
            throw new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
        return response.getResult();
    }

    // Khóa chứng chỉ để cập nhật, không có thì báo lỗi.
    private Certificate lockCertificate(UUID id) {
        return certificateRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
    }

    // Chuẩn hóa tên chứng chỉ, đơn vị cấp và số hiệu.
    private void normalizeFields(Certificate certificate) {
        certificate.setCertificateName(certificate.getCertificateName().trim());
        certificate.setIssuedBy(certificate.getIssuedBy().trim());
        certificate.setCredentialNumber(normalize(certificate.getCredentialNumber()));
    }

    // Báo lỗi nếu ngày hết hạn trước ngày cấp.
    private void validateDates(CertificateRequest request) {
        if (request.getIssuedDate() != null && request.getExpiryDate() != null
                && request.getExpiryDate().isBefore(request.getIssuedDate())) {
            throw new AppException(ErrorCode.CERTIFICATE_DATE_INVALID);
        }
    }

    // Báo xung đột nếu version gửi lên không khớp.
    private void checkVersion(Certificate certificate, Long version) {
        if (version == null || !version.equals(certificate.getVersion())) {
            throw new AppException(ErrorCode.CERTIFICATE_VERSION_CONFLICT);
        }
    }

    // Trim chuỗi, rỗng thì trả null.
    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    // Cắt chuỗi về độ dài tối đa.
    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    // Kiểm tra chuỗi rỗng hoặc null.
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // Ảnh chụp dữ liệu cần cho một lần gọi AI, lấy trong transaction nhận việc.
    private record AiCheckJob(Certificate certificate, UUID checkId, int attempts, List<ProviderMedia> mediaFiles) {
    }
}
