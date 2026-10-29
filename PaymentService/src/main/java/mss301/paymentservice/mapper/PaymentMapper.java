package mss301.paymentservice.mapper;

import mss301.paymentservice.dto.response.CheckoutResponse;
import mss301.paymentservice.dto.response.PaymentResponse;
import mss301.paymentservice.dto.response.PaymentReturnResponse;
import mss301.paymentservice.dto.response.RevenueResponse;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface PaymentMapper {

    @Mapping(target = "reservedRefundAmount", ignore = true)
    PaymentResponse toResponse(Payment payment);

    List<PaymentResponse> toResponseList(List<Payment> payments);

    @IgnoreServerManagedFields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "refundedAmount", constant = "0")
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "bookingNotifiedAt", ignore = true)
    Payment toPayment(UUID bookingId, UUID customerId, UUID centerId, PaymentType paymentType, UUID invoiceId,
                      BigDecimal amount, String currency, PaymentStatus status);

    // Phiên mới mang số tiền / tiền tệ hiện tại của payment; trạng thái phiên do service quyết.
    @IgnoreServerManagedFields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "payment", source = "payment")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "providerCheckoutId", ignore = true)
    @Mapping(target = "providerTransactionId", ignore = true)
    @Mapping(target = "checkoutUrl", ignore = true)
    @Mapping(target = "responseCode", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    PaymentAttempt toAttempt(Payment payment, PaymentProvider provider, String reference, PaymentStatus status,
                             Instant expiresAt);

    @Mapping(target = "paymentId", source = "payment.id")
    @Mapping(target = "bookingId", source = "payment.bookingId")
    @Mapping(target = "invoiceId", source = "payment.invoiceId")
    @Mapping(target = "paymentType", source = "payment.paymentType")
    @Mapping(target = "status", source = "attempt.status")
    @Mapping(target = "amount", source = "attempt.amount")
    @Mapping(target = "currency", source = "attempt.currency")
    CheckoutResponse toCheckoutResponse(Payment payment, PaymentAttempt attempt);

    @Mapping(target = "paymentId", source = "payment.id")
    PaymentReturnResponse toReturnResponse(Payment payment, String message);

    RevenueResponse toRevenueResponse(long successfulPaymentCount, BigDecimal grossRevenue, BigDecimal totalRefunded,
                                      BigDecimal totalRevenue, BigDecimal averagePayment);
}
