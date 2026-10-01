package mss301.identityservice.security;

/**
 * Tên claim JWT do IdentityService phát — contract với mọi service khác (Resource Server đọc các claim này).
 */
public final class TokenClaims {
    public static final String TOKEN_TYPE = "token_type";
    public static final String AUTHZ_VERSION = "authz_version";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String CENTER_ID = "center_id";

    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    // Tiền tố header Authorization của mobile/Swagger
    public static final String BEARER_PREFIX = "Bearer ";

    private TokenClaims() {
    }
}
