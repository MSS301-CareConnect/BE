package mss301.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
public class AppConfig {
    @Bean Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient serviceTokenRestClient(PaymentProperties properties) {
        return RestClient.builder().baseUrl(properties.getIdentityUrl()).build();
    }
}
