package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.RegistrationStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
// Unique (ca, caregiver) chỉ áp cho đăng ký PENDING/APPROVED — index một phần trong schema/provider.sql
// (JPA không khai báo được), nên không đặt @UniqueConstraint ở đây.
@Table(name = "shift_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "registration_id"))
public class ShiftRegistration extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_shift_id", nullable = false)
    WorkShift workShift;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caregiver_id", nullable = false)
    CaregiverProfile caregiver;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_status", nullable = false, length = 50)
    RegistrationStatus registrationStatus;

    @Column(name = "caregiver_note", columnDefinition = "text")
    String caregiverNote;

    @Column(name = "reviewed_by")
    UUID reviewedBy;

    @Column(name = "reviewed_at")
    Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
