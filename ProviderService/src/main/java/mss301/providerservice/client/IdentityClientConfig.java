package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.providerservice.config.*;
import mss301.providerservice.security.InternalSecretFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;

public class IdentityClientConfig {
    @Bean
    public RequestInterceptor identityRequestInterceptor(ProviderProperties properties) {
        String secret = properties.getIdentity().getInternalSecret();
        return template -> {
            template.header(InternalSecretFilter.HEADER, secret);
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) template.header(RequestIdFilter.HEADER, requestId);
        };
    }

    @Bean
    public ErrorDecoder identityErrorDecoder() {
        return new IdentityErrorDecoder();
    }
}
