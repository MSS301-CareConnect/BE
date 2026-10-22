package mss301.carerecipientservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class BookingClientConfig {

    // caregiver-access của BookingService yêu cầu hasRole('SERVICE') → gắn token SERVICE của CareRecipientService,
    // không chuyển tiếp token của caregiver đang gọi.
    @Bean
    public RequestInterceptor bookingRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    @Bean
    public ErrorDecoder bookingErrorDecoder() {
        return new BookingErrorDecoder();
    }
}
