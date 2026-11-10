package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateVerificationRequest {
    @NotNull(message = "INVALID_REQUEST")
    Boolean approved;
    @Size(max = 500, message = "VERIFICATION_REASON_REQUIRED")
    String reason;
    @NotNull(message = "CERTIFICATE_VERSION_CONFLICT")
    Long version;
}
