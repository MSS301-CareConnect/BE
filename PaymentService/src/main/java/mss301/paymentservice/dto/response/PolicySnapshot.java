package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Bản chụp chính sách lúc tạo phiên cọc (PAY-03), lưu nguyên vào booking_financials.policy_snapshot (jsonb).
 * Mọi phép tính hoàn tiền / hóa đơn về sau dùng bản chụp này — đổi chính sách không ảnh hưởng booking đã cọc.
 * depositPercent = % khoản đầu thật đã thu; mốc / % hoàn và % giữ cọc khi quá hạn chụp từ center booking policy của
 * BookingService (spec v1 §8, §10.2). coolingOff* / refundTiers chỉ còn để đọc snapshot cũ.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PolicySnapshot {
    UUID policyId;

    BigDecimal depositPercent;

    BigDecimal commissionPercent;

    int paymentSessionTtlMinutes;

    int depositDueHours;

    int coolingOffMinutes;

    int coolingOffMinLeadHours;

    List<RefundTier> refundTiers;

    BigDecimal customerNoShowChargePercent;

    int remainingDueHours;

    Integer refundThresholdMinutes;

    BigDecimal refundPercentBeforeThreshold;

    BigDecimal refundPercentAfterThreshold;

    BigDecimal overduePaymentForfeitPercent;
}
