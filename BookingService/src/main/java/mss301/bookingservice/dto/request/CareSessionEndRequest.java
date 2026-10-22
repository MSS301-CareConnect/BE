package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

// POST /api/v1/care-sessions/{id}/end — caregiver check-out (OPS-04). Tọa độ tùy chọn, chỉ lưu.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareSessionEndRequest {
    @DecimalMin(value = "-90", message = "INVALID_COORDINATES")
    @DecimalMax(value = "90", message = "INVALID_COORDINATES")
    BigDecimal latitude;

    @DecimalMin(value = "-180", message = "INVALID_COORDINATES")
    @DecimalMax(value = "180", message = "INVALID_COORDINATES")
    BigDecimal longitude;
}
