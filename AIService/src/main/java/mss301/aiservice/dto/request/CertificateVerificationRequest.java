package mss301.aiservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateVerificationRequest {
    @NotNull
    UUID certificateId;

    @NotBlank
    String caregiverFullName;

    @NotBlank
    String certificateName;

    @NotBlank
    String issuedBy;

    String credentialNumber;

    LocalDate issuedDate;

    LocalDate expiryDate;

    @NotEmpty(message = "CERTIFICATE_IMAGE_REQUIRED")
    @Size(max = 2, message = "CERTIFICATE_IMAGE_LIMIT_EXCEEDED")
    List<@NotBlank String> imageUrls;
}
