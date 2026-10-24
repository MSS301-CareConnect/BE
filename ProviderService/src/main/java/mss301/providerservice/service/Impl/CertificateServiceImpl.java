package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.CertificateVerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CertificateMapper;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.CertificateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import mss301.providerservice.util.ProviderTime;
import mss301.providerservice.enums.CertificateVerificationStatus;
import mss301.providerservice.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
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

    @Override
    @Transactional
    public APIResponse<CertificateResponse> create(UUID actorId, UUID caregiverId, CertificateRequest request) {
        CaregiverProfile caregiver = caregiverService.findActiveCaregiver(caregiverId);
        providerAuthorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
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
        providerAuthorization.requireCaregiverSelfOrCenterManagement(certificate.getCaregiver(), actorId);
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
        providerAuthorization.requireCaregiverSelfOrCenterManagement(certificate.getCaregiver(), actorId);
        checkVersion(certificate, version);
        certificate.setDeletedAt(Instant.now());
        certificateRepository.save(certificate);
        return APIResponse.success("Xóa chứng chỉ thành công");
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

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
