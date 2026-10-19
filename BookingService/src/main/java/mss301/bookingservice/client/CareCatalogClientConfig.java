package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class CareCatalogClientConfig {

    @Bean
    public RequestInterceptor careCatalogRequestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }

    @Bean
    public ErrorDecoder careCatalogErrorDecoder() {
        return new CareCatalogErrorDecoder();
    }
}
