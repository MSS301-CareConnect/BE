package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import org.springframework.context.annotation.Bean;

public class BookingArchiveClientConfig {
    @Bean
    public RequestInterceptor bookingArchiveAuth() {
        return new FeignAuthForwardingInterceptor();
    }

    @Bean
    public ErrorDecoder bookingArchiveErrorDecoder() {
        return (method, response) -> new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
    }
}
