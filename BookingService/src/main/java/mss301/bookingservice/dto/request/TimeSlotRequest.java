package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

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

    // Đặt lịch (2026-11-05): dịch vụ làm trong khung giờ này (NULL = mọi dịch vụ đã chọn) và, với SHIFT, ca caregiver
    // chứa khung giờ (NULL = caregiverScheduleId của booking). Kiểm lịch bận bỏ qua hai trường này.
    UUID careServiceId;

    UUID caregiverScheduleId;
}
