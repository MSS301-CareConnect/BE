package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

// Một khoảng thời gian nửa mở [startAt, endAt) — dùng cho kiểm lịch bận giữa các service.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TimeSlotRequest {
    @NotNull(message = "INVALID_TIME_SLOT")
    Instant startAt;

    @NotNull(message = "INVALID_TIME_SLOT")
    Instant endAt;
}
