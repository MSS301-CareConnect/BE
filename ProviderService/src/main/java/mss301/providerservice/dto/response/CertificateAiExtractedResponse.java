package mss301.providerservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

// Nội dung AI đọc được trên ảnh chứng chỉ (chỉ ADMIN thấy); null = không thấy trên ảnh.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertificateAiExtractedResponse {
    String holderName;
    String credentialNumber;
    String certificateName;
    String issuedBy;
    LocalDate issuedDate;
    LocalDate expiryDate;
}
