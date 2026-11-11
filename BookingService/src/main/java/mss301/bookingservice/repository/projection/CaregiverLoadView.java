package mss301.bookingservice.repository.projection;

import java.util.UUID;

// Tổng số phút caregiver đang được giao trong một khoảng — tự phân công ưu tiên người ít việc (2026-11-09).
public interface CaregiverLoadView {
    UUID getCaregiverId();
    Long getBookedMinutes();
}
