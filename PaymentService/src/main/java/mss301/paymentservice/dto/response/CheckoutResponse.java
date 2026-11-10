package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.PaymentProvider;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CheckoutResponse {
    UUID paymentId;

    UUID bookingId;

    UUID invoiceId;

    PaymentType paymentType;

    PaymentProvider provider;

    PaymentStatus status;

    BigDecimal amount;

    String currency;

    String checkoutUrl;

    Instant expiresAt;
}
