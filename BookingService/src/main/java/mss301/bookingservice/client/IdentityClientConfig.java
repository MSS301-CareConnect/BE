package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.bookingservice.config.BookingProperties;
import org.springframework.context.annotation.Bean;

/**
 * Cấu hình riêng của {@link IdentityClient}. Cố ý KHÔNG có @Configuration: nếu bị component scan, interceptor gắn
 * secret của Identity sẽ áp cho mọi Feign client khác.
 */
public class IdentityClientConfig {

    @Bean
    public RequestInterceptor identityRequestInterceptor(BookingProperties bookingProperties) {
        return InternalClientHeaders.interceptor(bookingProperties.getIdentity().getInternalSecret());
    }

    @Bean
    public ErrorDecoder identityErrorDecoder() {
        return new IdentityErrorDecoder();
    }
}
