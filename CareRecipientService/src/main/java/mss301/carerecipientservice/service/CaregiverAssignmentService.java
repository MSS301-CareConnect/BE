package mss301.carerecipientservice.service;

import java.util.UUID;

/**
 * Caregiver được phân công có được xem hồ sơ người bệnh không — hỏi BookingService (nguồn sự thật về phân công):
 * có booking CONFIRMED / IN_PROGRESS của người bệnh mà caregiver là người chăm sóc của booking hoặc của một buổi còn
 * hoạt động. caregiverUserId = user_id Identity (= sub JWT của caregiver). Booking lỗi → 503 BOOKING_UNAVAILABLE.
 * Gọi mạng — không gọi trong DB transaction đang mở.
 */
public interface CaregiverAssignmentService {
    boolean isAssigned(UUID recipientId, UUID caregiverUserId);
}
