package mss301.aiservice.service;

import mss301.aiservice.dto.request.CertificateCheckRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.CertificateCheckResponse;

public interface CertificateCheckService {
    APIResponse<CertificateCheckResponse> check(CertificateCheckRequest request);
}
