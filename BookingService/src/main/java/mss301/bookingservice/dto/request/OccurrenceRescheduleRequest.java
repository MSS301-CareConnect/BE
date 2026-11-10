package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceRescheduleRequest {

    @NotNull(message = "START_TIME_REQUIRED")
    Instant newStartAt;

    @NotNull(message = "END_TIME_REQUIRED")
    Instant newEndAt;

    @Size(max = 500, message = "TEXT_TOO_LONG")
    String reason;
}
