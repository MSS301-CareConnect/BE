package mss301.bookingservice.repository;

import java.time.Instant;

// Lúc tạo booking và lúc trung tâm trả lời (xác nhận / từ chối) — thống kê thời gian phản hồi.
public interface BookingResponseTimeView {
    Instant getCreatedAt();
    Instant getChangedAt();
}
