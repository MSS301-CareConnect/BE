package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.OccurrenceSlot;

import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.util.UUID;

public interface CaregiverScheduleGuard {

    // Tuần tự hóa mọi lệnh gán cùng một caregiver tới hết transaction hiện tại.
    void lockCaregiver(UUID caregiverId);

    // Trùng giờ với buổi đang giữ lịch của booking khác → 5215; sát hơn travel-buffer → 5248.
    void checkCaregiverFree(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId);

    // Như checkCaregiverFree nhưng trả false thay vì ném lỗi — dùng khi thử lần lượt nhiều ứng viên.
    boolean isCaregiverFree(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId);

    // 2026-11-13: giờ bắt đầu các buổi trùng / sát (travel-buffer) buổi của booking khác — một truy vấn cho cả khoảng.
    Set<Instant> findBusyStarts(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId);
}
