package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

// Khoảng nghỉ [startAt, endAt) — caregiver lấy từ path, người tạo lấy từ token (không nhận từ request).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TimeOffRequest {

    @NotNull(message = "TIME_OFF_TIME_REQUIRED")
    Instant startAt;

    @NotNull(message = "TIME_OFF_TIME_REQUIRED")
    Instant endAt;

    @Size(max = 500, message = "TIME_OFF_REASON_TOO_LONG")
    String reason;
}
