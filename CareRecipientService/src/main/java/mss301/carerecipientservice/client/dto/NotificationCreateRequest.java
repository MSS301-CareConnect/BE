package mss301.carerecipientservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Bản sao NotificationRequest của BookingService (POST /api/v1/notifications, token SERVICE).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationCreateRequest {
    List<UUID> recipientUserIds;

    String type;

    String title;

    String body;

    String refType;

    UUID refId;
}
