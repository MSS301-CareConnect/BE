package mss301.carerecipientservice.service;

import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;

import java.util.Optional;
import java.util.UUID;

// Phân quyền trên hồ sơ theo quan hệ người quản lý (OWNER > EDITOR > VIEWER). Dùng nội bộ bởi các service khác.
public interface RecipientAccessService {
    // Không quản lý hồ sơ (hoặc hồ sơ đã lưu trữ) → 404 RECIPIENT_NOT_FOUND (không lộ hồ sơ tồn tại);
    // có quan hệ nhưng thiếu cấp → 403 RECIPIENT_ACCESS_DENIED. Trả quan hệ (đã nạp hồ sơ).
    RecipientManager checkAccess(UUID recipientId, UUID userId, RecipientAccessLevel requiredLevel);

    void checkActiveForWrite(UUID recipientId);

    Optional<RecipientAccessLevel> findAccessLevel(UUID recipientId, UUID userId);
}
