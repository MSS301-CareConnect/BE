package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverChangeApproveRequest {

    @NotNull(message = "CAREGIVER_ID_REQUIRED")
    UUID newCaregiverId;

    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String note;
}
