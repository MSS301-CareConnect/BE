package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceReschedulePreviewRequest {

    @NotNull(message = "START_TIME_REQUIRED")
    Instant newStartAt;

    @NotNull(message = "END_TIME_REQUIRED")
    Instant newEndAt;
}
