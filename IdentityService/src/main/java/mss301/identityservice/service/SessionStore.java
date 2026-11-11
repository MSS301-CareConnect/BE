package mss301.identityservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface SessionStore {
    // Tạo phiên đăng nhập.
    void createSession(String sessionId, UUID userId, String deviceId, String userAgent,
                       String ip, String tokenHash, Duration ttl);

    // Đọc dữ liệu phiên.
    Map<String, String> getSession(String sessionId);

    // Cập nhật hoạt động của phiên.
    void updateSessionActivity(String sessionId, String newTokenHash, Duration ttl);

    // Gắn access token hiện hành vào phiên.
    void bindAccessToken(String sessionId, long authzVersion, String jti, Instant expiresAt);

    // Danh sách id phiên của người dùng.
    Set<String> getUserSessionIds(UUID userId);

    // Xóa một phiên.
    void removeSession(UUID userId, String sessionId);

    // Xóa mọi phiên của người dùng.
    void removeAllUserSessions(UUID userId);
}
