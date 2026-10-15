package mss301.bookingservice.client;

import feign.RequestInterceptor;
import mss301.bookingservice.config.RequestIdFilter;
import mss301.bookingservice.security.InternalSecretFilter;
import org.slf4j.MDC;

/**
 * Header chung cho mọi lời gọi /internal/** sang service khác: X-Internal-Secret (secret RIÊNG của service được gọi)
 * + X-Request-Id lấy từ MDC (circuit breaker chạy trên chính thread request nên MDC còn requestId).
 */
public final class InternalClientHeaders {

    private InternalClientHeaders() {
    }

    public static RequestInterceptor interceptor(String internalSecret) {
        return template -> {
            template.header(InternalSecretFilter.HEADER, internalSecret);
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                template.header(RequestIdFilter.HEADER, requestId);
            }
        };
    }
}
