package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CertificateAiCheckStatus;
import mss301.providerservice.enums.CertificateRiskLevel;
import mss301.providerservice.enums.CertificateVerificationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateResponse {
    UUID id;
    UUID caregiverId;
    // Tên caregiver / trung tâm để hàng chờ duyệt của admin không phải tra từng caregiver (không hiển thị UUID).
    String caregiverName;
    UUID centerId;
    String centerName;
    String certificateName;
    String issuedBy;
    String credentialNumber;
    LocalDate issuedDate;
    LocalDate expiryDate;
    CertificateVerificationStatus verificationStatus;
    String verificationReason;
    UUID verifiedBy;
    Instant verifiedAt;
    // Bước 3: Center Manager xác nhận.
    Instant centerVerifiedAt;
    String centerVerificationReason;
    // Bước 4: trạng thái kiểm tra AI — mọi role thấy (null = chưa yêu cầu).
    CertificateAiCheckStatus aiCheckStatus;
    // Kết quả AI — CHỈ ADMIN (role khác luôn null).
    CertificateRiskLevel aiRiskLevel;
    List<CertificateAiWarningResponse> aiWarnings;
    CertificateAiExtractedResponse aiExtracted;
    Instant aiCheckedAt;
    Long version;
    Instant createdAt;
    Instant updatedAt;
}
