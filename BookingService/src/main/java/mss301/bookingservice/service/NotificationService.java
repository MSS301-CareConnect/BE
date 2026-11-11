package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.NotificationRequest;
import mss301.bookingservice.dto.request.NotificationDeletionRequest;
import mss301.bookingservice.dto.request.NotificationReadStateRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.NotificationResponse;
import mss301.bookingservice.dto.response.NotificationsCreatedResponse;
import mss301.bookingservice.dto.response.NotificationsDeletedResponse;
import mss301.bookingservice.dto.response.NotificationsUpdatedResponse;
import mss301.bookingservice.dto.response.UnreadCountResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.NotificationType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {
    // Danh sách thông báo.
    APIResponse<PageResponse<NotificationResponse>> list(UUID userId, boolean unreadOnly, NotificationType type, int page, int size);
    // Số thông báo chưa đọc.
    APIResponse<UnreadCountResponse> unreadCount(UUID userId);
    // Đánh dấu đã đọc.
    APIResponse<Void> read(UUID userId, UUID id);
    // Đánh dấu tất cả đã đọc.
    APIResponse<Void> readAll(UUID userId);
    // Đánh dấu chưa đọc.
    APIResponse<Void> unread(UUID userId, UUID id);
    // Đổi trạng thái đọc nhiều thông báo.
    APIResponse<NotificationsUpdatedResponse> updateReadState(UUID userId, NotificationReadStateRequest request);
    // Xóa một thông báo.
    APIResponse<Void> delete(UUID userId, UUID id);
    // Xóa nhiều thông báo.
    APIResponse<NotificationsDeletedResponse> deleteMany(UUID userId, NotificationDeletionRequest request);
    // Xóa các thông báo đã đọc.
    APIResponse<NotificationsDeletedResponse> deleteRead(UUID userId);
    // Tạo thông báo cho service khác.
    APIResponse<NotificationsCreatedResponse> create(NotificationRequest request);
    // Mở luồng SSE.
    SseEmitter stream(UUID userId);
    // Gửi heartbeat SSE.
    void heartbeat();
    // Tạo thông báo cho danh sách người dùng.
    void notify(List<UUID> userIds, NotificationType type, Map<String, Object> params, String refType, UUID refId);
    // Đẩy cập nhật ca chăm sóc qua SSE.
    void publishSessionUpdate(UUID userId, Object payload);
}
