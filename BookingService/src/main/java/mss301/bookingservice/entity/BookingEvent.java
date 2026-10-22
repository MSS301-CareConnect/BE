package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingEventType;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Nhật ký sự kiện ở mức buổi (nhận ca, đổi trạng thái buổi, đổi caregiver) — chỉ ghi thêm, không kế thừa BaseEntity.
 * Chuyển trạng thái cấp booking vẫn ở booking_status_history. changed_by NULL = hệ thống.
 */
@Entity
@Table(name = "booking_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "event_id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    Booking booking;

    @Column(name = "occurrence_id")
    UUID occurrenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    BookingEventType eventType;

    @Column(name = "old_caregiver_id")
    UUID oldCaregiverId;

    @Column(name = "new_caregiver_id")
    UUID newCaregiverId;

    @Column(name = "from_status", length = 30)
    String fromStatus;

    @Column(name = "to_status", length = 30)
    String toStatus;

    @Column(name = "changed_by")
    UUID changedBy;

    @Column(name = "reason", columnDefinition = "text")
    String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
}
