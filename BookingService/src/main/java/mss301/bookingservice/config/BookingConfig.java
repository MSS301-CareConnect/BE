package mss301.bookingservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(BookingProperties.class)
public class BookingConfig {

    // Một nguồn "bây giờ" cho sinh lịch / hạn duyệt / hạn cọc — test thay bằng Clock cố định.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // Client xin token SERVICE ở IdentityService qua gateway (ServiceTokenProvider) — timeout riêng, không qua Feign.
    @Bean
    public RestClient serviceTokenRestClient(BookingProperties bookingProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(bookingProperties.getServiceClient().getConnectTimeout());
        requestFactory.setReadTimeout(bookingProperties.getServiceClient().getReadTimeout());
        return RestClient.builder()
                .baseUrl(bookingProperties.getIdentity().getUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
