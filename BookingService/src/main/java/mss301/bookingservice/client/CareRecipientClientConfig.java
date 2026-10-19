package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của CareRecipientClient — không @Configuration (xem IdentityClientConfig).
public class CareRecipientClientConfig {

    @Bean
    public RequestInterceptor careRecipientRequestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }

    @Bean
    public ErrorDecoder careRecipientErrorDecoder() {
        return new CareRecipientErrorDecoder();
    }
}
