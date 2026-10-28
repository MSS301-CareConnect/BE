package mss301.providerservice.mapper;

import mss301.providerservice.dto.request.CertificateRequest;
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

    @Mapping(target = "caregiverId", source = "caregiver.id")
    CertificateResponse toResponse(Certificate certificate);

    List<CertificateResponse> toResponseList(List<Certificate> certificates);

    // Hồ sơ công khai caregiver (SRCH-01) — service chỉ truyền chứng chỉ VERIFIED (do ADMIN duyệt).
    PublicCertificateResponse toPublicResponse(Certificate certificate);

    List<PublicCertificateResponse> toPublicResponseList(List<Certificate> certificates);

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
