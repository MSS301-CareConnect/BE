package mss301.apigateway.config;

/**
 * Đường dẫn public của gateway — một nguồn duy nhất cho route (RouteConfig) và bảo mật (SecurityConfig).
 * Gateway cắt prefix /api/v1 rồi chuyển tiếp nguyên phần còn lại cho service.
 */
public final class GatewayPaths {
    public static final String API_PREFIX = "/api/v1";
    public static final int API_PREFIX_SEGMENTS = 2;

    public static final String[] IDENTITY_ROUTES = {
            "/api/v1/auth/**", "/api/v1/users/**", "/api/v1/roles/**", "/api/v1/permissions/**"
    };

    public static final String[] CARE_RECIPIENT_ROUTES = {
            "/api/v1/care-recipients/**", "/api/v1/admin/care-recipients/**"
    };

    // Luồng tự xử lý token của IdentityService (đăng nhập/refresh/đăng xuất/đăng ký/quên mật khẩu) — gateway không đọc
    // token ở đây để cookie cũ/hỏng không chặn được login/refresh/logout (giống SecurityConfig của Identity).
    public static final String[] IDENTITY_TOKEN_FLOW_ENDPOINTS = {
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/csrf",
            "/api/v1/auth/mobile/login", "/api/v1/auth/mobile/refresh", "/api/v1/auth/mobile/logout",
            "/api/v1/users/register", "/api/v1/users/register/verify", "/api/v1/users/register/resend-otp",
            "/api/v1/users/forgot-password", "/api/v1/users/reset-password"
    };

    // Swagger tổng hợp + health của chính gateway.
    public static final String IDENTITY_API_DOCS = "/api-docs/identity";
    public static final String CARE_RECIPIENT_API_DOCS = "/api-docs/care-recipient";
    public static final String[] DOCUMENTATION_ENDPOINTS = {
            "/api-docs/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health/**"
    };

    private GatewayPaths() {
    }
}
