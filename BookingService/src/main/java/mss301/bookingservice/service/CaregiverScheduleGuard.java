package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.OccurrenceSlot;

import java.util.List;
import java.util.UUID;

/**
 * Chốt chống phân công trùng cho các luồng gán caregiver ngoài BookingServiceImpl (duyệt đổi người, tự phân công —
 * 2026-11-09). Cùng khóa tư vấn "booking:caregiver:<id>" và cùng quy tắc trùng giờ / travel-buffer với
 * BookingServiceImpl.lockSchedule + checkCaregiverAvailable. Chỉ gọi trong transaction ghi; exclusion constraint
 * ex_booking_occurrences_caregiver_slot vẫn là chốt cuối.
 */
public interface CaregiverScheduleGuard {

    // Tuần tự hóa mọi lệnh gán cùng một caregiver tới hết transaction hiện tại.
    void lockCaregiver(UUID caregiverId);

    // Trùng giờ với buổi đang giữ lịch của booking khác → 5215; sát hơn travel-buffer → 5248.
    void checkCaregiverFree(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId);

    // Như checkCaregiverFree nhưng trả false thay vì ném lỗi — dùng khi thử lần lượt nhiều ứng viên.
    boolean isCaregiverFree(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId);
}
