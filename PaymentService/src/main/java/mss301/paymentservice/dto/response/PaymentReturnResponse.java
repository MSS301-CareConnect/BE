package mss301.paymentservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.PaymentStatus;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentReturnResponse {
    UUID paymentId;
    UUID bookingId;
    PaymentStatus status;
    String message;
}
