package mss301.identityservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface TokenStore {
    // Lưu refresh token kèm thông tin phiên.
    void storeRefreshToken(String tokenHash, UUID userId, String sessionId, String deviceId,
                           String userAgent, String ip, Duration ttl);

    // Đọc dữ liệu refresh token.
    Map<String, String> getRefreshToken(String tokenHash);

    // Xoay vòng refresh token.
    boolean rotateRefreshToken(String tokenHash, String nextTokenHash, long authzVersion,
                               String accessJti, Instant expiresAt, Duration ttl);

    // Tìm chủ của refresh token đã thu hồi.
    UUID findRevokedTokenOwner(String tokenHash);

    // Thu hồi refresh token.
    boolean revokeRefreshToken(String tokenHash, UUID userId);

    // Thu hồi access token tới khi hết hạn.
    void blacklistAccessToken(String jti, Duration ttl);

    // Kiểm tra access token đã bị thu hồi.
    boolean isAccessTokenBlacklisted(String jti);
}
