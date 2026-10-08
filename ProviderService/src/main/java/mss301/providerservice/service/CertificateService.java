package mss301.providerservice.service;

import lombok.RequiredArgsConstructor;
import mss301.providerservice.dto.request.*;
import mss301.providerservice.dto.response.*;
import mss301.providerservice.entity.*;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.*;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.security.ProviderAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CertificateService {
    private final CertificateRepository certificateRepository;
    private final CaregiverService caregiverService;
    private final ProviderAuthorization authorization;
    private final ProviderViewMapper mapper;

    @Transactional
    public APIResponse<CertificateResponse> create(UUID actorId, UUID caregiverId, CertificateRequest request) {
        CaregiverProfile caregiver = caregiverService.find(caregiverId);
        authorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        validateDates(request);
        Certificate certificate = Certificate.builder()
                .caregiver(caregiver).certificateName(request.getCertificateName().trim())
                .issuedBy(request.getIssuedBy().trim()).credentialNumber(normalize(request.getCredentialNumber()))
                .issuedDate(request.getIssuedDate()).expiryDate(request.getExpiryDate())
                .verificationStatus(VerificationStatus.PENDING).build();
        return APIResponse.success("Tạo chứng chỉ thành công", mapper.certificate(certificateRepository.save(certificate)));
    }

    @Transactional(readOnly = true)
    public APIResponse<List<CertificateResponse>> list(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = caregiverService.find(caregiverId);
        authorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        return APIResponse.success("Lấy danh sách chứng chỉ thành công",
                certificateRepository.findByCaregiverIdAndDeletedAtIsNullOrderByIssuedDateDesc(caregiverId)
                        .stream().map(mapper::certificate).toList());
    }

    @Transactional(readOnly = true)
    public APIResponse<List<CertificateResponse>> verifiedList(UUID caregiverId) {
        CaregiverProfile caregiver = caregiverService.find(caregiverId);
        if (caregiver.getVerificationStatus() != VerificationStatus.VERIFIED
                || caregiver.getCaregiverStatus() != mss301.providerservice.enums.CaregiverStatus.ACTIVE) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_FOUND);
        }
        return APIResponse.success("Lấy chứng chỉ đã xác minh thành công",
                certificateRepository.findByCaregiverIdAndVerificationStatusAndDeletedAtIsNullOrderByIssuedDateDesc(
                        caregiverId, VerificationStatus.VERIFIED).stream().map(mapper::certificate).toList());
    }

    @Transactional
    public APIResponse<CertificateResponse> update(UUID actorId, UUID id, CertificateRequest request) {
        Certificate certificate = find(id);
        authorization.requireCaregiverSelfOrCenterManagement(certificate.getCaregiver(), actorId);
        checkVersion(certificate, request.getVersion());
        validateDates(request);
        certificate.setCertificateName(request.getCertificateName().trim());
        certificate.setIssuedBy(request.getIssuedBy().trim());
        certificate.setCredentialNumber(normalize(request.getCredentialNumber()));
        certificate.setIssuedDate(request.getIssuedDate());
        certificate.setExpiryDate(request.getExpiryDate());
        certificate.setVerificationStatus(VerificationStatus.PENDING);
        certificate.setVerificationReason(null);
        certificate.setVerifiedAt(null);
        certificate.setVerifiedBy(null);
        return APIResponse.success("Cập nhật chứng chỉ thành công",
                mapper.certificate(certificateRepository.save(certificate)));
    }

    @Transactional
    public APIResponse<CertificateResponse> verify(UUID actorId, UUID id, VerificationRequest request) {
        Certificate certificate = find(id);
        checkVersion(certificate, request.getVersion());
        if (request.getStatus() == VerificationStatus.PENDING) throw new AppException(ErrorCode.INVALID_REQUEST);
        if (request.getStatus() == VerificationStatus.REJECTED && isBlank(request.getReason())) {
            throw new AppException(ErrorCode.VERIFICATION_REASON_REQUIRED);
        }
        certificate.setVerificationStatus(request.getStatus());
        certificate.setVerificationReason(normalize(request.getReason()));
        certificate.setVerifiedBy(actorId);
        certificate.setVerifiedAt(Instant.now());
        return APIResponse.success("Xác minh chứng chỉ thành công",
                mapper.certificate(certificateRepository.save(certificate)));
    }

    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        Certificate certificate = find(id);
        authorization.requireCaregiverSelfOrCenterManagement(certificate.getCaregiver(), actorId);
        checkVersion(certificate, version);
        certificate.setDeletedAt(Instant.now());
        certificateRepository.save(certificate);
        return APIResponse.success("Xóa chứng chỉ thành công");
    }

    private Certificate find(UUID id) {
        return certificateRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
    }

    private void validateDates(CertificateRequest request) {
        if (request.getIssuedDate() != null && request.getExpiryDate() != null
                && request.getExpiryDate().isBefore(request.getIssuedDate())) {
            throw new AppException(ErrorCode.CERTIFICATE_DATE_INVALID);
        }
    }

    private void checkVersion(Certificate value, Long version) {
        if (version == null || !version.equals(value.getVersion())) {
            throw new AppException(ErrorCode.CERTIFICATE_VERSION_CONFLICT);
        }
    }

    private String normalize(String value) { return isBlank(value) ? null : value.trim(); }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
