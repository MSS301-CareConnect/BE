package mss301.paymentservice.mapper;

import mss301.paymentservice.dto.response.RefundCreateResponse;
import mss301.paymentservice.dto.response.RefundItemResponse;
import mss301.paymentservice.dto.response.RefundQuoteResponse;
import mss301.paymentservice.dto.response.RefundResponse;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.enums.CancelledBy;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.util.RefundCalculator;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface RefundMapper {

    // booking / khách / trung tâm lấy theo payment được hoàn; payment cũng có amount / status → chỉ rõ nguồn.
    @IgnoreServerManagedFields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paymentId", source = "payment.id")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "attempts", constant = "0")
    @Mapping(target = "providerRequestId", ignore = true)
    @Mapping(target = "providerResponseCode", ignore = true)
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "refundedAt", ignore = true)
    Refund toRefund(Payment payment, UUID attemptId, UUID occurrenceId, UUID complaintId, BigDecimal amount,
                    BigDecimal feeAmount,
                    RefundReason reason, CancelledBy cancelledBy, UUID requestedBy, String idempotencyKey,
                    RefundStatus status, Instant nextAttemptAt);

    RefundResponse toResponse(Refund refund);

    List<RefundResponse> toResponseList(List<Refund> refunds);

    @Mapping(target = "refundId", source = "id")
    RefundItemResponse toItemResponse(Refund refund);

    List<RefundItemResponse> toItemResponseList(List<Refund> refunds);

    RefundCreateResponse toCreateResponse(List<RefundItemResponse> items, BigDecimal totalRefund);

    RefundQuoteResponse.Item toQuoteItem(RefundCalculator.Line line);

    List<RefundQuoteResponse.Item> toQuoteItemList(List<RefundCalculator.Line> lines);

    RefundQuoteResponse toQuoteResponse(List<RefundQuoteResponse.Item> items, BigDecimal totalPaid, BigDecimal totalFee,
                                        BigDecimal totalRefund, UUID policyId);
}
