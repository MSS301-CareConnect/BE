package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class MediaClientConfig {

    // Gắn token SERVICE vào mọi lời gọi MediaService.
    @Bean
    public RequestInterceptor mediaRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    // Bộ dịch lỗi HTTP khi gọi MediaService.
    @Bean
    public ErrorDecoder mediaErrorDecoder() {
        return new MediaClientErrorDecoder();
    }
}
