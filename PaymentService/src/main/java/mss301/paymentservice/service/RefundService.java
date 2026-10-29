package mss301.paymentservice.service;

import mss301.paymentservice.dto.request.ComplaintRefundRequest;
import mss301.paymentservice.dto.request.RefundCreateRequest;
import mss301.paymentservice.dto.request.RefundQuoteRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.RefundCreateResponse;
import mss301.paymentservice.dto.response.RefundQuoteResponse;
import mss301.paymentservice.dto.response.RefundResponse;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.enums.RoleCode;

import java.util.Set;
import java.util.UUID;

public interface RefundService {
    APIResponse<PageResponse<RefundResponse>> list(UUID actorId, Set<RoleCode> roles, UUID bookingId, RefundStatus status,
                                                   int page, int size);

    // C2.2: preview, không ghi DB.
    APIResponse<RefundQuoteResponse> quote(RefundQuoteRequest request);

    // C2.3: tính lại bằng RefundCalculator (không tin số từ Booking), idempotent theo Idempotency-Key.
    APIResponse<RefundCreateResponse> create(String idempotencyKey, RefundCreateRequest request);

    // C2.4: admin chấp nhận khiếu nại — giảm hóa đơn REMAINING trước, phần còn lại hoàn tiền.
    APIResponse<RefundCreateResponse> createComplaintRefund(String idempotencyKey, ComplaintRefundRequest request);

    APIResponse<RefundResponse> retry(UUID actorId, UUID refundId);

    // Trong transaction của caller: hoàn toàn bộ phần còn hoàn được của payment (Booking không nhận cọc, REMAINING trả
    // sau khi hóa đơn đã miễn). Idempotent theo (lý do, payment).
    void refundWholePayment(UUID paymentId, RefundReason reason);

    // Trong transaction của caller: hoàn nguyên số tiền của MỘT phiên trả trễ / trả trùng (tiền ngoài payment.amount).
    void refundSeparateAttempt(PaymentAttempt attempt, RefundReason reason);
}
