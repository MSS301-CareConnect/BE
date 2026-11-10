package mss301.providerservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Digits;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverRatingRequest {
    @NotNull(message = "INVALID_REQUEST")
    @DecimalMin(value = "0", message = "INVALID_REQUEST")
    @DecimalMax(value = "5", message = "INVALID_REQUEST")
    @Digits(integer = 1, fraction = 2, message = "INVALID_REQUEST")
    BigDecimal ratingAverage;
    @NotNull(message = "INVALID_REQUEST")
    @Min(value = 0, message = "INVALID_REQUEST")
    Integer ratingCount;
}
