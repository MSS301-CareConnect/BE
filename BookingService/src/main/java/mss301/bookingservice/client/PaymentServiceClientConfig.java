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
        return (methodKey, response) -> new AppException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
    }
}
