package mss301.paymentservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.*;

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
    PaymentProvider provider;
    PaymentStatus status;
    BigDecimal amount;
    String currency;
    String checkoutUrl;
    Instant expiresAt;
}
