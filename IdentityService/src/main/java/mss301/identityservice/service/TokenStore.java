package mss301.identityservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface TokenStore {
    void storeRefreshToken(String tokenHash, UUID userId, String sessionId, String deviceId,
                           String userAgent, String ip, Duration ttl);

    Map<String, String> getRefreshToken(String tokenHash);

    boolean rotateRefreshToken(String tokenHash, String nextTokenHash, long authzVersion,
                               String accessJti, Instant expiresAt, Duration ttl);

    UUID findRevokedTokenOwner(String tokenHash);

    boolean revokeRefreshToken(String tokenHash, UUID userId);

    void recordRevokedToken(String tokenHash, UUID userId, Duration ttl);

    boolean isTokenRevoked(String tokenHash);

    void blacklistAccessToken(String jti, Duration ttl);

    boolean isAccessTokenBlacklisted(String jti);
}
