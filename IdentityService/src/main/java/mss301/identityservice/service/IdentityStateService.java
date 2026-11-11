package mss301.identityservice.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

public interface IdentityStateService {
    // Lưu jti của refresh token trên Redis.
    void storeRefresh(String jti, UUID userId, Duration ttl);

    // Claim nguyên tử: true chỉ cho đúng một lần gọi với jti còn hiệu lực của userId.
    boolean consumeRefresh(String jti, UUID userId);

    // Kiểm tra access token đã bị thu hồi chưa.
    boolean isBlacklisted(String jti);

    // false nếu còn cooldown gửi lại.
    boolean beginOtp(String purpose, String email, Map<String, String> data);

    // Đọc trạng thái OTP đang chờ.
    Map<String, String> getOtp(String purpose, String email);

    // Dùng OTP (một lần) nếu đúng mã.
    boolean consumeOtp(String purpose, String email, String expectedHash);

    // Trả số lần sai sau khi tăng; -1 nếu OTP không còn / đã đổi mã.
    int failOtp(String purpose, String email, String expectedHash);

    // Xóa trạng thái OTP và thời gian chờ gửi lại.
    void clearOtp(String purpose, String email);
}
