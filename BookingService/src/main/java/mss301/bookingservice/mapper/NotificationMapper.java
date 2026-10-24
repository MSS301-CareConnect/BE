package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.request.NotificationRequest;
import mss301.bookingservice.dto.response.NotificationResponse;
import mss301.bookingservice.dto.response.UnreadCountResponse;
import mss301.bookingservice.dto.response.NotificationsCreatedResponse;
import mss301.bookingservice.entity.Notification;
import mss301.bookingservice.enums.NotificationType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface NotificationMapper {
    NotificationResponse toResponse(Notification notification);
    List<NotificationResponse> toResponseList(List<Notification> notifications);

    @IgnoreServerManagedFields
    @Mapping(target = "readAt", ignore = true)
    Notification toNotification(NotificationRequest request, UUID recipientUserId);

    NotificationRequest toRequest(List<UUID> recipientUserIds, NotificationType type, String title, String body,
                                  String refType, UUID refId);
    UnreadCountResponse toUnreadCountResponse(Long count);
    NotificationsCreatedResponse toCreatedResponse(Long created);
}
