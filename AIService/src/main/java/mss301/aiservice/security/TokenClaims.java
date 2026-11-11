package mss301.aiservice.security;

public final class TokenClaims {
    public static final String TOKEN_TYPE = "token_type";

    public static final String ROLES = "roles";

    public static final String PERMISSIONS = "permissions";

    public static final String ACCESS_TOKEN_TYPE = "access";

    public static final String API_AUDIENCE = "careconnect-api";

    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    public static final String BEARER_PREFIX = "Bearer ";

    // Lớp chỉ chứa hằng tên claim, không cho khởi tạo.
    private TokenClaims() {
    }
}
