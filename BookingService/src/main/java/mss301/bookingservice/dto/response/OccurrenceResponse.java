package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceResponse {
    UUID id;

    Integer occurrenceNo;

    Instant startAt;

    Instant endAt;

    Integer durationMinutes;

    Instant actualStartAt;

    Instant actualEndAt;

    UUID caregiverId;

    OccurrenceStatus status;

    String cancelReason;

    String note;
}
