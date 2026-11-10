package mss301.aiservice.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

public class MediaClientConfig {
    @Bean
    public RequestInterceptor mediaRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }
}
