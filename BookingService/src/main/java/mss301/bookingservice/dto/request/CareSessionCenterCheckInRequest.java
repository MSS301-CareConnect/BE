package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

// POST /api/v1/care-sessions/center-check-in — caregiver quên bấm check-in, báo trung tâm ghi nhận hộ (2026-11-13).
// checkedInAt: giờ caregiver thực tế đến (bỏ trống = lúc ghi nhận), phải trong khung check-in của buổi và không ở tương lai.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareSessionCenterCheckInRequest {
    @NotNull(message = "OCCURRENCE_ID_REQUIRED")
    UUID occurrenceId;

    Instant checkedInAt;

    @NotBlank(message = "REASON_REQUIRED")
    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String note;
}
