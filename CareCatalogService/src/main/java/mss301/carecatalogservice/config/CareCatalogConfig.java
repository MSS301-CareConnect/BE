package mss301.carecatalogservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(CareCatalogProperties.class)
public class CareCatalogConfig {

    // Bean Clock hệ thống, cho phép thay khi test.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // RestClient riêng (không Feign) để xin token SERVICE — không đi qua interceptor gắn chính token đó.
    @Bean
    public RestClient serviceTokenRestClient(CareCatalogProperties careCatalogProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(careCatalogProperties.getServiceClient().getConnectTimeout());
        requestFactory.setReadTimeout(careCatalogProperties.getServiceClient().getReadTimeout());
        return RestClient.builder()
                .baseUrl(careCatalogProperties.getIdentity().getUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
