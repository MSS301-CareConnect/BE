package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit chuyển trạng thái booking (chỉ ghi). changed_by NULL = hệ thống (scheduler) hoặc service nội bộ (Payment).
 */
@Entity
@Table(name = "booking_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingStatusHistory {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "history_id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    Booking booking;

    // NULL ở dòng tạo booking
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    BookingStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    BookingStatus toStatus;

    @Column(name = "changed_by")
    UUID changedBy;

    @Column(name = "reason", columnDefinition = "text")
    String reason;

    @Column(name = "changed_at", nullable = false)
    Instant changedAt;
}
