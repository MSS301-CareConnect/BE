package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

// Một buổi do OccurrenceGenerator sinh (UTC) — nội bộ, chưa phải entity.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceSlot {
    int occurrenceNo;

    Instant startAt;

    Instant endAt;

    int durationMinutes;
}
