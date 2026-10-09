package mss301.bookingservice.service;

import java.util.UUID;

// Trung tâm chăm sóc bên ProviderService (Feign /internal/centers/{id}). Không gọi trong DB transaction đang mở.
public interface CenterPort {
    // Trung tâm phải tồn tại (CENTER_NOT_FOUND) và ACTIVE (CENTER_NOT_ACTIVE); Provider lỗi → 503.
    void checkBookable(UUID centerId);
}
