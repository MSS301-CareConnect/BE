package mss301.carerecipientservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.carerecipientservice.config.CareRecipientProperties;
import mss301.carerecipientservice.config.RequestIdFilter;
import mss301.carerecipientservice.security.InternalSecretFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;

/**
 * Cấu hình riêng của {@link BookingClient} (secret của BookingService, khác secret của Identity). Cố ý KHÔNG có
 * @Configuration — xem {@link IdentityClientConfig}.
 */
public class BookingClientConfig {

    @Bean
    public RequestInterceptor bookingRequestInterceptor(CareRecipientProperties careRecipientProperties) {
        String internalSecret = careRecipientProperties.getBooking().getInternalSecret();
        return template -> {
            template.header(InternalSecretFilter.HEADER, internalSecret);
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                template.header(RequestIdFilter.HEADER, requestId);
            }
        };
    }

    @Bean
    public ErrorDecoder bookingErrorDecoder() {
        return new BookingErrorDecoder();
    }
}
