package mss301.paymentservice.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.security.TokenClaims;
import org.springframework.http.HttpHeaders;

// Gắn token SERVICE của PaymentService (không phải token người dùng) — chỉ khai báo trong *ClientConfig cần quyền service.
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServiceTokenInterceptor implements RequestInterceptor {
    ServiceTokenProvider serviceTokenProvider;

    @Override
    public void apply(RequestTemplate template) {
        template.removeHeader(HttpHeaders.AUTHORIZATION);
        template.header(HttpHeaders.AUTHORIZATION, TokenClaims.BEARER_PREFIX + serviceTokenProvider.getToken());
    }
}
