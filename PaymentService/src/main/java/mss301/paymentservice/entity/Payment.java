package mss301.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payment extends BaseEntity {
    @Id
    @Column(name = "payment_id")
    UUID id;

    @Column(name = "booking_id", nullable = false, unique = true)
    UUID bookingId;

    @Column(name = "customer_id", nullable = false)
    UUID customerId;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    @Column(nullable = false, precision = 14, scale = 2)
    BigDecimal amount;

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
    long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    List<PaymentAttempt> attempts = new ArrayList<>();
}
