package mss301.paymentservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của BookingClient — không @Configuration để interceptor token SERVICE không lan sang client khác.
public class BookingClientConfig {

    @Bean
    public RequestInterceptor bookingServiceTokenInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    // 404 = booking không tồn tại; 4xx khác = Booking từ chối lệnh (không retry); còn lại = tạm thời không khả dụng.
    @Bean
    public ErrorDecoder bookingErrorDecoder() {
        return (methodKey, response) -> {
            int status = response.status();
            if (status == 404) {
                return new AppException(ErrorCode.BOOKING_NOT_FOUND);
            }
            if (status >= 400 && status < 500 && status != 408 && status != 429) {
                return new AppException(ErrorCode.BOOKING_COMMAND_REJECTED);
            }
            return new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
        };
    }
}
