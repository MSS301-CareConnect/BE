package mss301.providerservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.response.CertificateAiExtractedResponse;
import mss301.providerservice.dto.response.CertificateAiWarningResponse;
import mss301.providerservice.enums.CertificateRiskLevel;

import java.time.Instant;
import java.util.List;

// Bản sao response của AIService; cảnh báo / nội dung đọc được dùng thẳng DTO trả cho admin (lưu dạng JSON).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertificateCheckClientResponse {
    CertificateRiskLevel riskLevel;

    List<CertificateAiWarningResponse> warnings;

    CertificateAiExtractedResponse extracted;

    String model;

    Instant checkedAt;
}
