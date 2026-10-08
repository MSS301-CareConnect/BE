package mss301.providerservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateRequest {
    @NotBlank @Size(max = 255) String certificateName;
    @NotBlank @Size(max = 255) String issuedBy;
    @Size(max = 255) String credentialNumber;
    LocalDate issuedDate;
    LocalDate expiryDate;
    Long version;
}
