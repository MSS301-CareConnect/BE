package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import java.time.Instant;
import mss301.bookingservice.enums.FeedbackVisibility;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FeedbackResponse {
    UUID id;
    UUID bookingId;
    UUID caregiverId;
    UUID centerId;
    String customerDisplayName;
    Integer rating;
    String comment;
    String reply;
    Instant repliedAt;
    FeedbackVisibility visibility;
    Instant createdAt;
}
