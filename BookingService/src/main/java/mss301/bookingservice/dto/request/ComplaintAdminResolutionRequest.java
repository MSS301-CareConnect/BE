package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ComplaintAdminResolutionRequest {
    @NotBlank(message = "RESOLUTION_REQUIRED")
    @Size(max = 4000, message = "INVALID_REQUEST")
    String resolution;
    @DecimalMin(value = "0", message = "INVALID_REQUEST")
    @Digits(integer = 12, fraction = 0, message = "INVALID_REQUEST")
    BigDecimal refundAmount;
}
