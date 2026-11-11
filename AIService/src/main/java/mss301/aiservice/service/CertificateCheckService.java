package mss301.aiservice.service;

import mss301.aiservice.dto.request.CertificateCheckRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.CertificateCheckResponse;

public interface CertificateCheckService {
    // AI đọc ảnh chứng chỉ và đánh giá mức rủi ro so với thông tin khai báo.
    APIResponse<CertificateCheckResponse> check(CertificateCheckRequest request);
}
