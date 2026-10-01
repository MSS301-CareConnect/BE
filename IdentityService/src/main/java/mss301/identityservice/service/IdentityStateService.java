package mss301.identityservice.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * State xác thực trên Redis (refresh jti, access blacklist, OTP). Lỗi Redis → AppException
 * AUTH_DEPENDENCY_UNAVAILABLE (503) — không bao giờ coi như "không bị thu hồi".
 */
public interface IdentityStateService {
    void storeRefresh(String jti, UUID userId, Duration ttl);

    // Claim nguyên tử: true chỉ cho đúng một lần gọi với jti còn hiệu lực của userId.
    boolean consumeRefresh(String jti, UUID userId);

    void blacklist(String jti, Duration ttl);

    boolean isBlacklisted(String jti);

    // false nếu còn cooldown gửi lại.
    boolean beginOtp(String purpose, String email, Map<String, String> data);

    Map<String, String> getOtp(String purpose, String email);

    boolean consumeOtp(String purpose, String email, String expectedHash);

    // Trả số lần sai sau khi tăng; -1 nếu OTP không còn / đã đổi mã.
    int failOtp(String purpose, String email, String expectedHash);

    void clearOtp(String purpose, String email);
}
