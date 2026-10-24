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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RedisSessionStore implements SessionStore {

    static DefaultRedisScript<Long> UPDATE_SESSION = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4])
            redis.call('PEXPIRE', KEYS[1], ARGV[5])
            local uid = redis.call('HGET', KEYS[1], ARGV[6])
            redis.call('PEXPIRE', ARGV[7] .. uid, ARGV[5])
            return 1
            """, Long.class);
    static DefaultRedisScript<Long> BIND_ACCESS = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2], ARGV[3], ARGV[4], ARGV[5], ARGV[6])
            return 1
            """, Long.class);

    static DefaultRedisScript<Long> REMOVE_ALL = new DefaultRedisScript<>("""
            local sessions = redis.call('SMEMBERS', KEYS[1])
            for _, sid in ipairs(sessions) do
                local sk = ARGV[1] .. sid
                local hash = redis.call('HGET', sk, ARGV[2])
                if hash then redis.call('DEL', ARGV[3] .. hash) end
                redis.call('DEL', sk)
            end
            redis.call('DEL', KEYS[1])
            return #sessions
            """, Long.class);

    StringRedisTemplate stringRedisTemplate;

    @Override
    public void bindAccessToken(String sessionId, long authzVersion, String jti, Instant expiresAt) {
        execute(() -> {
            Long result = stringRedisTemplate.execute(BIND_ACCESS, List.of(RedisKeys.SESSION + sessionId),
                    RedisKeys.FIELD_AUTHZ_VERSION, Long.toString(authzVersion), RedisKeys.FIELD_ACCESS_JTI, jti,
                    RedisKeys.FIELD_ACCESS_EXPIRES_AT, expiresAt.toString());
            if (!Long.valueOf(1).equals(result)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            return null;
        });
    }

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
            Long result = stringRedisTemplate.execute(UPDATE_SESSION, List.of(RedisKeys.SESSION + sessionId),
                    RedisKeys.FIELD_REFRESH_TOKEN_HASH, newTokenHash, RedisKeys.FIELD_LAST_ACTIVE_AT,
                    Instant.now().toString(), Long.toString(ttl.toMillis()), RedisKeys.FIELD_USER_ID,
                    RedisKeys.USER_SESSIONS);
            if (!Long.valueOf(1).equals(result)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
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
            Object hash = stringRedisTemplate.opsForHash().get(sessionKey, RedisKeys.FIELD_REFRESH_TOKEN_HASH);
            if (hash != null) {
                stringRedisTemplate.delete(RedisKeys.REFRESH + hash);
            }
            stringRedisTemplate.delete(sessionKey);
            stringRedisTemplate.opsForSet().remove(RedisKeys.USER_SESSIONS + userId, sessionId);
            return null;
        });
    }

    @Override
    public void removeAllUserSessions(UUID userId) {
        execute(() -> {
            stringRedisTemplate.execute(REMOVE_ALL, List.of(RedisKeys.USER_SESSIONS + userId),
                    RedisKeys.SESSION, RedisKeys.FIELD_REFRESH_TOKEN_HASH, RedisKeys.REFRESH);
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
