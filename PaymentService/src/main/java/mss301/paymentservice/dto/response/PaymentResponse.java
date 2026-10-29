package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Booking đọc id, bookingId, status, amount, refundedAmount (ComplaintServiceImpl.findRefundableAmount).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentResponse {
    UUID id;

    UUID bookingId;

    UUID customerId;

    UUID centerId;

    UUID invoiceId;

    PaymentType paymentType;

    PaymentStatus status;

    BigDecimal amount;

    BigDecimal refundedAmount;

    // M-09: hoàn tiền đang giữ chỗ (REFUND_PENDING + FAILED) — trần hoàn mới = amount − refundedAmount − reservedRefundAmount.
    BigDecimal reservedRefundAmount;

    String currency;

    Instant paidAt;

    Instant createdAt;
}
