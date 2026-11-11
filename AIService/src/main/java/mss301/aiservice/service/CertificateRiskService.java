package mss301.aiservice.service;

import mss301.aiservice.dto.request.CertificateCheckRequest;
import mss301.aiservice.dto.response.CertificateExtractedResponse;
import mss301.aiservice.dto.response.CertificateWarningResponse;
import mss301.aiservice.enums.CertificateRiskLevel;

import java.time.LocalDate;
import java.util.List;

// Đối chiếu thông tin khai báo với nội dung AI đọc được và quy ra mức rủi ro — thuần, không gọi mạng.
public interface CertificateRiskService {
    // So thông tin khai báo với thông tin AI đọc được, trả danh sách cảnh báo.
    List<CertificateWarningResponse> compare(CertificateCheckRequest declared, CertificateExtractedResponse extracted,
                                             LocalDate today);

    // Quy đổi danh sách cảnh báo thành mức rủi ro.
    CertificateRiskLevel toRiskLevel(List<CertificateWarningResponse> warnings);
}
