package mss301.bookingservice.service;

import mss301.bookingservice.client.dto.ProviderCenterResponse;

import java.util.UUID;

// Trung tâm chăm sóc bên ProviderService (Feign /internal/centers/{id}). Không gọi trong DB transaction đang mở.
public interface CenterPort {
    // Trung tâm phải tồn tại (CENTER_NOT_FOUND) và ACTIVE (CENTER_NOT_ACTIVE); Provider lỗi → 503.
    void checkBookable(UUID centerId);

    // Kiểm tra trung tâm ACTIVE và địa chỉ chăm sóc thuộc cùng khu vực địa bàn (tỉnh/thành phố) với trung tâm.
    void checkServiceArea(UUID centerId, String careProvince);

    // Lấy thông tin chi tiết trung tâm từ ProviderService.
    ProviderCenterResponse getCenter(UUID centerId);

    // Trung tâm phải tồn tại (mọi trạng thái) — admin cấu hình policy cho trung tâm bất kỳ; Provider lỗi → 503.
    void checkExists(UUID centerId);
}
