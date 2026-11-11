package mss301.paymentservice.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của IdentityClient — không @Configuration để interceptor token SERVICE không lan sang client khác.
public class IdentityClientConfig {

    @Bean
    public RequestInterceptor identityServiceTokenInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }
}
