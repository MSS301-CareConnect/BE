package mss301.carerecipientservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.carerecipientservice.config.CareRecipientProperties;
import mss301.carerecipientservice.config.RequestIdFilter;
import mss301.carerecipientservice.security.InternalSecretFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;

/**
 * Cấu hình riêng của {@link IdentityClient}. Cố ý KHÔNG có @Configuration: nếu bị component scan, interceptor gắn
 * secret của Identity sẽ áp cho mọi Feign client sau này.
 */
public class IdentityClientConfig {

    @Bean
    public RequestInterceptor identityRequestInterceptor(CareRecipientProperties careRecipientProperties) {
        String internalSecret = careRecipientProperties.getIdentity().getInternalSecret();
        return template -> {
            template.header(InternalSecretFilter.HEADER, internalSecret);
            // Circuit breaker chạy trên chính thread request (disable-thread-pool) nên MDC còn requestId.
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                template.header(RequestIdFilter.HEADER, requestId);
            }
        };
    }

    @Bean
    public ErrorDecoder identityErrorDecoder() {
        return new IdentityErrorDecoder();
    }
}
