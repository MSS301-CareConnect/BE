package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

// Cấu hình riêng của ProviderClient — không @Configuration (xem IdentityClientConfig).
public class ProviderClientConfig {

    // Chuyển tiếp token người dùng khi gọi ProviderService.
    @Bean
    public RequestInterceptor providerRequestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }

    // Bộ dịch lỗi HTTP khi gọi ProviderService.
    @Bean
    public ErrorDecoder providerErrorDecoder() {
        return new ProviderErrorDecoder();
    }
}
