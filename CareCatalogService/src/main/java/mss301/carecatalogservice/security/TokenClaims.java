package mss301.carecatalogservice.security;

/**
 * Tên claim / giá trị trong JWT do IdentityService phát — bản sao contract (không import class của service khác).
 */
public final class TokenClaims {
    public static final String TOKEN_TYPE = "token_type";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String CENTER_ID = "center_id";

    // Giá trị claim token_type của access token.
    public static final String ACCESS_TOKEN_TYPE = "access";

    // Audience chung của mọi service backend (IdentityService ghi vào mọi access token).
    public static final String API_AUDIENCE = "careconnect-api";

    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    // Tiền tố header Authorization của mobile/Swagger
    public static final String BEARER_PREFIX = "Bearer ";

    private TokenClaims() {
    }
}
