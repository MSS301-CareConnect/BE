package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.VerificationStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VerificationRequest {

    @NotNull
    VerificationStatus status;

    @Size(max = 500)
    String reason;

    @NotNull
    Long version;
}
