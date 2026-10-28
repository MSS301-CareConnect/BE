package mss301.paymentservice.config;

import feign.RequestInterceptor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.client.ServiceTokenProvider;
import org.springframework.context.annotation.Bean;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FeignConfig {
    ServiceTokenProvider serviceTokenProvider;

    @Bean
    RequestInterceptor serviceTokenInterceptor() {
        return template -> template.header("Authorization", "Bearer " + serviceTokenProvider.getToken());
    }
}
