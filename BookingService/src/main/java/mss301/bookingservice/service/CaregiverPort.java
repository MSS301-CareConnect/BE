package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.OccurrenceSlot;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// Caregiver bên ProviderService (qua gateway). Không gọi trong DB transaction đang mở.
public interface CaregiverPort {
    /**
     * Caregiver được phân công cho booking của trung tâm: thuộc đúng trung tâm, VERIFIED + ACTIVE, làm được mọi dịch vụ
     * đã chọn. Trả cả caregiver_id (Provider) lẫn user_id (Identity) — user_id không bao giờ lấy từ client.
     */
    AssignedCaregiver resolveAssignable(UUID centerId, UUID caregiverId, Collection<UUID> careServiceIds);

    // BK-02 (C5.2): caregiver có lịch làm việc và không nghỉ trong MỌI buổi → không thì 409 CAREGIVER_NOT_AVAILABLE;
    // Provider lỗi → 503. Trùng với booking khác vẫn do Booking kiểm (nguồn sự thật về giờ đã đặt).
    void checkAvailable(UUID caregiverId, List<OccurrenceSlot> slots);
}
