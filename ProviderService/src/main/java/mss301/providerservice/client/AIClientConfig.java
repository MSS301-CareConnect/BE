package mss301.providerservice.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class AIClientConfig {

    @Bean
    public RequestInterceptor aiRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }
}
