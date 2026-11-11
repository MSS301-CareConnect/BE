package mss301.aiservice.dto.response;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateVerificationResponse {
    UUID certificateId;

    String extractedText;

    String qrContent;

    boolean caregiverNameMatched;

    boolean certificateNameMatched;

    boolean issuedByMatched;

    boolean credentialNumberMatched;

    boolean issuedDateMatched;

    boolean expiryDateMatched;

    boolean expired;

    String riskLevel;

    List<String> warnings;

    Instant checkedAt;
}
