package mss301.providerservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiCertificateVerificationResponse {
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
