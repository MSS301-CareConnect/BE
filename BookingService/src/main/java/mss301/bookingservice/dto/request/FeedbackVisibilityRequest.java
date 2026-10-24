package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FeedbackVisibilityRequest {
    @NotNull(message = "INVALID_REQUEST")
    Boolean visible;
    @Size(max = 1000, message = "INVALID_REQUEST")
    String reason;
}
