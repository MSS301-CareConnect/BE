package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class MediaClientConfig {

    @Bean
    public RequestInterceptor mediaRequestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }

    @Bean
    public ErrorDecoder mediaErrorDecoder() {
        return new MediaClientErrorDecoder();
    }
}
