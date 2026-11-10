package mss301.bookingservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverRatingRequest {
    BigDecimal ratingAverage;
    Integer ratingCount;
}
