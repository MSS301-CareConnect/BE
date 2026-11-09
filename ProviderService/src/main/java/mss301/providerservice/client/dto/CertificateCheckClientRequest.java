package mss301.providerservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;

// Bản sao request của AIService POST /api/v1/ai/certificate-checks.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateCheckClientRequest {
    // Họ tên trên hồ sơ caregiver — so với tên in trên chứng chỉ.
    String holderName;
    String certificateName;
    String issuedBy;
    String credentialNumber;
    LocalDate issuedDate;
    LocalDate expiryDate;
    List<CertificateImageClientRequest> images;
}
