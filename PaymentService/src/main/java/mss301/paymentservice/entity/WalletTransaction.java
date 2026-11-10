package mss301.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.WalletTransactionType;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Sổ ví append-only: mỗi biến động số dư một dòng. reference_key duy nhất trong ví — một sự kiện không bao giờ cộng hai lần.
@Entity
@Immutable
@Table(name = "wallet_transactions", uniqueConstraints = @UniqueConstraint(name = "uk_wallet_transactions_reference",
        columnNames = {"wallet_id", "reference_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletTransaction {
    @Id
    @UuidGenerator
    @Column(name = "transaction_id")
    UUID id;

    @Column(name = "wallet_id", nullable = false)
    UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    WalletTransactionType type;

    @Column(nullable = false, precision = 14, scale = 2)
    BigDecimal amount;

    @Column(name = "available_before", nullable = false, precision = 14, scale = 2)
    BigDecimal availableBefore;

    @Column(name = "available_after", nullable = false, precision = 14, scale = 2)
    BigDecimal availableAfter;

    @Column(name = "held_before", nullable = false, precision = 14, scale = 2)
    BigDecimal heldBefore;

    @Column(name = "held_after", nullable = false, precision = 14, scale = 2)
    BigDecimal heldAfter;

    // Trung tâm của booking (tiền buổi, hoa hồng, cọc bị giữ, trừ khiếu nại) — thống kê theo trung tâm.
    @Column(name = "center_id")
    UUID centerId;

    @Column(name = "booking_id")
    UUID bookingId;

    @Column(name = "occurrence_id")
    UUID occurrenceId;

    @Column(name = "refund_id")
    UUID refundId;

    @Column(name = "withdrawal_id")
    UUID withdrawalId;

    @Column(name = "reference_key", nullable = false, length = 150)
    String referenceKey;

    @Column(length = 500)
    String note;

    // Lấy từ Clock của service (không auditing) — cùng mốc thời gian với paid_at / refunded_at của payment.
    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;
}
