package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentSummaryResponse {
    UUID id;
    UUID bookingId;
    String status;
    BigDecimal amount;
    BigDecimal refundedAmount;
}
