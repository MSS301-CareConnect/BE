package mss301.carerecipientservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(CareRecipientProperties.class)
public class CareRecipientConfig {

    // Một nguồn "bây giờ" cho hạn cache token SERVICE — test thay bằng Clock cố định.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // Client xin token SERVICE ở IdentityService qua gateway (ServiceTokenProvider) — timeout riêng, không qua Feign.
    @Bean
    public RestClient serviceTokenRestClient(CareRecipientProperties careRecipientProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(careRecipientProperties.getServiceClient().getConnectTimeout());
        requestFactory.setReadTimeout(careRecipientProperties.getServiceClient().getReadTimeout());
        return RestClient.builder()
                .baseUrl(careRecipientProperties.getIdentity().getUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
