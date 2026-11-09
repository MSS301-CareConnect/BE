package mss301.providerservice.client;

import mss301.providerservice.client.dto.CertificateCheckClientRequest;
import mss301.providerservice.client.dto.CertificateCheckClientResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// AIService qua ApiGateway (route AI có read timeout dài) — đọc ảnh chứng chỉ và đánh giá rủi ro (bước 4).
@FeignClient(name = "ai", url = "${provider.ai.url}",
        configuration = AiClientConfig.class,
        fallbackFactory = AiClientFallbackFactory.class)
public interface AiClient {

    @PostMapping("/api/v1/ai/certificate-checks")
    RemoteApiResponse<CertificateCheckClientResponse> checkCertificate(@RequestBody CertificateCheckClientRequest request);
}
