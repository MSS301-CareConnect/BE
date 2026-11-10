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
import java.util.List;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RedisTokenStore implements TokenStore {

    static String BLACKLISTED_VALUE = "1";

    // Đổi cả refresh, session và access jti trong cùng lệnh. Request thua không thể hủy phiên khi request thắng đang phát token.
    static DefaultRedisScript<Long> ROTATE_REFRESH = new DefaultRedisScript<>("""
            local data = redis.call('HGETALL', KEYS[1])
            if #data == 0 then return 0 end
            local sid = redis.call('HGET', KEYS[1], ARGV[1])
            local uid = redis.call('HGET', KEYS[1], ARGV[2])
            local sk = ARGV[3] .. sid
            if redis.call('HGET', sk, ARGV[2]) ~= uid
                or redis.call('HGET', sk, ARGV[4]) ~= ARGV[5]
                or redis.call('HGET', sk, ARGV[14]) ~= ARGV[15] then
                redis.call('DEL', KEYS[1])
                return 0
            end
            local ttl = redis.call('PTTL', KEYS[1])
            redis.call('DEL', KEYS[1])
            if ttl > 0 then redis.call('PSETEX', KEYS[2], ttl, uid) end
            redis.call('HSET', KEYS[3], unpack(data))
            redis.call('PEXPIRE', KEYS[3], ARGV[13])
            redis.call('HSET', sk, ARGV[4], ARGV[6], ARGV[7], ARGV[8], ARGV[9], ARGV[10], ARGV[11], ARGV[12])
            redis.call('PEXPIRE', sk, ARGV[13])
            redis.call('PEXPIRE', ARGV[16] .. uid, ARGV[13])
            return 1
            """, Long.class);

    StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean rotateRefreshToken(String tokenHash, String nextTokenHash, long authzVersion,
                                      String accessJti, Instant expiresAt, Duration ttl) {
        return Long.valueOf(1).equals(execute(() -> stringRedisTemplate.execute(ROTATE_REFRESH,
                List.of(RedisKeys.REFRESH + tokenHash, RedisKeys.REVOKED_REFRESH + tokenHash, RedisKeys.REFRESH + nextTokenHash),
                RedisKeys.FIELD_SESSION_ID, RedisKeys.FIELD_USER_ID, RedisKeys.SESSION,
                RedisKeys.FIELD_REFRESH_TOKEN_HASH, tokenHash, nextTokenHash,
                RedisKeys.FIELD_LAST_ACTIVE_AT, Instant.now().toString(), RedisKeys.FIELD_ACCESS_JTI, accessJti,
                RedisKeys.FIELD_ACCESS_EXPIRES_AT, expiresAt.toString(), Long.toString(ttl.toMillis()),
                RedisKeys.FIELD_AUTHZ_VERSION, Long.toString(authzVersion), RedisKeys.USER_SESSIONS)));
    }

    @Override
    public UUID findRevokedTokenOwner(String tokenHash) {
        return execute(() -> {
            String owner = stringRedisTemplate.opsForValue().get(RedisKeys.REVOKED_REFRESH + tokenHash);
            return owner == null ? null : UUID.fromString(owner);
        });
    }

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
