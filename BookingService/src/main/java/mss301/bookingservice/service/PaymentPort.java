package mss301.bookingservice.service;

import mss301.bookingservice.client.dto.PaymentPolicyResponse;
import mss301.bookingservice.client.dto.RefundQuoteRequest;
import mss301.bookingservice.client.dto.RefundQuoteResponse;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Chính sách thanh toán hiệu lực của trung tâm (PaymentService, C2.1). Chỉ dùng để hiển thị cọc dự kiến khi báo giá:
 * PaymentService lỗi / chưa triển khai → Optional.empty (báo giá vẫn trả, không có phần cọc), không chặn khách.
 */
public interface PaymentPort {
    // BK-02: khách đang nợ → 409 CUSTOMER_HAS_OUTSTANDING_DEBT; PaymentService lỗi → 503 (fail-closed, không đặt được).
    void checkNoOutstandingDebt();

    Optional<PaymentPolicyResponse> findEffective(UUID centerId, Instant at);

    // BK-03 (C2.2): số tiền hoàn nếu hủy các buổi. PaymentService lỗi → 503 (không đoán số tiền).
    RefundQuoteResponse quoteRefund(RefundQuoteRequest request);
}
