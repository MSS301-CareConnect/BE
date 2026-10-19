package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class IdentityClientConfig {

    @Bean
    public RequestInterceptor identityRequestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }

    @Bean
    public ErrorDecoder identityErrorDecoder() {
        return new IdentityErrorDecoder();
    }
}
