package mss301.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.CancelledBy;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.RefundStatus;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Một lệnh hoàn tiền gắn với một payment; dispatcher đưa REFUND_PENDING → REFUNDED | FAILED (retry có giới hạn).
@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Refund extends BaseEntity {
    @Id
    @UuidGenerator
    @Column(name = "refund_id")
    UUID id;

    @Column(name = "booking_id", nullable = false)
    UUID bookingId;

    @Column(name = "occurrence_id")
    UUID occurrenceId;

    @Column(name = "payment_id", nullable = false)
    UUID paymentId;

    // Khác null: hoàn tiền của một phiên trả trễ / trả trùng — tiền nằm ngoài payment.amount (Đợt 1b).
    @Column(name = "attempt_id")
    UUID attemptId;

    @Column(name = "customer_id", nullable = false)
    UUID customerId;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    @Column(name = "complaint_id")
    UUID complaintId;

    @Column(nullable = false, precision = 14, scale = 2)
    BigDecimal amount;

    @Column(name = "fee_amount", nullable = false, precision = 14, scale = 2)
    BigDecimal feeAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    RefundReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 20)
    CancelledBy cancelledBy;

    @Column(name = "requested_by")
    UUID requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    RefundStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 150)
    String idempotencyKey;

    @Column(name = "provider_request_id", length = 100)
    String providerRequestId;

    @Column(name = "provider_response_code", length = 20)
    String providerResponseCode;

    @Column(nullable = false)
    int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    String lastError;

    @Column(name = "refunded_at")
    Instant refundedAt;

    @Version
    @Column(nullable = false)
    Long version;
}
