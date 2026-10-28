package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.config.RequestIdFilter;
import mss301.providerservice.security.TokenClaims;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;

/**
 * Gắn token SERVICE của ProviderService (không phải token người dùng) vào lời gọi Feign. Chỉ khai báo trong
 * {@code *ClientConfig} của client cần quyền service (vd {@link CareCatalogClientConfig}); client chạy dưới quyền người
 * dùng vẫn dùng {@link FeignAuthForwardingInterceptor}.
 */
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
