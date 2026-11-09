package mss301.aiservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

// Nội dung AI đọc được trên ảnh chứng chỉ (null = không thấy trên ảnh).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertificateExtractedResponse {
    String holderName;
    String credentialNumber;
    String certificateName;
    String issuedBy;
    LocalDate issuedDate;
    LocalDate expiryDate;
}
