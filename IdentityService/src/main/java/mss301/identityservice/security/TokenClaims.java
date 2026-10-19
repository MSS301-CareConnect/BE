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

    // Audience chung của mọi service backend (CareRecipient, service sau, gateway) — chỉ có trong access token.
    // IdentityService vẫn kiểm audience riêng (identity.audience) nên token phát trước khi thêm giá trị này vẫn hợp lệ ở đây.
    public static final String API_AUDIENCE = "careconnect-api";

    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    // Tiền tố header Authorization của mobile/Swagger
    public static final String BEARER_PREFIX = "Bearer ";

    public static final String SESSION_ID = "sid";

    // Headers
    public static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    public static final String DEVICE_ID_HEADER = "X-Device-Id";
    public static final String DEPRECATION_HEADER = "Deprecation";

    private TokenClaims() {
    }
}
