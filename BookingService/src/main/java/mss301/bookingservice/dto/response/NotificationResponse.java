package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import java.time.Instant;
import mss301.bookingservice.enums.NotificationType;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationResponse {
    UUID id;
    NotificationType type;
    String title;
    String body;
    String refType;
    UUID refId;
    Instant readAt;
    Instant createdAt;
}
