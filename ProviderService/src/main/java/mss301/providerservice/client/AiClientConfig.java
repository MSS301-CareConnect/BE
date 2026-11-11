package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class AiClientConfig {

    // Token SERVICE: endpoint certificate-checks chỉ mở cho service, không chạy trong request của người dùng.
    @Bean
    public RequestInterceptor aiRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    // Bộ dịch lỗi HTTP khi gọi AIService.
    @Bean
    public ErrorDecoder aiErrorDecoder() {
        return new AiErrorDecoder();
    }
}
