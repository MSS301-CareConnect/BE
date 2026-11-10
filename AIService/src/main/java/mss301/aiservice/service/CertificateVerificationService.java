package mss301.aiservice.service;

import mss301.aiservice.dto.request.CertificateVerificationRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.CertificateVerificationResponse;

public interface CertificateVerificationService {
    APIResponse<CertificateVerificationResponse> verify(CertificateVerificationRequest request);
}
