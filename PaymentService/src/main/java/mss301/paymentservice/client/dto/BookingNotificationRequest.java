package mss301.paymentservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Bản sao NotificationRequest của BookingService (POST /api/v1/notifications, token SERVICE) — tên field phải trùng vì Booking
// bật fail-on-unknown-properties. type là tên NotificationType của Booking (vd REFUND_COMPLETED).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingNotificationRequest {
    List<UUID> recipientUserIds;

    String type;

    String title;

    String body;

    String refType;

    UUID refId;
}
