package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.request.NotificationRequest;
import mss301.bookingservice.dto.response.NotificationResponse;
import mss301.bookingservice.dto.response.UnreadCountResponse;
import mss301.bookingservice.dto.response.NotificationsCreatedResponse;
import mss301.bookingservice.dto.response.NotificationsDeletedResponse;
import mss301.bookingservice.dto.response.NotificationsUpdatedResponse;
import mss301.bookingservice.entity.Notification;
import mss301.bookingservice.enums.NotificationType;
import org.mapstruct.Mapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapping;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface NotificationMapper {
    // bookingId / occurrenceId do service gắn sau (updateLink) từ ref của thông báo.
    @Mapping(target = "bookingId", ignore = true)
    @Mapping(target = "occurrenceId", ignore = true)
    NotificationResponse toResponse(Notification notification);
    List<NotificationResponse> toResponseList(List<Notification> notifications);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "bookingId", source = "bookingId")
    @Mapping(target = "occurrenceId", source = "occurrenceId")
    void updateLink(UUID bookingId, UUID occurrenceId, @MappingTarget NotificationResponse response);

    @IgnoreServerManagedFields
    @Mapping(target = "readAt", ignore = true)
    Notification toNotification(NotificationRequest request, UUID recipientUserId);

    NotificationRequest toRequest(List<UUID> recipientUserIds, NotificationType type, String title, String body,
                                  String refType, UUID refId);
    UnreadCountResponse toUnreadCountResponse(Long count);
    NotificationsCreatedResponse toCreatedResponse(Long created);
    NotificationsDeletedResponse toDeletedResponse(Long deleted);
    NotificationsUpdatedResponse toUpdatedResponse(Long updated);
}
