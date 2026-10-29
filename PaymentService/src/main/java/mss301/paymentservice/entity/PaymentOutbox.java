package mss301.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.OutboxEventType;
import mss301.paymentservice.enums.OutboxStatus;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

// Lệnh gửi sang BookingService, ghi cùng transaction với biến động tiền rồi gửi sau commit (Đợt 1b, rule #24).
@Entity
@Table(name = "payment_outbox", uniqueConstraints = @UniqueConstraint(name = "uk_payment_outbox_event_payment",
        columnNames = {"event_type", "payment_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentOutbox extends BaseEntity {
    @Id
    @UuidGenerator
    @Column(name = "outbox_id")
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    OutboxEventType eventType;

    @Column(name = "booking_id", nullable = false)
    UUID bookingId;

    @Column(name = "payment_id", nullable = false)
    UUID paymentId;

    @Column(name = "payment_reference", nullable = false, length = 100)
    String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    OutboxStatus status;

    @Column(nullable = false)
    int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    String lastError;

    @Column(name = "sent_at")
    Instant sentAt;

    @Version
    @Column(nullable = false)
    Long version;
}
