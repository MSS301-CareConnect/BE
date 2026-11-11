package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class CareCatalogClientConfig {

    // Chuyển tiếp token người dùng khi gọi CareCatalogService.
    @Bean
    public RequestInterceptor careCatalogRequestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }

    // Bộ dịch lỗi HTTP khi gọi CareCatalogService.
    @Bean
    public ErrorDecoder careCatalogErrorDecoder() {
        return new CareCatalogErrorDecoder();
    }
}
