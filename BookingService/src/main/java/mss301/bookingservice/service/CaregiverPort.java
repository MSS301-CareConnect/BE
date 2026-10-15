package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.AssignedCaregiver;

import java.util.Collection;
import java.util.UUID;

// Caregiver bên ProviderService (Feign /internal/caregivers/{id}). Không gọi trong DB transaction đang mở.
public interface CaregiverPort {
    /**
     * Caregiver được phân công cho booking của trung tâm: thuộc đúng trung tâm, VERIFIED + ACTIVE, làm được mọi dịch vụ
     * đã chọn. Trả cả caregiver_id (Provider) lẫn user_id (Identity) — user_id không bao giờ lấy từ client.
     * TODO(Provider): kiểm khung giờ làm việc khi Provider có API (Booking vẫn là nguồn sự thật về giờ đã đặt).
     */
    AssignedCaregiver resolveAssignable(UUID centerId, UUID caregiverId, Collection<UUID> careServiceIds);
}
