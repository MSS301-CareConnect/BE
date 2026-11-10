package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CareTaskStatus;
import mss301.bookingservice.enums.CareTaskType;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Một việc trong checklist của ca: sinh tự động lúc check-in từ dịch vụ của booking và thuốc trong snapshot người bệnh
 * (OPS-02); caregiver đánh dấu DONE / SKIPPED (OPS-03). Check-out yêu cầu không còn việc PENDING (OPS-04).
 */
@Entity
@Table(name = "care_tasks")
@AttributeOverride(name = "id", column = @Column(name = "task_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareTask extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    CareSession session;

    @Column(name = "booking_detail_id")
    UUID bookingDetailId;

    @Column(name = "care_service_id")
    UUID careServiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false, length = 20)
    CareTaskType taskType;

    @Column(name = "title", nullable = false, length = 500)
    String title;

    // Giờ địa phương cần làm (thuốc theo giờ); NULL = trong ca.
    @Column(name = "scheduled_time")
    LocalTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    CareTaskStatus status;

    @Column(name = "skip_reason", columnDefinition = "text")
    String skipReason;

    @Column(name = "note", columnDefinition = "text")
    String note;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "sort_order", nullable = false)
    Integer sortOrder;

    @Column(name = "updated_by")
    UUID updatedBy;
}
