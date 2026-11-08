package mss301.providerservice.client;

import mss301.providerservice.client.dto.AiCertificateVerificationRequest;
import mss301.providerservice.client.dto.AiCertificateVerificationResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ai", url = "${provider.ai.url}", configuration = AIClientConfig.class)
public interface AIClient {

    @PostMapping("/api/v1/ai/certificate-verifications")
    RemoteApiResponse<AiCertificateVerificationResponse> verifyCertificate(@RequestBody AiCertificateVerificationRequest request);
}
