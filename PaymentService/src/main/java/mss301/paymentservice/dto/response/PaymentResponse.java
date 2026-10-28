package mss301.paymentservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

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
    PaymentStatus status;
    BigDecimal amount;
    BigDecimal refundedAmount;
    String currency;
    Instant paidAt;
    List<PaymentAttemptResponse> attempts;
}
