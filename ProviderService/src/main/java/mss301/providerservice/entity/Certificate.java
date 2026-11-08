package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CertificateVerificationStatus;

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

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    @Column(name = "ai_risk_level", length = 20)
    String aiRiskLevel;

    @Column(name = "ai_warnings")
    String aiWarnings;

    @Column(name = "ai_extracted_text")
    String aiExtractedText;

    @Column(name = "ai_qr_content")
    String aiQrContent;

    @Column(name = "ai_caregiver_name_matched")
    Boolean aiCaregiverNameMatched;

    @Column(name = "ai_certificate_name_matched")
    Boolean aiCertificateNameMatched;

    @Column(name = "ai_issued_by_matched")
    Boolean aiIssuedByMatched;

    @Column(name = "ai_credential_number_matched")
    Boolean aiCredentialNumberMatched;

    @Column(name = "ai_issued_date_matched")
    Boolean aiIssuedDateMatched;

    @Column(name = "ai_expiry_date_matched")
    Boolean aiExpiryDateMatched;

    @Column(name = "ai_expired")
    Boolean aiExpired;

    @Column(name = "ai_checked_at")
    Instant aiCheckedAt;
}
