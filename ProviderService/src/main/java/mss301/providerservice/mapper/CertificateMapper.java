package mss301.providerservice.mapper;

import mss301.providerservice.client.dto.CertificateCheckClientRequest;
import mss301.providerservice.client.dto.CertificateImageClientRequest;
import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.response.CertificateAiExtractedResponse;
import mss301.providerservice.dto.response.CertificateAiWarningResponse;
import mss301.providerservice.dto.response.CertificatePendingAuditStatisticsResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.dto.response.PublicCertificateResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.Certificate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface CertificateMapper {

    // Caregiver / Center Manager: thấy trạng thái kiểm tra AI nhưng KHÔNG thấy kết quả (chỉ ADMIN — chốt 2026-11-09).
    @Mapping(target = "caregiverId", source = "caregiver.id")
    @Mapping(target = "caregiverName", source = "caregiver.fullName")
    @Mapping(target = "centerId", source = "caregiver.center.id")
    @Mapping(target = "centerName", source = "caregiver.center.centerName")
    @Mapping(target = "aiRiskLevel", ignore = true)
    @Mapping(target = "aiWarnings", ignore = true)
    @Mapping(target = "aiExtracted", ignore = true)
    @Mapping(target = "aiCheckedAt", ignore = true)
    CertificateResponse toResponse(Certificate certificate);

    List<CertificateResponse> toResponseList(List<Certificate> certificates);

    // ADMIN: kèm kết quả AI; cảnh báo / nội dung đọc được do service đọc từ cột JSON rồi truyền vào.
    @Mapping(target = "caregiverId", source = "certificate.caregiver.id")
    @Mapping(target = "caregiverName", source = "certificate.caregiver.fullName")
    @Mapping(target = "centerId", source = "certificate.caregiver.center.id")
    @Mapping(target = "centerName", source = "certificate.caregiver.center.centerName")
    @Mapping(target = "aiWarnings", source = "aiWarnings")
    @Mapping(target = "aiExtracted", source = "aiExtracted")
    // aiExtracted có cùng tên trường (nội dung AI đọc) — giá trị khai báo luôn lấy từ chứng chỉ.
    @Mapping(target = "certificateName", source = "certificate.certificateName")
    @Mapping(target = "issuedBy", source = "certificate.issuedBy")
    @Mapping(target = "credentialNumber", source = "certificate.credentialNumber")
    @Mapping(target = "issuedDate", source = "certificate.issuedDate")
    @Mapping(target = "expiryDate", source = "certificate.expiryDate")
    CertificateResponse toAdminResponse(Certificate certificate, List<CertificateAiWarningResponse> aiWarnings,
                                        CertificateAiExtractedResponse aiExtracted);

    // Hồ sơ công khai caregiver (SRCH-01) — service chỉ truyền chứng chỉ VERIFIED (do ADMIN duyệt).
    PublicCertificateResponse toPublicResponse(Certificate certificate);

    List<PublicCertificateResponse> toPublicResponseList(List<Certificate> certificates);

    CertificatePendingAuditStatisticsResponse toStatisticsResponse(long total, long high, long medium, long low,
                                                                   long undetermined, long checking, long failed);

    // Yêu cầu gửi AIService: họ tên lấy từ hồ sơ caregiver, file do service lấy URL tải từ MediaService.
    @Mapping(target = "holderName", source = "certificate.caregiver.fullName")
    CertificateCheckClientRequest toCheckRequest(Certificate certificate, List<CertificateImageClientRequest> images);

    CertificateImageClientRequest toImageRequest(String url, String mimeType);

    // caregiver do service tra cứu + kiểm phạm vi rồi truyền vào; trạng thái xác minh do service gán.
    @IgnoreServerManagedFields
    @IgnoreServerManagedCertificateFields
    Certificate toCertificate(CertificateRequest request, CaregiverProfile caregiver);

    // PUT toàn phần: null ghi đè. Chứng chỉ không đổi chủ sở hữu.
    @IgnoreServerManagedFields
    @IgnoreServerManagedCertificateFields
    @Mapping(target = "caregiver", ignore = true)
    void update(CertificateRequest request, @MappingTarget Certificate certificate);
}
