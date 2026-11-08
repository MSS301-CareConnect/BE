package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.AIClient;
import mss301.providerservice.client.dto.AiCertificateVerificationRequest;
import mss301.providerservice.client.dto.AiCertificateVerificationResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.CertificateVerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.entity.ProviderMedia;
import mss301.providerservice.enums.*;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CertificateMapper;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.repository.ProviderMediaRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.CertificateService;
import mss301.providerservice.service.ProviderMediaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import mss301.providerservice.util.ProviderTime;
import mss301.providerservice.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CertificateServiceImpl implements CertificateService {

    CertificateRepository certificateRepository;
    Clock clock;
    CaregiverService caregiverService;
    ProviderAuthorization providerAuthorization;
    CertificateMapper certificateMapper;

    AIClient aiClient;
    ProviderMediaRepository providerMediaRepository;
    TransactionTemplate transactionTemplate;
    ProviderMediaService providerMediaService;

    @Override
    @Transactional
    public APIResponse<CertificateResponse> create(UUID actorId, UUID caregiverId, CertificateRequest request) {
        CaregiverProfile caregiver = caregiverService.findActiveCaregiver(caregiverId);
        providerAuthorization.requireCaregiverSelf(caregiver, actorId);
        validateDates(request);
        Certificate certificate = certificateMapper.toCertificate(request, caregiver);
        normalizeFields(certificate);
        // Chứng chỉ mới luôn chờ admin xác minh.
        certificate.setVerificationStatus(CertificateVerificationStatus.PENDING);
        Certificate savedCertificate = certificateRepository.save(certificate);
        CertificateResponse certificateResponse = certificateMapper.toResponse(savedCertificate);
        return APIResponse.success("Tạo chứng chỉ thành công", certificateResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CertificateResponse>> list(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = caregiverService.findActiveCaregiver(caregiverId);
        providerAuthorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        List<Certificate> certificates =
                certificateRepository.findByCaregiverIdAndDeletedAtIsNullOrderByIssuedDateDesc(caregiverId);
        List<CertificateResponse> certificateResponses = certificateMapper.toResponseList(certificates);
        return APIResponse.success("Lấy danh sách chứng chỉ thành công", certificateResponses);
    }

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

    @Override
    @Transactional
    public APIResponse<CertificateResponse> update(UUID actorId, UUID id, CertificateRequest request) {
        Certificate certificate = lockCertificate(id);
        providerAuthorization.requireCaregiverSelf(certificate.getCaregiver(), actorId);
        checkVersion(certificate, request.getVersion());
        validateDates(request);
        certificateMapper.update(request, certificate);
        normalizeFields(certificate);
        // Nội dung chứng chỉ đổi → kết quả xác minh cũ không còn giá trị, quay về chờ xác minh.
        certificate.setVerificationStatus(CertificateVerificationStatus.PENDING);
        certificate.setCenterVerifiedBy(null);
        certificate.setCenterVerifiedAt(null);
        certificate.setCenterVerificationReason(null);
        certificate.setVerificationReason(null);
        certificate.setVerifiedAt(null);
        certificate.setVerifiedBy(null);
        clearAiResult(certificate);

        Certificate savedCertificate = certificateRepository.save(certificate);
        CertificateResponse certificateResponse = certificateMapper.toResponse(savedCertificate);
        return APIResponse.success("Cập nhật chứng chỉ thành công", certificateResponse);
    }

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
        certificate.setVerificationStatus(Boolean.TRUE.equals(request.getApproved())
                ? CertificateVerificationStatus.ADMIN_VERIFIED : CertificateVerificationStatus.REJECTED);
        certificate.setVerificationReason(normalize(request.getReason()));
        certificate.setVerifiedBy(actorId);
        certificate.setVerifiedAt(clock.instant());
        CertificateResponse response = certificateMapper.toResponse(certificateRepository.saveAndFlush(certificate));
        return APIResponse.success("Xác minh chứng chỉ thành công", response);
    }

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
        certificate.setVerificationStatus(Boolean.TRUE.equals(request.getApproved())
                ? CertificateVerificationStatus.CENTER_VERIFIED : CertificateVerificationStatus.REJECTED);
        certificate.setCenterVerifiedBy(actorId);
        certificate.setCenterVerifiedAt(clock.instant());
        certificate.setCenterVerificationReason(normalize(request.getReason()));
        CertificateResponse response = certificateMapper.toResponse(certificateRepository.saveAndFlush(certificate));
        return APIResponse.success("Trung tâm xác minh chứng chỉ thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CertificateResponse>> listPendingAudit(UUID centerId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        Page<Certificate> certificates = certificateRepository.findAll((root, query, builder) -> {
            Predicate predicate = builder.and(builder.isNull(root.get("deletedAt")),
                    builder.equal(root.get("verificationStatus"), CertificateVerificationStatus.CENTER_VERIFIED));
            return centerId == null ? predicate : builder.and(predicate,
                    builder.equal(root.get("caregiver").get("center").get("id"), centerId));
        }, PageRequest.of(page, size));
        PageResponse<CertificateResponse> response = PageResponse.of(certificates,
                certificateMapper.toResponseList(certificates.getContent()));
        return APIResponse.success("Lấy chứng chỉ chờ duyệt thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        Certificate certificate = lockCertificate(id);
        providerAuthorization.requireCaregiverSelf(certificate.getCaregiver(), actorId);
        checkVersion(certificate, version);
        certificate.setDeletedAt(Instant.now());
        certificateRepository.save(certificate);
        return APIResponse.success("Xóa chứng chỉ thành công");
    }

    @Override
    public APIResponse<CertificateResponse> checkWithAi(UUID actorId, UUID certificateId) {
        Certificate certificate = findActiveCertificateWithCaregiver(certificateId);
        providerAuthorization.requireCenterManagement(certificate.getCaregiver().getCenter(), actorId);

        if (certificate.getVerificationStatus() != CertificateVerificationStatus.CENTER_VERIFIED) {
            throw new AppException(ErrorCode.CERTIFICATE_INVALID_TRANSITION);
        }

        List<ProviderMedia> images = providerMediaRepository.findAllByOwnerTypeAndOwnerIdOrderByDisplayOrderAsc(
                        MediaOwnerType.CERTIFICATE, certificateId)
                .stream()
                .filter(media -> media.getMediaFileType() == MediaFileType.IMAGE)
                .toList();

        if (images.isEmpty()) {
            throw new AppException(ErrorCode.CERTIFICATE_AI_IMAGES_REQUIRED);
        }

        if (images.size() > 2) {
            throw new AppException(ErrorCode.CERTIFICATE_AI_IMAGE_LIMIT_EXCEEDED);
        }

        List<String> imageUrls = providerMediaService
                .findImageUrls(MediaOwnerType.CERTIFICATE, List.of(certificateId), 2)
                .getOrDefault(certificateId, List.of());

        if (imageUrls.size() != images.size()) {
            throw new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
        }

        Long currentVersion = certificate.getVersion();

        AiCertificateVerificationRequest aiRequest = AiCertificateVerificationRequest.builder()
                        .certificateId(certificate.getId())
                        .caregiverFullName(certificate.getCaregiver().getFullName())
                        .certificateName(certificate.getCertificateName())
                        .issuedBy(certificate.getIssuedBy())
                        .credentialNumber(certificate.getCredentialNumber())
                        .issuedDate(certificate.getIssuedDate())
                        .expiryDate(certificate.getExpiryDate())
                        .imageUrls(imageUrls)
                        .build();

        AiCertificateVerificationResponse aiResult = callAiService(aiRequest);

        CertificateResponse response = transactionTemplate.execute(
                status -> saveAiResult(certificateId, currentVersion, aiResult));

        return APIResponse.success("AI checked certificate successfully", response);
    }

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

    private Certificate lockCertificate(UUID id) {
        return certificateRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
    }

    private Certificate findActiveCertificate(UUID id) {
        return certificateRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
    }

    private void normalizeFields(Certificate certificate) {
        certificate.setCertificateName(certificate.getCertificateName().trim());
        certificate.setIssuedBy(certificate.getIssuedBy().trim());
        certificate.setCredentialNumber(normalize(certificate.getCredentialNumber()));
    }

    private void validateDates(CertificateRequest request) {
        if (request.getIssuedDate() != null && request.getExpiryDate() != null
                && request.getExpiryDate().isBefore(request.getIssuedDate())) {
            throw new AppException(ErrorCode.CERTIFICATE_DATE_INVALID);
        }
    }

    private void checkVersion(Certificate certificate, Long version) {
        if (version == null || !version.equals(certificate.getVersion())) {
            throw new AppException(ErrorCode.CERTIFICATE_VERSION_CONFLICT);
        }
    }

    private Certificate findActiveCertificateWithCaregiver(
            UUID certificateId
    ) {
        return certificateRepository.findWithCaregiverById(certificateId)
                .orElseThrow(() ->
                        new AppException(
                                ErrorCode.CERTIFICATE_NOT_FOUND
                        )
                );
    }

    private AiCertificateVerificationResponse callAiService(AiCertificateVerificationRequest request) {
        try {
            RemoteApiResponse<AiCertificateVerificationResponse> response = aiClient.verifyCertificate(request);

            if (response == null || response.getResult() == null) {
                throw new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE);
            }
            return response.getResult();

        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("AI certificate verification failed", exception);
            throw new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }

    private CertificateResponse saveAiResult(UUID certificateId, Long expectedVersion, AiCertificateVerificationResponse result) {
        Certificate certificate = lockCertificate(certificateId);

        if (!Objects.equals(expectedVersion, certificate.getVersion())) {
            throw new AppException(ErrorCode.CERTIFICATE_VERSION_CONFLICT);
        }

        if (certificate.getVerificationStatus() != CertificateVerificationStatus.CENTER_VERIFIED) {
            throw new AppException(ErrorCode.CERTIFICATE_INVALID_TRANSITION);
        }

        certificate.setAiRiskLevel(result.getRiskLevel());
        certificate.setAiWarnings(joinWarnings(result.getWarnings()));
        certificate.setAiExtractedText(result.getExtractedText());
        certificate.setAiQrContent(result.getQrContent());
        certificate.setAiCaregiverNameMatched(result.isCaregiverNameMatched());
        certificate.setAiCertificateNameMatched(result.isCertificateNameMatched());
        certificate.setAiIssuedByMatched(result.isIssuedByMatched());
        certificate.setAiCredentialNumberMatched(result.isCredentialNumberMatched());
        certificate.setAiIssuedDateMatched(result.isIssuedDateMatched());
        certificate.setAiExpiryDateMatched(result.isExpiryDateMatched());
        certificate.setAiExpired(result.isExpired());
        certificate.setAiCheckedAt(result.getCheckedAt());

        Certificate saved = certificateRepository.saveAndFlush(certificate);
        return certificateMapper.toResponse(saved);
    }

    private String joinWarnings(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return null;
        }

        return String.join("\n", warnings);
    }

    private void clearAiResult(Certificate certificate) {
        certificate.setAiRiskLevel(null);
        certificate.setAiWarnings(null);
        certificate.setAiExtractedText(null);
        certificate.setAiQrContent(null);
        certificate.setAiCaregiverNameMatched(null);
        certificate.setAiCertificateNameMatched(null);
        certificate.setAiIssuedByMatched(null);
        certificate.setAiCredentialNumberMatched(null);
        certificate.setAiIssuedDateMatched(null);
        certificate.setAiExpiryDateMatched(null);
        certificate.setAiExpired(null);
        certificate.setAiCheckedAt(null);
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
