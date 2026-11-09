package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CertificateAiCheckStatus;
import mss301.providerservice.enums.CertificateRiskLevel;
import mss301.providerservice.enums.CertificateVerificationStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "certificate_id"))
public class Certificate extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caregiver_id", nullable = false)
    CaregiverProfile caregiver;

    @Column(name = "certificate_name", nullable = false)
    String certificateName;

    @Column(name = "issued_by", nullable = false)
    String issuedBy;

    @Column(name = "credential_number")
    String credentialNumber;

    @Column(name = "issued_date")
    LocalDate issuedDate;

    @Column(name = "expiry_date")
    LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 50)
    CertificateVerificationStatus verificationStatus;

    @Column(name = "verification_reason", length = 500)
    String verificationReason;

    @Column(name = "verified_by")
    UUID verifiedBy;

    @Column(name = "verified_at")
    Instant verifiedAt;

    @Column(name = "center_verified_by")
    UUID centerVerifiedBy;

    @Column(name = "center_verified_at")
    Instant centerVerifiedAt;

    @Column(name = "center_verification_reason", length = 500)
    String centerVerificationReason;

    // ─── Kiểm tra bằng AI (bước 4) — chỉ giữ kết quả mới nhất; CertificateAiCheckScheduler ghi bằng update có điều
    // kiện (không tăng version) để không làm 409 thao tác của caregiver / admin đang mở chứng chỉ.

    // Mã lần kiểm tra hiện hành: đổi mỗi lần xếp hàng; kết quả của lần cũ (nội dung đã sửa) bị bỏ qua.
    @Column(name = "ai_check_id")
    UUID aiCheckId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_check_status", length = 20)
    CertificateAiCheckStatus aiCheckStatus;

    @Column(name = "ai_check_attempts", nullable = false)
    int aiCheckAttempts;

    @Column(name = "ai_next_attempt_at")
    Instant aiNextAttemptAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_risk_level", length = 20)
    CertificateRiskLevel aiRiskLevel;

    // JSON danh sách cảnh báo AIService trả về (code, field, declaredValue, extractedValue).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_warnings")
    String aiWarnings;

    // JSON nội dung AI đọc được trên ảnh (holderName, credentialNumber, certificateName, issuedBy, các ngày).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_extracted")
    String aiExtracted;

    @Column(name = "ai_model", length = 120)
    String aiModel;

    @Column(name = "ai_checked_at")
    Instant aiCheckedAt;

    @Column(name = "ai_last_error", length = 100)
    String aiLastError;

    // Nội dung (thông tin hoặc ảnh) đổi → kết quả xác minh và kiểm tra AI cũ không còn giá trị: quay về chờ trung tâm.
    // Dùng chung cho CertificateServiceImpl.update và ProviderMediaServiceImpl (thêm / xóa ảnh chứng chỉ).
    public void restartReview() {
        verificationStatus = CertificateVerificationStatus.PENDING;
        centerVerifiedBy = null;
        centerVerifiedAt = null;
        centerVerificationReason = null;
        verificationReason = null;
        verifiedAt = null;
        verifiedBy = null;
        clearAiCheck();
    }

    // Bỏ lần kiểm tra AI hiện hành (kết quả đang chạy của ai_check_id cũ sẽ không được ghi).
    public void clearAiCheck() {
        aiCheckId = null;
        aiCheckStatus = null;
        aiCheckAttempts = 0;
        aiNextAttemptAt = null;
        aiRiskLevel = null;
        aiWarnings = null;
        aiExtracted = null;
        aiModel = null;
        aiCheckedAt = null;
        aiLastError = null;
    }

    // Xếp một lần kiểm tra AI mới (mã mới), xóa kết quả cũ; CertificateAiCheckScheduler nhận từ nextAttemptAt.
    public void queueAiCheck(Instant now) {
        clearAiCheck();
        aiCheckId = UUID.randomUUID();
        aiCheckStatus = CertificateAiCheckStatus.QUEUED;
        aiNextAttemptAt = now;
    }

    // true khi chứng chỉ có gì để quay lại (đã qua bước trung tâm hoặc đã có lần kiểm tra AI).
    public boolean hasReviewProgress() {
        return verificationStatus != CertificateVerificationStatus.PENDING || aiCheckStatus != null;
    }

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
