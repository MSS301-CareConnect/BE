package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mss301.bookingservice.enums.NotificationType;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationRequest {
    @NotEmpty(message = "INVALID_REQUEST")
    @Size(max = 500, message = "TOO_MANY_RECIPIENTS")
    List<@NotNull(message = "INVALID_REQUEST") UUID> recipientUserIds;
    @NotNull(message = "INVALID_REQUEST")
    NotificationType type;
    @NotBlank(message = "INVALID_REQUEST")
    @Size(max = 200, message = "INVALID_REQUEST")
    String title;
    @NotBlank(message = "INVALID_REQUEST")
    @Size(max = 1000, message = "INVALID_REQUEST")
    String body;
    @Size(max = 80, message = "INVALID_REQUEST")
    String refType;
    UUID refId;
}
