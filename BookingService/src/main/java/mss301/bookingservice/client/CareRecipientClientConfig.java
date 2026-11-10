package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của CareRecipientClient — không @Configuration (xem IdentityClientConfig).
public class CareRecipientClientConfig {

    @Bean
    // snapshot / access của CareRecipient chỉ cho SERVICE (người dùng không được dò hồ sơ / quyền của người khác).
    public RequestInterceptor careRecipientRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    @Bean
    public ErrorDecoder careRecipientErrorDecoder() {
        return new CareRecipientErrorDecoder();
    }
}
