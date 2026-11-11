package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Caregiver nhận nhanh nhiều buổi của CÙNG một booking trong một lệnh (2026-11-10) — tất cả hoặc không buổi nào.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceAcknowledgementRequest {
    @NotEmpty(message = "OCCURRENCE_ID_REQUIRED")
    @Size(max = 100, message = "TOO_MANY_OCCURRENCES")
    List<@NotNull(message = "OCCURRENCE_ID_REQUIRED") UUID> occurrenceIds;
}
