package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.SystemCancellationReason;

import java.util.UUID;

// BK-04 (SERVICE): CENTER_LOCKED cần centerId, CAREGIVER_DEACTIVATED cần caregiverId (caregiver_profiles.caregiver_id).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SystemCancellationRequest {
    UUID centerId;

    UUID caregiverId;

    @NotNull(message = "SYSTEM_CANCELLATION_REASON_REQUIRED")
    SystemCancellationReason reason;

    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String note;
}
