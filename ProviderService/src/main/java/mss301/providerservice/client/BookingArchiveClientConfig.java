package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import org.springframework.context.annotation.Bean;

public class BookingArchiveClientConfig {
    // Chuyển tiếp token người dùng khi gọi BookingService.
    @Bean
    public RequestInterceptor bookingArchiveAuth() {
        return new FeignAuthForwardingInterceptor();
    }

    // Mọi lỗi HTTP từ BookingService coi là dịch vụ không khả dụng.
    @Bean
    public ErrorDecoder bookingArchiveErrorDecoder() {
        return (method, response) -> new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
    }
}
