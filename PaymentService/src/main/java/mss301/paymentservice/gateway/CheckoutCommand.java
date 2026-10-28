package mss301.paymentservice.gateway;

import lombok.Builder;
import mss301.paymentservice.enums.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record CheckoutCommand(PaymentProvider provider, String reference, String bookingCode, BigDecimal amount,
                              String currency, Instant expiresAt, String clientIp) {
}
