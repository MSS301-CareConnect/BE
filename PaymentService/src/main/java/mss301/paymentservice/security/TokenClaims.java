package mss301.paymentservice.security;

public final class TokenClaims {
    public static final String TOKEN_TYPE = "token_type";
    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String CENTER_ID = "center_id";
    public static final String API_AUDIENCE = "careconnect-api";
    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";
    public static final String BEARER_PREFIX = "Bearer ";

    private TokenClaims() {
    }
}
