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

    // 2026-11-10: null ở snapshot cũ → RefundCalculator dùng 120 phút (luật đã áp từ 2026-11-05).
    Integer lateCancelNoRefundMinutes;

    BigDecimal overduePaymentForfeitPercent;
}
