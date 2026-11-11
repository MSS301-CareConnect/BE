package mss301.bookingservice.repository.projection;

import mss301.bookingservice.enums.OccurrenceStatus;

import java.time.Instant;

// Giờ bắt đầu + trạng thái của một buổi — gom theo ngày / tháng cho biểu đồ xu hướng.
public interface OccurrenceStartView {
    Instant getStartAt();
    OccurrenceStatus getStatus();
}
