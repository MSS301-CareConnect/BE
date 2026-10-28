package mss301.paymentservice.security;

public final class TokenClaims {
    public static final String TOKEN_TYPE = "token_type";
    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String API_AUDIENCE = "careconnect-api";

    private TokenClaims() {
    }
}
