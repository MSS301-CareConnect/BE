package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionVitals {
    @Positive(message = "INVALID_VITALS")
    Integer systolic;
    @Positive(message = "INVALID_VITALS")
    Integer diastolic;
    @Positive(message = "INVALID_VITALS")
    Integer heartRate;
    @DecimalMin(value = "30", message = "INVALID_VITALS")
    @DecimalMax(value = "45", message = "INVALID_VITALS")
    BigDecimal temperature;
    @Min(value = 0, message = "INVALID_VITALS")
    @Max(value = 100, message = "INVALID_VITALS")
    Integer spo2;
    @PositiveOrZero(message = "INVALID_VITALS")
    BigDecimal bloodGlucose;
}
