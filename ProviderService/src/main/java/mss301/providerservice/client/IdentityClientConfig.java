package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class IdentityClientConfig {

    // Gắn token SERVICE vào mọi lời gọi IdentityService.
    @Bean
    public RequestInterceptor identityRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    // Bộ dịch lỗi HTTP khi gọi IdentityService.
    @Bean
    public ErrorDecoder identityErrorDecoder() {
        return new IdentityErrorDecoder();
    }
}
