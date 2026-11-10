package mss301.bookingservice.repository;

import java.time.Instant;
import java.util.UUID;

// Projection BK-06: thống kê buổi của từng booking cho danh sách (một truy vấn gộp cho cả trang).
public interface OccurrenceStatsView {
    UUID getBookingId();

    Long getTotalCount();

    Long getCompletedCount();

    // Buổi còn hoạt động chưa kết thúc sớm nhất; NULL khi không còn buổi nào.
    Instant getNextOccurrenceAt();
}
