package mss301.identityservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface SessionStore {
    void createSession(String sessionId, UUID userId, String deviceId, String userAgent,
                       String ip, String tokenHash, Duration ttl);

    Map<String, String> getSession(String sessionId);

    void updateSessionActivity(String sessionId, String newTokenHash, Duration ttl);

    void bindAccessToken(String sessionId, long authzVersion, String jti, Instant expiresAt);

    Set<String> getUserSessionIds(UUID userId);

    void removeSession(UUID userId, String sessionId);

    void removeAllUserSessions(UUID userId);
}
