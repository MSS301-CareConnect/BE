package mss301.paymentservice.mapper;

import mss301.paymentservice.client.dto.BookingPaymentContextResponse;
import mss301.paymentservice.dto.response.PaymentPolicyResponse;
import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.entity.PlatformPolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(config = MapStructConfig.class)
public interface PaymentPolicyMapper {

    @Mapping(target = "policyId", source = "id")
    PaymentPolicyResponse toResponse(PlatformPolicy policy);

    // % khoản đầu đã chốt ở checkout; phần hoàn / giữ cọc lấy từ center booking policy của Booking (Q4a).
    @Mapping(target = "policyId", source = "policy.id")
    @Mapping(target = "depositPercent", source = "depositPercent")
    @Mapping(target = "commissionPercent", source = "policy.commissionPercent")
    @Mapping(target = "paymentSessionTtlMinutes", source = "policy.paymentSessionTtlMinutes")
    @Mapping(target = "depositDueHours", source = "policy.depositDueHours")
    @Mapping(target = "coolingOffMinutes", source = "policy.coolingOffMinutes")
    @Mapping(target = "coolingOffMinLeadHours", source = "policy.coolingOffMinLeadHours")
    @Mapping(target = "refundTiers", source = "policy.refundTiers")
    @Mapping(target = "customerNoShowChargePercent", source = "policy.customerNoShowChargePercent")
    @Mapping(target = "remainingDueHours", source = "policy.remainingDueHours")
    @Mapping(target = "refundThresholdMinutes", source = "context.refundThresholdMinutes")
    @Mapping(target = "refundPercentBeforeThreshold", source = "context.refundPercentBeforeThreshold")
    @Mapping(target = "refundPercentAfterThreshold", source = "context.refundPercentAfterThreshold")
    @Mapping(target = "lateCancelNoRefundMinutes", source = "context.lateCancelNoRefundMinutes")
    @Mapping(target = "overduePaymentForfeitPercent", source = "context.overduePaymentForfeitPercent")
    PolicySnapshot toSnapshot(PlatformPolicy policy, BookingPaymentContextResponse context, BigDecimal depositPercent);
}
