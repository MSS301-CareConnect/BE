package mss301.identityservice;

import mss301.identityservice.config.RedisKeys;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.service.IdentityStateService;
import mss301.identityservice.service.RateLimiter;
import mss301.identityservice.service.SessionStore;
import mss301.identityservice.service.TokenStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@TestConfiguration
public class TestIdentityState {

    @Bean
    @Primary
    @ConditionalOnProperty(name = "identity.testing.real-state", havingValue = "false", matchIfMissing = true)
    IdentityStateService memoryState() {
        return new Memory();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "identity.testing.real-state", havingValue = "false", matchIfMissing = true)
    TokenStore memoryTokenStore() {
        return new MemoryTokenStore();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "identity.testing.real-state", havingValue = "false", matchIfMissing = true)
    SessionStore memorySessionStore() {
        return new MemorySessionStore();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "identity.testing.real-state", havingValue = "false", matchIfMissing = true)
    RateLimiter memoryRateLimiter() {
        return new MemoryRateLimiter();
    }

    static class Memory implements IdentityStateService {
        final Map<String, UUID> refresh = new HashMap<>();
        final Map<String, Instant> blacklist = new HashMap<>();
        final Map<String, Map<String, String>> otp = new HashMap<>();
        final Set<String> cooldown = new HashSet<>();

        String key(String purpose, String email) {
            return purpose + ":" + email;
        }

        public synchronized void storeRefresh(String jti, UUID id, Duration ttl) {
            refresh.put(jti, id);
        }

        public synchronized boolean consumeRefresh(String jti, UUID id) {
            return id.equals(refresh.remove(jti));
        }

        public synchronized void blacklist(String jti, Duration ttl) {
            blacklist.put(jti, Instant.now().plus(ttl));
        }

        public synchronized boolean isBlacklisted(String jti) {
            return blacklist.containsKey(jti) && blacklist.get(jti).isAfter(Instant.now());
        }

        public synchronized boolean beginOtp(String purpose, String email, Map<String, String> data) {
            String key = key(purpose, email);
            if (!cooldown.add(key)) return false;
            otp.put(key, new HashMap<>(data));
            return true;
        }

        public synchronized Map<String, String> getOtp(String purpose, String email) {
            return new HashMap<>(otp.getOrDefault(key(purpose, email), Map.of()));
        }

        public synchronized boolean consumeOtp(String purpose, String email, String hash) {
            var data = otp.get(key(purpose, email));
            if (data == null || !hash.equals(data.get(RedisKeys.OTP_HASH_FIELD))) return false;
            otp.remove(key(purpose, email));
            return true;
        }

        public synchronized int failOtp(String purpose, String email, String hash) {
            var data = otp.get(key(purpose, email));
            if (data == null || !hash.equals(data.get(RedisKeys.OTP_HASH_FIELD))) return -1;
            int n = Integer.parseInt(data.get(RedisKeys.OTP_ATTEMPTS_FIELD)) + 1;
            data.put(RedisKeys.OTP_ATTEMPTS_FIELD, Integer.toString(n));
            if (n >= 5) data.remove(RedisKeys.OTP_HASH_FIELD);
            return n;
        }

        public synchronized void clearOtp(String purpose, String email) {
            otp.remove(key(purpose, email));
            cooldown.remove(key(purpose, email));
        }
    }

    static class MemoryTokenStore implements TokenStore {
        final Map<String, Map<String, String>> refreshTokens = new HashMap<>();
        final Map<String, UUID> revokedTokens = new HashMap<>();
        final Map<String, Instant> blacklist = new HashMap<>();

        @Override
        public synchronized void storeRefreshToken(String tokenHash, UUID userId, String sessionId,
                                                   String deviceId, String userAgent, String ip, Duration ttl) {
            Map<String, String> data = new HashMap<>();
            data.put(RedisKeys.FIELD_USER_ID, userId.toString());
            data.put(RedisKeys.FIELD_SESSION_ID, sessionId);
            data.put(RedisKeys.FIELD_DEVICE_ID, deviceId != null ? deviceId : "unknown");
            data.put(RedisKeys.FIELD_USER_AGENT, userAgent != null ? userAgent : "unknown");
            data.put(RedisKeys.FIELD_IP, ip != null ? ip : "unknown");
            data.put(RedisKeys.FIELD_CREATED_AT, Instant.now().toString());
            refreshTokens.put(tokenHash, data);
        }

        @Override
        public synchronized Map<String, String> getRefreshToken(String tokenHash) {
            Map<String, String> data = refreshTokens.get(tokenHash);
            return data != null ? new HashMap<>(data) : Map.of();
        }

        @Override
        public synchronized boolean revokeRefreshToken(String tokenHash, UUID userId) {
            return refreshTokens.remove(tokenHash) != null;
        }

        @Override
        public synchronized void recordRevokedToken(String tokenHash, UUID userId, Duration ttl) {
            revokedTokens.put(tokenHash, userId);
        }

        @Override
        public synchronized boolean isTokenRevoked(String tokenHash) {
            return revokedTokens.containsKey(tokenHash);
        }

        @Override
        public synchronized void blacklistAccessToken(String jti, Duration ttl) {
            blacklist.put(jti, Instant.now().plus(ttl));
        }

        @Override
        public synchronized boolean isAccessTokenBlacklisted(String jti) {
            return blacklist.containsKey(jti) && blacklist.get(jti).isAfter(Instant.now());
        }
    }

    static class MemorySessionStore implements SessionStore {
        final Map<String, Map<String, String>> sessions = new HashMap<>();
        final Map<UUID, Set<String>> userSessions = new HashMap<>();

        @Override
        public synchronized void createSession(String sessionId, UUID userId, String deviceId,
                                               String userAgent, String ip, String tokenHash, Duration ttl) {
            String now = Instant.now().toString();
            Map<String, String> data = new HashMap<>();
            data.put(RedisKeys.FIELD_SESSION_ID, sessionId);
            data.put(RedisKeys.FIELD_USER_ID, userId.toString());
            data.put(RedisKeys.FIELD_DEVICE_ID, deviceId != null ? deviceId : "unknown");
            data.put(RedisKeys.FIELD_USER_AGENT, userAgent != null ? userAgent : "unknown");
            data.put(RedisKeys.FIELD_IP, ip != null ? ip : "unknown");
            data.put(RedisKeys.FIELD_REFRESH_TOKEN_HASH, tokenHash);
            data.put(RedisKeys.FIELD_CREATED_AT, now);
            data.put(RedisKeys.FIELD_LAST_ACTIVE_AT, now);

            sessions.put(sessionId, data);
            userSessions.computeIfAbsent(userId, k -> new HashSet<>()).add(sessionId);
        }

        @Override
        public synchronized Map<String, String> getSession(String sessionId) {
            Map<String, String> data = sessions.get(sessionId);
            return data != null ? new HashMap<>(data) : Map.of();
        }

        @Override
        public synchronized void updateSessionActivity(String sessionId, String newTokenHash, Duration ttl) {
            Map<String, String> data = sessions.get(sessionId);
            if (data != null) {
                data.put(RedisKeys.FIELD_REFRESH_TOKEN_HASH, newTokenHash);
                data.put(RedisKeys.FIELD_LAST_ACTIVE_AT, Instant.now().toString());
            }
        }

        @Override
        public synchronized Set<String> getUserSessionIds(UUID userId) {
            Set<String> sids = userSessions.get(userId);
            return sids != null ? new HashSet<>(sids) : Collections.emptySet();
        }

        @Override
        public synchronized void removeSession(UUID userId, String sessionId) {
            sessions.remove(sessionId);
            Set<String> sids = userSessions.get(userId);
            if (sids != null) {
                sids.remove(sessionId);
            }
        }

        @Override
        public synchronized void removeAllUserSessions(UUID userId) {
            Set<String> sids = userSessions.remove(userId);
            if (sids != null) {
                for (String sid : sids) {
                    sessions.remove(sid);
                }
            }
        }
    }

    static class MemoryRateLimiter implements RateLimiter {
        final Map<String, Integer> attempts = new HashMap<>();

        String key(String email, String ip) {
            return (email != null ? email.trim().toLowerCase() : "") + ":" + (ip != null ? ip.trim() : "unknown");
        }

        @Override
        public synchronized void checkLoginRateLimit(String email, String ip) {
            if (attempts.getOrDefault(key(email, ip), 0) >= 5) {
                throw new AppException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
            }
        }

        @Override
        public synchronized void recordLoginFailure(String email, String ip) {
            String k = key(email, ip);
            attempts.put(k, attempts.getOrDefault(k, 0) + 1);
        }

        @Override
        public synchronized void resetLoginAttempts(String email, String ip) {
            attempts.remove(key(email, ip));
        }
    }
}
