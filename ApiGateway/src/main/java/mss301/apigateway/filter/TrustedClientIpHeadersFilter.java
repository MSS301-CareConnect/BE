package mss301.apigateway.filter;

import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter.RequestHttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

@Component
public class TrustedClientIpHeadersFilter implements RequestHttpHeadersFilter, Ordered {
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public HttpHeaders apply(HttpHeaders headers, ServerRequest request) {
        // Gateway bỏ XFF mặc định; gắn lại từ peer thật sau các bộ lọc của framework.
        HttpHeaders forwardedHeaders = new HttpHeaders();
        forwardedHeaders.addAll(headers);
        forwardedHeaders.remove("Forwarded");
        String remoteAddress = request.servletRequest().getRemoteAddr();
        if (remoteAddress != null && !remoteAddress.isBlank()) {
            forwardedHeaders.set(GatewayRequestHeaders.FORWARDED_FOR_HEADER, remoteAddress);
        }
        return forwardedHeaders;
    }
}
