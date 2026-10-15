package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.OccurrenceSlot;

import java.util.List;
import java.util.UUID;

/**
 * Chống trùng lịch dựa trên buổi đang hoạt động trong Booking (nguồn sự thật duy nhất về giờ đã đặt). Gọi TRONG
 * transaction ghi: lock trước rồi mới check, để hai request song song không cùng vượt qua.
 */
public interface ScheduleConflictService {
    // Khóa tư vấn theo caregiver rồi người bệnh (thứ tự cố định, tránh deadlock); id null được bỏ qua.
    void lock(UUID caregiverId, UUID recipientId);

    // excludeBookingId: bỏ qua buổi của chính booking đang đổi caregiver (null khi tạo mới).
    void checkCaregiverAvailable(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId);

    void checkRecipientAvailable(UUID recipientId, List<OccurrenceSlot> slots, UUID excludeBookingId);
}
