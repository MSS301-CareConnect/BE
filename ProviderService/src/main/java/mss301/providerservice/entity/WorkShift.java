package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.ShiftStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "work_shifts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "work_shift_id"))
public class WorkShift extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false)
    CareCenter center;

    @Column(name = "work_date", nullable = false)
    LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    LocalTime endTime;

    @Column(name = "shift_type", nullable = false, length = 50)
    String shiftType;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_status", nullable = false, length = 50)
    ShiftStatus shiftStatus;

    @Column(name = "capacity", nullable = false)
    Integer capacity;

    @Column(name = "registration_deadline")
    Instant registrationDeadline;

    @Column(name = "note", columnDefinition = "text")
    String note;

    @Column(name = "created_by", nullable = false)
    UUID createdBy;

    @Column(name = "updated_by")
    UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
