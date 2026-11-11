package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.context.annotation.Bean;

public class MediaClientConfig {

    // Gắn token SERVICE vào mọi lời gọi MediaService.
    @Bean
    public RequestInterceptor mediaRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    // Mọi lỗi HTTP từ MediaService coi là dịch vụ file không khả dụng.
    @Bean
    public ErrorDecoder mediaErrorDecoder() {
        return (methodKey, response) -> new AppException(ErrorCode.MEDIA_SERVICE_UNAVAILABLE);
    }
}
