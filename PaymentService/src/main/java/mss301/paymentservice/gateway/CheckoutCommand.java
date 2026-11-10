package mss301.paymentservice.gateway;

import mss301.paymentservice.enums.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;

// createdAt = thời điểm mở phiên — gửi làm vnp_CreateDate để querydr dùng lại đúng giá trị (vnp_TransactionDate).
public record CheckoutCommand(PaymentProvider provider, String reference, String orderLabel, BigDecimal amount,
                              String currency, Instant createdAt, Instant expiresAt, String clientIp) {
}
