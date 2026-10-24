package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.NotificationType;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@AttributeOverride(name = "id", column = @Column(name = "notification_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification extends BaseEntity {
    @Column(name = "recipient_user_id", nullable = false)
    UUID recipientUserId;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    NotificationType type;
    @Column(name = "title", nullable = false, length = 200)
    String title;
    @Column(name = "body", nullable = false, length = 1000)
    String body;
    @Column(name = "ref_type", length = 80)
    String refType;
    @Column(name = "ref_id")
    UUID refId;
    @Column(name = "read_at")
    Instant readAt;
}
