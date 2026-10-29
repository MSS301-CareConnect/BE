package mss301.paymentservice.service;

import jakarta.servlet.http.HttpServletRequest;
import mss301.paymentservice.dto.response.CheckoutResponse;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.enums.PaymentProvider;

import java.time.Instant;
import java.util.UUID;

// Phần chung của mọi phiên thanh toán (cọc, phần còn lại): mở phiên trong transaction, gọi cổng ngoài transaction.
public interface PaymentCheckoutService {
    void checkProviderEnabled(PaymentProvider provider);

    // Trong transaction của caller: phiên PENDING cũ → EXPIRED (một phiên sống), tạo phiên mới, ghi CHECKOUT_CREATED.
    PaymentAttempt openAttempt(Payment payment, PaymentProvider provider, Instant expiresAt);

    // Ngoài transaction: gọi cổng lấy URL; lỗi cổng → phiên FAILED rồi ném PAYMENT_GATEWAY_ERROR.
    CheckoutResponse launch(UUID attemptId, String orderLabel, String clientIp);

    String resolveClientIp(HttpServletRequest httpServletRequest);
}
