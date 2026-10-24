package mss301.apigateway.filter;

import mss301.apigateway.security.TokenClaims;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Before-filter cho mọi route: làm sạch header client gửi rồi gắn header do gateway quản lý.
 * - Xóa mọi X-User-* (client tự đặt) và gắn lại X-User-Id / X-User-Roles từ JWT đã xác minh — CHỈ để log/tracing,
 *   service phía sau vẫn tự xác minh JWT, không phân quyền theo header này.
 * - Xóa X-Internal-Secret và mọi X-Service-* (không cho client giả danh cơ chế service-to-service).
 * - Xóa Origin: gateway đã xử lý CORS; service phía sau không thêm header CORS trùng.
 * - Đặt X-Request-Id = id của gateway (RequestIdFilter).
 * Authorization, Cookie (access_token, refresh_token, XSRF-TOKEN) và X-XSRF-TOKEN được chuyển nguyên.
 */
public final class GatewayRequestHeaders {

    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    public static final String USER_HEADER_PREFIX = "x-user-";
    public static final String SERVICE_HEADER_PREFIX = "x-service-";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private GatewayRequestHeaders() {
    }

    public static Function<ServerRequest, ServerRequest> sanitize() {
        return request -> ServerRequest.from(request)
                .headers(headers -> {
                    removeClientControlledHeaders(headers);
                    addUserHeaders(headers);
                    headers.remove("Forwarded");
                    headers.set(FORWARDED_FOR_HEADER, request.servletRequest().getRemoteAddr());
                    Object requestId = request.servletRequest().getAttribute(RequestIdFilter.ATTRIBUTE);
                    if (requestId != null) {
                        headers.set(RequestIdFilter.HEADER, requestId.toString());
                    }
                })
                .build();
    }

    private static void removeClientControlledHeaders(HttpHeaders headers) {
        List<String> headerNames = new ArrayList<>(headers.headerNames());
        for (String headerName : headerNames) {
            String normalizedName = headerName.toLowerCase(Locale.ROOT);
            if (normalizedName.startsWith(USER_HEADER_PREFIX)
                    || normalizedName.startsWith(SERVICE_HEADER_PREFIX)
                    || normalizedName.equals(INTERNAL_SECRET_HEADER.toLowerCase(Locale.ROOT))
                    || normalizedName.equals(HttpHeaders.ORIGIN.toLowerCase(Locale.ROOT))) {
                headers.remove(headerName);
            }
        }
    }

    private static void addUserHeaders(HttpHeaders headers) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return;
        }
        headers.set(USER_ID_HEADER, jwtAuthentication.getToken().getSubject());
        List<String> roles = jwtAuthentication.getToken().getClaimAsStringList(TokenClaims.ROLES);
        if (roles != null && !roles.isEmpty()) {
            headers.set(USER_ROLES_HEADER, String.join(",", roles));
        }
    }
}
