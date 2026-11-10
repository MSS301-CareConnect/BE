package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.context.annotation.Bean;

public class MediaClientConfig {

    @Bean
    public RequestInterceptor mediaRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    @Bean
    public ErrorDecoder mediaErrorDecoder() {
        return (methodKey, response) -> new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
    }
}
