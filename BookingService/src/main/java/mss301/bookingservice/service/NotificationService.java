package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.NotificationRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.NotificationResponse;
import mss301.bookingservice.dto.response.NotificationsCreatedResponse;
import mss301.bookingservice.dto.response.UnreadCountResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.NotificationType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {
    APIResponse<PageResponse<NotificationResponse>> list(UUID userId, boolean unreadOnly, NotificationType type, int page, int size);
    APIResponse<UnreadCountResponse> unreadCount(UUID userId);
    APIResponse<Void> read(UUID userId, UUID id);
    APIResponse<Void> readAll(UUID userId);
    APIResponse<NotificationsCreatedResponse> create(NotificationRequest request);
    SseEmitter stream(UUID userId);
    void heartbeat();
    void notify(List<UUID> userIds, NotificationType type, Map<String, Object> params, String refType, UUID refId);
    void publishSessionUpdate(UUID userId, Object payload);
}
