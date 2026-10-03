package mss301.carerecipientservice.service;

import java.util.UUID;

/**
 * Caregiver được phân công có được xem hồ sơ người bệnh không. Chưa có BookingService nên CHƯA mở API nào cho
 * CAREGIVER.
 * TODO(BookingService): implement bằng Feign client gọi API nội bộ của Booking (caregiver có booking đang hiệu lực
 *  với recipient), rồi cho RecipientAccessService / InternalCareRecipientService dùng để cấp quyền đọc snapshot.
 */
public interface CaregiverAssignmentService {
    boolean isAssigned(UUID recipientId, UUID caregiverId);
}
