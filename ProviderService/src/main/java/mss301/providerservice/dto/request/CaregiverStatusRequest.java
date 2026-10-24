package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CaregiverStatus;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverStatusRequest {
    @NotNull(message = "INVALID_REQUEST")
    CaregiverStatus status;
    @NotNull(message = "INVALID_REQUEST")
    Long version;
}
