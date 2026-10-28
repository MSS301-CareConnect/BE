package mss301.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentAttempt extends BaseEntity {
    @Id
    @Column(name = "attempt_id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PaymentStatus status;

    @Column(nullable = false, precision = 14, scale = 2)
    BigDecimal amount;

    @Column(nullable = false, length = 3)
    String currency;

    @Column(nullable = false, unique = true, length = 100)
    String reference;

    @Column(name = "provider_checkout_id", unique = true, length = 255)
    String providerCheckoutId;

    @Column(name = "provider_transaction_id", length = 255)
    String providerTransactionId;

    @Column(name = "checkout_url", length = 2000)
    String checkoutUrl;

    @Column(name = "response_code", length = 50)
    String responseCode;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "completed_at")
    Instant completedAt;

    @Version
    @Column(nullable = false)
    long version;
}
