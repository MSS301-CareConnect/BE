package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.config.RedisKeys;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.service.TokenStore;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RedisTokenStore implements TokenStore {

    static String BLACKLISTED_VALUE = "1";

    StringRedisTemplate stringRedisTemplate;

    @Override
    public void storeRefreshToken(String tokenHash, UUID userId, String sessionId, String deviceId,
                                  String userAgent, String ip, Duration ttl) {
        execute(() -> {
            String key = RedisKeys.REFRESH + tokenHash;
            Map<String, String> data = new HashMap<>();
            data.put(RedisKeys.FIELD_USER_ID, userId.toString());
            data.put(RedisKeys.FIELD_SESSION_ID, sessionId);
            data.put(RedisKeys.FIELD_DEVICE_ID, deviceId != null ? deviceId : "unknown");
            data.put(RedisKeys.FIELD_USER_AGENT, userAgent != null ? userAgent : "unknown");
            data.put(RedisKeys.FIELD_IP, ip != null ? ip : "unknown");
            data.put(RedisKeys.FIELD_CREATED_AT, Instant.now().toString());

            stringRedisTemplate.opsForHash().putAll(key, data);
            stringRedisTemplate.expire(key, ttl);
            return null;
        });
    }

    @Override
    public Map<String, String> getRefreshToken(String tokenHash) {
        return execute(() -> {
            String key = RedisKeys.REFRESH + tokenHash;
            Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(key);
            if (raw.isEmpty()) {
                return Map.of();
            }
            Map<String, String> result = new HashMap<>();
            raw.forEach((k, v) -> result.put(k.toString(), v.toString()));
            return result;
        });
    }

    @Override
    public boolean revokeRefreshToken(String tokenHash, UUID userId) {
        return Boolean.TRUE.equals(execute(() -> {
            String key = RedisKeys.REFRESH + tokenHash;
            return stringRedisTemplate.delete(key);
        }));
    }

    @Override
    public void recordRevokedToken(String tokenHash, UUID userId, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        execute(() -> {
            stringRedisTemplate.opsForValue().set(RedisKeys.REVOKED_REFRESH + tokenHash, userId.toString(), ttl);
            return null;
        });
    }

    @Override
    public boolean isTokenRevoked(String tokenHash) {
        return Boolean.TRUE.equals(execute(() -> stringRedisTemplate.hasKey(RedisKeys.REVOKED_REFRESH + tokenHash)));
    }

    @Override
    public void blacklistAccessToken(String jti, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        execute(() -> {
            stringRedisTemplate.opsForValue().set(RedisKeys.BLACKLIST + jti, BLACKLISTED_VALUE, ttl);
            return null;
        });
    }

    @Override
    public boolean isAccessTokenBlacklisted(String jti) {
        return Boolean.TRUE.equals(execute(() -> stringRedisTemplate.hasKey(RedisKeys.BLACKLIST + jti)));
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException e) {
            log.error("Redis token store operation failed", e);
            throw new AppException(ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE);
        }
    }
}
