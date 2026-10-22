package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

// POST /api/v1/care-sessions/{id}/force-end — trung tâm buộc kết thúc ca caregiver quên đóng (OPS-04).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareSessionForceEndRequest {
    @NotBlank(message = "REASON_REQUIRED")
    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String reason;
}
