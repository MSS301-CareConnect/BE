package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class CareCatalogClientConfig {

    // Token SERVICE thay cho token người dùng: kiểm offering là việc của Provider, không phụ thuộc quyền của manager.
    @Bean
    public RequestInterceptor careCatalogRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    @Bean
    public ErrorDecoder careCatalogErrorDecoder() {
        return new CareCatalogErrorDecoder();
    }
}
