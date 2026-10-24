package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import java.time.Instant;
import mss301.bookingservice.enums.ComplaintStatus;
import mss301.bookingservice.enums.ComplaintAction;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ComplaintEventResponse {
    UUID id;
    UUID actorId;
    ComplaintAction action;
    ComplaintStatus fromStatus;
    ComplaintStatus toStatus;
    String note;
    Instant createdAt;
}
