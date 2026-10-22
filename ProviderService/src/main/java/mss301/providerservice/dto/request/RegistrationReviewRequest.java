package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.RegistrationStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegistrationReviewRequest {

    @NotNull
    RegistrationStatus status;

    @Size(max = 500)
    String reason;

    @Size(max = 2000)
    String scheduleNote;

    @NotNull
    Long version;
}
