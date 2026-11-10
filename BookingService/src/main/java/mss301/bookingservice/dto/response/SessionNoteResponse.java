package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import java.time.Instant;
import mss301.bookingservice.dto.request.SessionVitals;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionNoteResponse {
    UUID id;
    UUID sessionId;
    String content;
    SessionVitals vitals;
    UUID createdBy;
    Instant createdAt;
}
