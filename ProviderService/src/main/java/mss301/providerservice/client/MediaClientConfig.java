package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class MediaClientConfig {

    @Bean
    public RequestInterceptor mediaRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    @Bean
    public ErrorDecoder mediaErrorDecoder() {
        return new MediaClientErrorDecoder();
    }
}
