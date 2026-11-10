package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import mss301.bookingservice.enums.CareTaskStatus;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionUpdateResponse {
    UUID sessionId;
    UUID taskId;
    CareTaskStatus status;
    UUID noteId;
}
