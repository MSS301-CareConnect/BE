package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của ProviderServiceClient — không @Configuration. Token SERVICE của Booking; mọi lỗi HTTP = Provider
// không dùng được (fail-closed: không đặt / phân công khi chưa xác minh được lịch rảnh).
public class ProviderServiceClientConfig {

    @Bean
    public RequestInterceptor providerServiceRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    @Bean
    public ErrorDecoder providerServiceErrorDecoder() {
        // 404 caregiver → CAREGIVER_NOT_FOUND, còn lại → PROVIDER_UNAVAILABLE (giống ProviderClient).
        return (methodKey, response) -> {
            int status = response.status();
            if (methodKey.contains("updateRating") && status >= 400 && status < 500 && status != 408 && status != 429) {
                return new AppException(ErrorCode.REMOTE_COMMAND_REJECTED);
            }
            return new ProviderErrorDecoder().decode(methodKey, response);
        };
    }
}
