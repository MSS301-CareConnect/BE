package mss301.aiservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.enums.CertificateRiskLevel;

import java.time.Instant;
import java.util.List;

// Kết quả kiểm tra một chứng chỉ: mức rủi ro, cảnh báo, nội dung đọc được (null khi AI không đọc được ảnh).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertificateCheckResponse {
    CertificateRiskLevel riskLevel;
    List<CertificateWarningResponse> warnings;
    CertificateExtractedResponse extracted;
    String model;
    Instant checkedAt;
}
