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

public final class GatewayRequestHeaders {

    public static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    public static final String USER_HEADER_PREFIX = "x-user-";
    public static final String SERVICE_HEADER_PREFIX = "x-service-";
    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private GatewayRequestHeaders() {
    }

    // Bộ lọc trước route: xóa header client tự đặt và gắn header người dùng từ JWT.
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

    // Xóa các header X-User-* do client gửi để không giả mạo danh tính.
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

    // Gắn X-User-Id / X-User-Roles từ JWT đã xác thực (chỉ để log).
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
