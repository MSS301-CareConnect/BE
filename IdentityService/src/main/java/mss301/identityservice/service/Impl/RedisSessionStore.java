package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.config.RedisKeys;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.service.SessionStore;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RedisSessionStore implements SessionStore {

    StringRedisTemplate stringRedisTemplate;

    @Override
    public void createSession(String sessionId, UUID userId, String deviceId, String userAgent,
                              String ip, String tokenHash, Duration ttl) {
        execute(() -> {
            String sessionKey = RedisKeys.SESSION + sessionId;
            String userSessionsKey = RedisKeys.USER_SESSIONS + userId;
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

            stringRedisTemplate.opsForHash().putAll(sessionKey, data);
            stringRedisTemplate.expire(sessionKey, ttl);

            stringRedisTemplate.opsForSet().add(userSessionsKey, sessionId);
            stringRedisTemplate.expire(userSessionsKey, ttl);
            return null;
        });
    }

    @Override
    public Map<String, String> getSession(String sessionId) {
        return execute(() -> {
            String sessionKey = RedisKeys.SESSION + sessionId;
            Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(sessionKey);
            if (raw.isEmpty()) {
                return Map.of();
            }
            Map<String, String> result = new HashMap<>();
            raw.forEach((k, v) -> result.put(k.toString(), v.toString()));
            return result;
        });
    }

    @Override
    public void updateSessionActivity(String sessionId, String newTokenHash, Duration ttl) {
        execute(() -> {
            String sessionKey = RedisKeys.SESSION + sessionId;
            Map<String, String> updates = new HashMap<>();
            updates.put(RedisKeys.FIELD_REFRESH_TOKEN_HASH, newTokenHash);
            updates.put(RedisKeys.FIELD_LAST_ACTIVE_AT, Instant.now().toString());

            stringRedisTemplate.opsForHash().putAll(sessionKey, updates);
            stringRedisTemplate.expire(sessionKey, ttl);
            return null;
        });
    }

    @Override
    public Set<String> getUserSessionIds(UUID userId) {
        return execute(() -> {
            String userSessionsKey = RedisKeys.USER_SESSIONS + userId;
            Set<String> members = stringRedisTemplate.opsForSet().members(userSessionsKey);
            return members != null ? members : Collections.emptySet();
        });
    }

    @Override
    public void removeSession(UUID userId, String sessionId) {
        execute(() -> {
            String sessionKey = RedisKeys.SESSION + sessionId;
            String userSessionsKey = RedisKeys.USER_SESSIONS + userId;

            stringRedisTemplate.delete(sessionKey);
            stringRedisTemplate.opsForSet().remove(userSessionsKey, sessionId);
            return null;
        });
    }

    @Override
    public void removeAllUserSessions(UUID userId) {
        execute(() -> {
            String userSessionsKey = RedisKeys.USER_SESSIONS + userId;
            Set<String> sessionIds = stringRedisTemplate.opsForSet().members(userSessionsKey);
            if (sessionIds != null && !sessionIds.isEmpty()) {
                List<String> keysToDelete = new ArrayList<>();
                for (String sid : sessionIds) {
                    keysToDelete.add(RedisKeys.SESSION + sid);
                }
                keysToDelete.add(userSessionsKey);
                stringRedisTemplate.delete(keysToDelete);
            } else {
                stringRedisTemplate.delete(userSessionsKey);
            }
            return null;
        });
    }

    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException e) {
            log.error("Redis session store operation failed", e);
            throw new AppException(ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE);
        }
    }
}
