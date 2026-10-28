package mss301.paymentservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentAttemptResponse {
    UUID id;
    PaymentProvider provider;
    PaymentStatus status;
    String reference;
    String providerTransactionId;
    Instant completedAt;
}
