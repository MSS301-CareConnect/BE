package mss301.apigateway.security;

/**
 * Tên claim / giá trị trong JWT do IdentityService phát — bản sao contract (không import class của service khác).
 */
public final class TokenClaims {
    public static final String TOKEN_TYPE = "token_type";
    public static final String ROLES = "roles";

    // Giá trị claim token_type của access token.
    public static final String ACCESS_TOKEN_TYPE = "access";

    // Audience chung của mọi service backend (IdentityService ghi vào mọi access token).
    public static final String API_AUDIENCE = "careconnect-api";

    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    // Cookie access token do IdentityService đặt cho web (HttpOnly, Path=/).
    public static final String ACCESS_COOKIE = "access_token";

    public static final String CSRF_HEADER = "X-XSRF-TOKEN";

    private TokenClaims() {
    }
}
