package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.bookingservice.config.BookingProperties;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của CareRecipientClient — không @Configuration (xem IdentityClientConfig).
public class CareRecipientClientConfig {

    @Bean
    public RequestInterceptor careRecipientRequestInterceptor(BookingProperties bookingProperties) {
        return InternalClientHeaders.interceptor(bookingProperties.getCareRecipient().getInternalSecret());
    }

    @Bean
    public ErrorDecoder careRecipientErrorDecoder() {
        return new CareRecipientErrorDecoder();
    }
}
