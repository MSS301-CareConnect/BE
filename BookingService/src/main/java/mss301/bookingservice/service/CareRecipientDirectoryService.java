package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.RecipientSnapshot;

import java.util.UUID;

// Bọc CareRecipientClient (Feign). Không gọi trong DB transaction đang mở (rule #24).
public interface CareRecipientDirectoryService {
    /**
     * Snapshot hồ sơ để đặt lịch: khách phải có quyền ≥ EDITOR (không đủ / không có quan hệ → 404 RECIPIENT_NOT_FOUND,
     * không lộ hồ sơ tồn tại), hồ sơ phải ACTIVE (RECIPIENT_NOT_ACTIVE). CareRecipient lỗi → 503, không đoán.
     */
    RecipientSnapshot getBookableSnapshot(UUID recipientId, UUID customerId);
}
