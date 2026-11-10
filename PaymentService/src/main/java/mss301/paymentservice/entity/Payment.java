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
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Một khoản thu (cọc hoặc phần còn lại) của booking. Giữ SUCCESS sau khi hoàn — tiền đã hoàn nằm ở refundedAmount.
@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uk_payments_booking_type",
        columnNames = {"booking_id", "payment_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payment extends BaseEntity {
    @Id
    @UuidGenerator
    @Column(name = "payment_id")
    UUID id;

    @Column(name = "booking_id", nullable = false)
    UUID bookingId;

    @Column(name = "customer_id", nullable = false)
    UUID customerId;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    PaymentType paymentType;

    @Column(name = "invoice_id")
    UUID invoiceId;

    @Column(nullable = false, precision = 14, scale = 2)
    BigDecimal amount;

    @Builder.Default
    @Column(name = "refunded_amount", nullable = false, precision = 14, scale = 2)
    BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PaymentStatus status;

    @Column(name = "paid_at")
    Instant paidAt;

    @Column(name = "booking_notified_at")
    Instant bookingNotifiedAt;

    @Version
    @Column(nullable = false)
    Long version;
}
