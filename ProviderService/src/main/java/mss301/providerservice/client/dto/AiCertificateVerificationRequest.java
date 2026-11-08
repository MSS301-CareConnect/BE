package mss301.providerservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiCertificateVerificationRequest {
    UUID certificateId;
    String caregiverFullName;
    String certificateName;
    String issuedBy;
    String credentialNumber;
    LocalDate issuedDate;
    LocalDate expiryDate;
    List<String> imageUrls;
}
