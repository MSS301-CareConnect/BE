package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.bookingservice.config.BookingProperties;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của ProviderClient — không @Configuration (xem IdentityClientConfig).
public class ProviderClientConfig {

    @Bean
    public RequestInterceptor providerRequestInterceptor(BookingProperties bookingProperties) {
        return InternalClientHeaders.interceptor(bookingProperties.getProvider().getInternalSecret());
    }

    @Bean
    public ErrorDecoder providerErrorDecoder() {
        return new ProviderErrorDecoder();
    }
}
