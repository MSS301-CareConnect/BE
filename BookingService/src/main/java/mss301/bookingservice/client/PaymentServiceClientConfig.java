package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của PaymentServiceClient — không @Configuration. Token SERVICE của Booking, không phải token người dùng.
public class PaymentServiceClientConfig {

    @Bean
    public RequestInterceptor paymentServiceRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    @Bean
    public ErrorDecoder paymentServiceErrorDecoder() {
        return (methodKey, response) -> {
            int status = response.status();
            if (methodKey.contains("createComplaintRefund") && status >= 400 && status < 500 && status != 408 && status != 429) {
                return new AppException(ErrorCode.REMOTE_COMMAND_REJECTED);
            }
            return new AppException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
        };
    }
}
