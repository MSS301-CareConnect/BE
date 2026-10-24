package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * Cấu hình riêng của {@link IdentityClient}. Cố ý KHÔNG có @Configuration.
 */
public class IdentityClientConfig {

    @Bean
    public RequestInterceptor identityRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    @Bean
    public ErrorDecoder identityErrorDecoder() {
        return new IdentityErrorDecoder();
    }
}
