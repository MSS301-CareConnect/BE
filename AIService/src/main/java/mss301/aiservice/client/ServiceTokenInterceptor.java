package mss301.aiservice.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.config.RequestIdFilter;
import mss301.aiservice.security.TokenClaims;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServiceTokenInterceptor implements RequestInterceptor {
    ServiceTokenProvider serviceTokenProvider;

    @Override
    public void apply(RequestTemplate template) {
        template.removeHeader(HttpHeaders.AUTHORIZATION);
        template.header(HttpHeaders.AUTHORIZATION, TokenClaims.BEARER_PREFIX + serviceTokenProvider.getToken());

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        if (requestId != null && !requestId.isBlank()) {
            template.header(RequestIdFilter.HEADER, requestId);
        }
    }
}
