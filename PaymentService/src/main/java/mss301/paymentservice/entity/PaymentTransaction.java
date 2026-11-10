package mss301.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.TransactionEventType;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Nhật ký append-only (@Immutable: Hibernate không bao giờ UPDATE). detail không chứa secret / vnp_SecureHash.
@Entity
@Immutable
@Table(name = "payment_transactions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentTransaction {
    @Id
    @UuidGenerator
    @Column(name = "transaction_id")
    UUID id;

    @Column(name = "booking_id", nullable = false)
    UUID bookingId;

    @Column(name = "payment_id")
    UUID paymentId;

    @Column(name = "refund_id")
    UUID refundId;

    @Column(name = "invoice_id")
    UUID invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    TransactionEventType eventType;

    @Column(precision = 14, scale = 2)
    BigDecimal amount;

    @Column(name = "idempotency_key", length = 150)
    String idempotencyKey;

    @Column(length = 1000)
    String detail;

    @Column(name = "signature_valid")
    Boolean signatureValid;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
}
