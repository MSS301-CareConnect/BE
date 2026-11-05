package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceRescheduleResponse {
    UUID occurrenceId;
    UUID bookingId;
    Instant originalStartAt;
    Instant originalEndAt;
    Instant newStartAt;
    Instant newEndAt;
    Integer rescheduleCount;
    Boolean available;
    String message;
}
