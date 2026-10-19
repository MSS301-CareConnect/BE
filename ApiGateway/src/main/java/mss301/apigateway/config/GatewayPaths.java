package mss301.apigateway.config;

/**
 * Đường dẫn public của gateway — một nguồn duy nhất cho route (RouteConfig) và bảo mật (SecurityConfig).
 * Mọi service tự khai báo prefix /api/v1 nên gateway chuyển tiếp nguyên path, không cắt prefix.
 */
public final class GatewayPaths {
    public static final String API_PREFIX = "/api/v1";

    public static final String[] IDENTITY_ROUTES = {
            "/api/v1/auth/**", "/api/v1/users/**", "/api/v1/roles/**", "/api/v1/permissions/**"
    };

    public static final String[] CARE_RECIPIENT_ROUTES = {
            "/api/v1/care-recipients/**"
    };

    public static final String[] BOOKING_ROUTES = {
            "/api/v1/bookings/**", "/api/v1/booking-occurrences/**"
    };

    public static final String[] PROVIDER_ROUTES = {
            "/api/v1/care-centers/**", "/api/v1/caregivers/**", "/api/v1/work-shifts/**",
            "/api/v1/certificates/**", "/api/v1/provider-media/**"
    };

    public static final String[] CARE_CATALOG_ROUTES = {
            "/api/v1/catalog-items/**", "/api/v1/care-services/**", "/api/v1/center-services/**",
            "/api/v1/price-plans/**"
    };

    public static final String[] MEDIA_ROUTES = {
            "/api/v1/media/**"
    };

    // Luồng tự xử lý token của IdentityService (đăng nhập/refresh/đăng xuất/đăng ký/quên mật khẩu) — gateway không đọc
    // token ở đây để cookie cũ/hỏng không chặn được login/refresh/logout (giống SecurityConfig của Identity).
    public static final String[] IDENTITY_TOKEN_FLOW_ENDPOINTS = {
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/csrf",
            "/api/v1/auth/mobile/login", "/api/v1/auth/mobile/refresh", "/api/v1/auth/mobile/logout",
            "/api/v1/auth/register", "/api/v1/auth/register/verify", "/api/v1/auth/register/resend-otp",
            "/api/v1/auth/verify-email", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password"
    };

    // Swagger tổng hợp + health của chính gateway.
    public static final String IDENTITY_API_DOCS = "/api-docs/identity";
    public static final String CARE_RECIPIENT_API_DOCS = "/api-docs/care-recipient";
    public static final String BOOKING_API_DOCS = "/api-docs/booking";
    public static final String PROVIDER_API_DOCS = "/api-docs/provider";
    public static final String CARE_CATALOG_API_DOCS = "/api-docs/care-catalog";
    public static final String MEDIA_API_DOCS = "/api-docs/media";
    public static final String[] DOCUMENTATION_ENDPOINTS = {
            "/api-docs/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health/**"
    };

    private GatewayPaths() {
    }
}
