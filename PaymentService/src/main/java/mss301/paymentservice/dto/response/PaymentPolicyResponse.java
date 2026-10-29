package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.PolicyScope;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Booking chỉ đọc policyId + depositPercent (hiển thị cọc dự kiến); FE / admin đọc thêm phần còn lại.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentPolicyResponse {
    UUID policyId;

    PolicyScope scope;

    UUID centerId;

    Instant effectiveFrom;

    BigDecimal depositPercent;

    BigDecimal commissionPercent;

    int paymentSessionTtlMinutes;

    int depositDueHours;

    int coolingOffMinutes;

    int coolingOffMinLeadHours;

    List<RefundTier> refundTiers;

    BigDecimal customerNoShowChargePercent;

    int remainingDueHours;
}
