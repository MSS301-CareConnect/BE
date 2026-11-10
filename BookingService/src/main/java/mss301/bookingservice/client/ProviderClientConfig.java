package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của ProviderClient — không @Configuration (xem IdentityClientConfig).
public class ProviderClientConfig {

    @Bean
    public RequestInterceptor providerRequestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }

    @Bean
    public ErrorDecoder providerErrorDecoder() {
        return new ProviderErrorDecoder();
    }
}
