package mss301.aiservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AIProperties.class)
public class AIConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RestClient groqRestClient(AIProperties aiProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(aiProperties.getGroq().getConnectTimeout());
        requestFactory.setReadTimeout(aiProperties.getGroq().getReadTimeout());

        return RestClient.builder()
                .baseUrl(aiProperties.getGroq().getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getGroq().getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public RestClient serviceTokenRestClient(AIProperties aiProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(aiProperties.getServiceClient().getConnectTimeout());

        requestFactory.setReadTimeout(aiProperties.getServiceClient().getReadTimeout());

        return RestClient.builder()
                .baseUrl(aiProperties.getIdentity().getUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
