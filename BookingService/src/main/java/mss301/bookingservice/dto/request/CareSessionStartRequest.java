package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

// POST /api/v1/care-sessions — caregiver check-in một buổi (OPS-02). Tọa độ tùy chọn, chỉ lưu.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareSessionStartRequest {
    @NotNull(message = "OCCURRENCE_ID_REQUIRED")
    UUID occurrenceId;

    @DecimalMin(value = "-90", message = "INVALID_COORDINATES")
    @DecimalMax(value = "90", message = "INVALID_COORDINATES")
    BigDecimal latitude;

    @DecimalMin(value = "-180", message = "INVALID_COORDINATES")
    @DecimalMax(value = "180", message = "INVALID_COORDINATES")
    BigDecimal longitude;
}
