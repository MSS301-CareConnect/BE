package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.config.RedisKeys;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.service.IdentityStateService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class IdentityStateServiceImpl implements IdentityStateService {

    static String BLACKLISTED_VALUE = "1";

    // Các thao tác check-then-act chạy bằng Lua để nguyên tử trên Redis (chống replay / dùng lại OTP).
    static RedisScript<Long> CONSUME_REFRESH_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    static RedisScript<Long> BEGIN_OTP_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end "
                    + "redis.call('DEL', KEYS[1]); "
                    + "for i=3,#ARGV,2 do redis.call('HSET',KEYS[1],ARGV[i],ARGV[i+1]) end "
                    + "redis.call('EXPIRE',KEYS[1],ARGV[1]); redis.call('SET',KEYS[2],'1','EX',ARGV[2]); return 1",
            Long.class);

    static RedisScript<Long> CONSUME_OTP_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET',KEYS[1],'" + RedisKeys.OTP_HASH_FIELD + "') == ARGV[1] "
                    + "then return redis.call('DEL',KEYS[1]) else return 0 end",
            Long.class);

    static RedisScript<Long> FAIL_OTP_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET',KEYS[1],'" + RedisKeys.OTP_HASH_FIELD + "') ~= ARGV[2] then return -1 end "
                    + "local n=redis.call('HINCRBY',KEYS[1],'" + RedisKeys.OTP_ATTEMPTS_FIELD + "',1); "
                    + "if n >= tonumber(ARGV[1]) then redis.call('HDEL',KEYS[1],'" + RedisKeys.OTP_HASH_FIELD + "') end; "
                    + "return n",
            Long.class);

    StringRedisTemplate stringRedisTemplate;
    IdentityProperties identityProperties;

    @Override
    public void storeRefresh(String jti, UUID userId, Duration ttl) {
        execute(() -> {
            stringRedisTemplate.opsForValue().set(RedisKeys.REFRESH + jti, userId.toString(), ttl);
            return null;
        });
    }

    @Override
    public boolean consumeRefresh(String jti, UUID userId) {
        Long result = execute(() -> stringRedisTemplate.execute(CONSUME_REFRESH_SCRIPT,
                List.of(RedisKeys.REFRESH + jti), userId.toString()));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        // Token đã hết hạn thì không cần blacklist.
        if (ttl.isNegative() || ttl.isZero()) {
            return;
        }
        execute(() -> {
            stringRedisTemplate.opsForValue().set(RedisKeys.BLACKLIST + jti, BLACKLISTED_VALUE, ttl);
            return null;
        });
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(execute(() -> stringRedisTemplate.hasKey(RedisKeys.BLACKLIST + jti)));
    }

    @Override
    public boolean beginOtp(String purpose, String email, Map<String, String> data) {
        List<String> args = new ArrayList<>();
        args.add(Long.toString(identityProperties.getOtpTtl().toSeconds()));
        args.add(Long.toString(identityProperties.getOtpCooldown().toSeconds()));
        data.forEach((key, value) -> {
            args.add(key);
            args.add(value);
        });
        String otpKey = otpKey(purpose, email);
        Long result = execute(() -> stringRedisTemplate.execute(BEGIN_OTP_SCRIPT,
                List.of(otpKey, otpKey + RedisKeys.COOLDOWN_SUFFIX), args.toArray()));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public Map<String, String> getOtp(String purpose, String email) {
        Map<Object, Object> values = execute(() -> stringRedisTemplate.opsForHash().entries(otpKey(purpose, email)));
        Map<String, String> result = new HashMap<>();
        values.forEach((key, value) -> result.put(key.toString(), value.toString()));
        return result;
    }

    @Override
    public boolean consumeOtp(String purpose, String email, String expectedHash) {
        Long result = execute(() -> stringRedisTemplate.execute(CONSUME_OTP_SCRIPT,
                List.of(otpKey(purpose, email)), expectedHash));
        return Long.valueOf(1).equals(result);
    }

    @Override
    public int failOtp(String purpose, String email, String expectedHash) {
        Long result = execute(() -> stringRedisTemplate.execute(FAIL_OTP_SCRIPT,
                List.of(otpKey(purpose, email)), Integer.toString(identityProperties.getOtpMaxAttempts()), expectedHash));
        return result == null ? -1 : result.intValue();
    }

    @Override
    public void clearOtp(String purpose, String email) {
        String otpKey = otpKey(purpose, email);
        execute(() -> stringRedisTemplate.delete(List.of(otpKey, otpKey + RedisKeys.COOLDOWN_SUFFIX)));
    }

    private String otpKey(String purpose, String email) {
        return RedisKeys.OTP + purpose + ":" + email;
    }

    // Lỗi Redis không bao giờ được hiểu là "không bị thu hồi" — luôn từ chối với 503.
    private <T> T execute(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException e) {
            log.error("Identity Redis operation failed", e);
            throw new AppException(ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE);
        }
    }
}
