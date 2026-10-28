package mss301.paymentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingPaymentContextResponse {
    UUID bookingId;
    String bookingCode;
    UUID customerId;
    UUID centerId;
    String status;
    BigDecimal totalAmount;
    Instant depositDueAt;
    Instant depositPaidAt;
}
