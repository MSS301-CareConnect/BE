package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

// Một buổi trong báo giá kèm giá buổi.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteOccurrenceResponse {
    Integer occurrenceNo;

    Instant startAt;

    Instant endAt;

    BigDecimal priceAmount;
}
