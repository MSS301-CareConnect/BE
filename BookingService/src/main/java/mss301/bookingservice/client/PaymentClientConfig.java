package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của PaymentClient — không @Configuration (xem IdentityClientConfig). Chính sách hiệu lực đọc bằng token
// của người dùng đang báo giá; mọi lỗi HTTP gom về PAYMENT_SERVICE_UNAVAILABLE.
public class PaymentClientConfig {

    @Bean
    public RequestInterceptor paymentRequestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }

    @Bean
    public ErrorDecoder paymentErrorDecoder() {
        return (methodKey, response) -> new AppException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
    }
}
