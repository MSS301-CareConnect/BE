package mss301.aiservice.service;

import mss301.aiservice.dto.request.CertificateVerificationRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.CertificateVerificationResponse;

public interface CertificateVerificationService {
    // Kiểm tra sơ bộ chứng chỉ bằng OCR + QR và so với thông tin khai báo.
    APIResponse<CertificateVerificationResponse> verify(CertificateVerificationRequest request);
}
