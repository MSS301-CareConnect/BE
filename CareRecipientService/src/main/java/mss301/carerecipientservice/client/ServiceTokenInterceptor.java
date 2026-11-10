package mss301.carerecipientservice.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.config.RequestIdFilter;
import mss301.carerecipientservice.security.TokenClaims;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;

/**
 * Gắn token SERVICE của CareRecipientService (không phải token người dùng) vào lời gọi Feign. Chỉ khai báo trong
 * {@code *ClientConfig} của client gọi endpoint {@code hasRole('SERVICE')}, ví dụ:
 * <pre>
 * &#64;Bean
 * public RequestInterceptor paymentRequestInterceptor(ServiceTokenProvider serviceTokenProvider) {
 *     return new ServiceTokenInterceptor(serviceTokenProvider);
 * }
 * </pre>
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
