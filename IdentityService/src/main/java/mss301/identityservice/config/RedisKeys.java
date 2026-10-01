package mss301.identityservice.config;

/**
 * Nguồn duy nhất cho key / field Redis của IdentityService. Prefix theo service để không đụng dữ liệu
 * của service hoặc dự án khác dùng chung Redis.
 */
public final class RedisKeys {
    public static final String PREFIX = "careconnect:identity:";

    public static final String REFRESH = PREFIX + "rt:";              // rt:{jti} → userId
    public static final String BLACKLIST = PREFIX + "blacklist:";     // blacklist:{jti} → "1", TTL = thời gian sống còn lại
    public static final String OTP = PREFIX + "otp:";                 // otp:{purpose}:{email} → hash dữ liệu OTP
    public static final String COOLDOWN_SUFFIX = ":cooldown";         // otp:{purpose}:{email}:cooldown → chặn gửi lại

    // Field trong hash OTP
    public static final String OTP_HASH_FIELD = "otpHash";
    public static final String OTP_ATTEMPTS_FIELD = "attempts";

    private RedisKeys() {
    }
}
