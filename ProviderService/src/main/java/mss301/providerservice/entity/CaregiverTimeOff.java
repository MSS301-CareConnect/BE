package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

/**
 * Ngày nghỉ của caregiver (PRV-02) — entity con của aggregate CaregiverProfile: chỉ có repository, nghiệp vụ nằm ở
 * CaregiverServiceImpl. Khoảng nghỉ là [startAt, endAt) nửa mở; xóa = xóa mềm (deleted_at).
 */
@Entity
@Table(name = "caregiver_time_offs", indexes = @Index(
        name = "idx_caregiver_time_offs_caregiver_start", columnList = "caregiver_id, start_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "time_off_id"))
public class CaregiverTimeOff extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caregiver_id", nullable = false)
    CaregiverProfile caregiver;

    @Column(name = "start_at", nullable = false)
    Instant startAt;

    @Column(name = "end_at", nullable = false)
    Instant endAt;

    @Column(name = "reason", length = 500)
    String reason;

    @Column(name = "created_by", nullable = false)
    UUID createdBy;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
