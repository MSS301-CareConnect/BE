package mss301.aiservice.mapper;

import mss301.aiservice.dto.response.CertificateCheckResponse;
import mss301.aiservice.dto.response.CertificateExtractedResponse;
import mss301.aiservice.dto.response.CertificateWarningResponse;
import mss301.aiservice.enums.CertificateCheckField;
import mss301.aiservice.enums.CertificateRiskLevel;
import mss301.aiservice.enums.CertificateWarningCode;
import org.mapstruct.Mapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Kiểm tra chứng chỉ không có entity — mapper chỉ dựng response từ giá trị service đã tính.
@Mapper(config = MapStructConfig.class)
public interface CertificateCheckMapper {
    CertificateExtractedResponse toExtractedResponse(String holderName, String credentialNumber,
                                                     String certificateName, String issuedBy,
                                                     LocalDate issuedDate, LocalDate expiryDate);

    CertificateWarningResponse toWarningResponse(CertificateWarningCode code, CertificateCheckField field,
                                                 String declaredValue, String extractedValue);

    CertificateCheckResponse toResponse(CertificateRiskLevel riskLevel, List<CertificateWarningResponse> warnings,
                                        CertificateExtractedResponse extracted, String model, Instant checkedAt);
}
