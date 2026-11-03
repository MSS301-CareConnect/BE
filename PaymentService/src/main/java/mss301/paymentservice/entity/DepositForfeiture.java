package mss301.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
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
import mss301.paymentservice.enums.ForfeitureBeneficiary;
import mss301.paymentservice.enums.RefundReason;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CR-4: sổ ghi phần tiền khách bị giữ khi hủy sát giờ / quá hạn trả phần còn lại, theo từng buổi — append-only như
 * payment_transactions. Buổi đã có caregiver thì caregiver hưởng toàn bộ, chưa có thì trung tâm.
 */
@Entity
@Immutable
@Table(name = "deposit_forfeitures")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DepositForfeiture {
    @Id
    @UuidGenerator
    @Column(name = "forfeiture_id")
    UUID id;

    @Column(name = "booking_id", nullable = false)
    UUID bookingId;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    @Column(name = "occurrence_id", nullable = false)
    UUID occurrenceId;

    @Column(name = "occurrence_start_at", nullable = false)
    Instant occurrenceStartAt;

    // Khoản thanh toán đầu của booking — doanh thu tính theo paid_at của khoản này (cùng mốc với đã thu / đã hoàn).
    @Column(name = "payment_id", nullable = false)
    UUID paymentId;

    @Column(nullable = false, precision = 14, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ForfeitureBeneficiary beneficiary;

    @Column(name = "caregiver_id")
    UUID caregiverId;

    @Column(name = "caregiver_user_id")
    UUID caregiverUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    RefundReason reason;

    @Column(length = 40)
    String rule;

    @Column(name = "idempotency_key", nullable = false, length = 150)
    String idempotencyKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
}
