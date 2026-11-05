package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmergencyReassignmentRequest {

    @NotEmpty(message = "OCCURRENCES_REQUIRED")
    List<UUID> occurrenceIds;

    @NotNull(message = "CAREGIVER_ID_REQUIRED")
    UUID newCaregiverId;

    @NotBlank(message = "REASON_REQUIRED")
    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String reason;
}
