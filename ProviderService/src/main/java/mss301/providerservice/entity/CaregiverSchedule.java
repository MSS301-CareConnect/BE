package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.ScheduleStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "caregiver_schedules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_caregiver_schedules_registration", columnNames = "registration_id"),
        @UniqueConstraint(name = "uk_caregiver_schedules_shift_caregiver", columnNames = {"work_shift_id", "caregiver_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "schedule_id"))
public class CaregiverSchedule extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    ShiftRegistration registration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caregiver_id", nullable = false)
    CaregiverProfile caregiver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_shift_id", nullable = false)
    WorkShift workShift;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_status", nullable = false, length = 50)
    ScheduleStatus scheduleStatus;

    @Column(name = "assigned_by", nullable = false)
    UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    Instant assignedAt;

    @Column(name = "note", columnDefinition = "text")
    String note;

    @Column(name = "cancellation_reason", length = 500)
    String cancellationReason;

    @Column(name = "cancelled_at")
    Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
