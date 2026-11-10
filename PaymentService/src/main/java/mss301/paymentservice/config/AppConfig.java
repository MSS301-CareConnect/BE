package mss301.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    // RestClient riêng (không Feign) để xin token SERVICE — không đi qua interceptor gắn token của chính nó.
    @Bean
    RestClient serviceTokenRestClient(PaymentProperties paymentProperties) {
        return RestClient.builder()
                .baseUrl(paymentProperties.getIdentityUrl())
                .build();
    }

    // API merchant VNPay (refund): timeout từ env — gọi mạng luôn ngoài transaction (rule #24).
    @Bean
    RestClient vnPayApiRestClient(PaymentProperties paymentProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(paymentProperties.getVnpay().getConnectTimeout());
        requestFactory.setReadTimeout(paymentProperties.getVnpay().getReadTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
