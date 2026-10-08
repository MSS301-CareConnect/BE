package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.VerificationStatus;

import java.time.*;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateResponse {
    UUID id;
    UUID caregiverId;
    String certificateName;
    String issuedBy;
    String credentialNumber;
    LocalDate issuedDate;
    LocalDate expiryDate;
    VerificationStatus verificationStatus;
    String verificationReason;
    UUID verifiedBy;
    Instant verifiedAt;
    Long version;
    Instant createdAt;
    Instant updatedAt;
}
