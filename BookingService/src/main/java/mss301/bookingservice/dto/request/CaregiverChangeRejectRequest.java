package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverChangeRejectRequest {

    @NotBlank(message = "REASON_REQUIRED")
    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String reason;
}
