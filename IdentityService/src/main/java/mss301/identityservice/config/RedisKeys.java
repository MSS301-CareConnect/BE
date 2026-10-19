package mss301.identityservice.config;

/**
 * Nguồn duy nhất cho key / field Redis của IdentityService. Prefix theo service để không đụng dữ liệu
 * của service hoặc dự án khác dùng chung Redis.
 */
public final class RedisKeys {
    public static final String PREFIX = "careconnect:identity:";

    public static final String REFRESH = PREFIX + "rt:";              // rt:{sha256(token)} → hash {userId, sessionId, deviceId, userAgent, ip, createdAt}
    public static final String REVOKED_REFRESH = PREFIX + "revoked_rt:"; // revoked_rt:{sha256(token)} → userId (phục vụ reuse detection)
    public static final String SESSION = PREFIX + "session:";         // session:{sessionId} → hash metadata phiên
    public static final String USER_SESSIONS = PREFIX + "user_sessions:"; // user_sessions:{userId} → Set các sessionId
    public static final String RATE_LIMIT_LOGIN = PREFIX + "rate_limit:login:"; // rate_limit:login:{hash(email:ip)} → count
    public static final String BLACKLIST = PREFIX + "blacklist:";     // blacklist:{jti} → "1", TTL = thời gian sống còn lại
    public static final String OTP = PREFIX + "otp:";                 // otp:{purpose}:{email} → hash dữ liệu OTP
    public static final String COOLDOWN_SUFFIX = ":cooldown";         // otp:{purpose}:{email}:cooldown → chặn gửi lại

    // Field trong hash OTP
    public static final String OTP_HASH_FIELD = "otpHash";
    public static final String OTP_ATTEMPTS_FIELD = "attempts";

    // Field trong hash Refresh Token / Session
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_SESSION_ID = "sessionId";
    public static final String FIELD_DEVICE_ID = "deviceId";
    public static final String FIELD_USER_AGENT = "userAgent";
    public static final String FIELD_IP = "ip";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_LAST_ACTIVE_AT = "lastActiveAt";
    public static final String FIELD_REFRESH_TOKEN_HASH = "refreshTokenHash";

    private RedisKeys() {
    }
}
