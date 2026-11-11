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
import mss301.paymentservice.enums.OccurrenceFinalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Tiền của một buổi: cọc phân bổ, phần thu thêm (REMAINING), đã hoàn (gồm cả hoàn đang chờ) — PAY-04.
@Entity
@Table(name = "occurrence_financials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceFinancial extends BaseEntity {
    @Id
    @Column(name = "occurrence_id")
    UUID occurrenceId;

    @Column(name = "booking_id", nullable = false)
    UUID bookingId;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    @Column(name = "start_at", nullable = false)
    Instant startAt;

    @Column(name = "price_amount", nullable = false, precision = 14, scale = 2)
    BigDecimal priceAmount;

    @Column(name = "deposit_amount", nullable = false, precision = 14, scale = 2)
    BigDecimal depositAmount;

    @Builder.Default
    @Column(name = "charged_amount", nullable = false, precision = 14, scale = 2)
    BigDecimal chargedAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "refunded_amount", nullable = false, precision = 14, scale = 2)
    BigDecimal refundedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_status", length = 20)
    OccurrenceFinalStatus finalStatus;

    // CR-4: caregiver được giao buổi (Provider id + Identity id), chụp từ payment-context lúc mở phiên thanh toán đầu và
    // cập nhật theo lệnh hoàn của Booking — quyết định ai hưởng phần cọc bị giữ.
    @Column(name = "caregiver_id")
    UUID caregiverId;

    @Column(name = "caregiver_user_id")
    UUID caregiverUserId;

    // Ví: lúc buổi hoàn tất được cộng cho trung tâm / nền tảng (WalletSettlementService) — mỗi buổi một lần.
    @Column(name = "settled_at")
    Instant settledAt;

    @Version
    @Column(nullable = false)
    Long version;

    // Hai id caregiver luôn đổi cùng nhau; id rỗng (buổi chưa có người) xóa cả hai.
    public void assignCaregiver(UUID newCaregiverId, UUID newCaregiverUserId) {
        boolean assigned = newCaregiverId != null && newCaregiverUserId != null;
        caregiverId = assigned ? newCaregiverId : null;
        caregiverUserId = assigned ? newCaregiverUserId : null;
    }
}
