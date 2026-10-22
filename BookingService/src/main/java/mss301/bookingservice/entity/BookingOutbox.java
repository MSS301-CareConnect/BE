package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.OutboxEventType;
import mss301.bookingservice.enums.OutboxStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox: việc phải gửi sang service khác (hóa đơn REMAINING, hoàn tiền…) ghi CÙNG transaction với thay
 * đổi nghiệp vụ, gửi sau commit và retry khi bên nhận lỗi — không mất việc, không rollback nghiệp vụ vì bên kia sập.
 */
@Entity
@Table(name = "booking_outbox")
@AttributeOverride(name = "id", column = @Column(name = "outbox_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingOutbox extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    OutboxEventType eventType;

    // Booking liên quan; cũng là phần đầu idempotency key gửi sang bên nhận.
    @Column(name = "aggregate_id", nullable = false)
    UUID aggregateId;

    // Khóa khử trùng gửi kèm sang bên nhận; UNIQUE — cùng một việc không bao giờ được xếp hai lần.
    @Column(name = "idempotency_key", nullable = false, length = 200)
    String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    Integer attempts;

    @Column(name = "next_attempt_at", nullable = false)
    Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    String lastError;

    @Column(name = "sent_at")
    Instant sentAt;
}
