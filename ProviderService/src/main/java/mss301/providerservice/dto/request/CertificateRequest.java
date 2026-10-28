package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateRequest {

    @NotBlank(message = "INVALID_REQUEST")
    @Size(max = 255, message = "INVALID_REQUEST")
    String certificateName;

    @NotBlank(message = "INVALID_REQUEST")
    @Size(max = 255, message = "INVALID_REQUEST")
    String issuedBy;

    @Size(max = 255, message = "INVALID_REQUEST")
    String credentialNumber;

    LocalDate issuedDate;

    LocalDate expiryDate;

    Long version;
}
