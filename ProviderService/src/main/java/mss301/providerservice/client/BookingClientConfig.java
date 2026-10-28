package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class BookingClientConfig {

    // Token SERVICE: endpoint busy-caregivers chỉ mở cho service (C5.1), không phụ thuộc quyền của người dùng hiện tại.
    @Bean
    public RequestInterceptor bookingRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    @Bean
    public ErrorDecoder bookingErrorDecoder() {
        return new BookingErrorDecoder();
    }
}
