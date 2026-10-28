package mss301.providerservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderConfig {

    // Một nguồn "bây giờ" cho kiểm giờ ca / hạn đăng ký — test thay bằng Clock cố định (WORK_PLAN §3.4).
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    // Client xin token SERVICE ở IdentityService qua gateway (ServiceTokenProvider) — timeout riêng, không qua Feign.
    @Bean
    public RestClient serviceTokenRestClient(ProviderProperties providerProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(providerProperties.getServiceClient().getConnectTimeout());
        requestFactory.setReadTimeout(providerProperties.getServiceClient().getReadTimeout());
        return RestClient.builder()
                .baseUrl(providerProperties.getIdentity().getUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
