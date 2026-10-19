package mss301.carerecipientservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class BookingClientConfig {

    @Bean
    public RequestInterceptor bookingRequestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }

    @Bean
    public ErrorDecoder bookingErrorDecoder() {
        return new BookingErrorDecoder();
    }
}
