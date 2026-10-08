package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.VerificationStatus;

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
    VerificationStatus verificationStatus;

    @Column(name = "verification_reason", length = 500)
    String verificationReason;

    @Column(name = "verified_by")
    UUID verifiedBy;

    @Column(name = "verified_at")
    Instant verifiedAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
