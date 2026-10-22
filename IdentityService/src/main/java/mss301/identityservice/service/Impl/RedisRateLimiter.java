package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.config.RedisKeys;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.service.RateLimiter;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RedisRateLimiter implements RateLimiter {

    static int MAX_LOGIN_ATTEMPTS = 5;
    static Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);

    StringRedisTemplate stringRedisTemplate;

    @Override
    public void checkLoginRateLimit(String email, String ip) {
        String key = rateLimitKey(email, ip);
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null && Integer.parseInt(value) >= MAX_LOGIN_ATTEMPTS) {
                log.warn("Login rate limit exceeded for email={} from ip={}", email, ip);
                throw new AppException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
            }
        } catch (DataAccessException e) {
            log.error("Redis rate limit check failed", e);
            throw new AppException(ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE);
        }
    }

    @Override
    public void recordLoginFailure(String email, String ip) {
        String key = rateLimitKey(email, ip);
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, RATE_LIMIT_WINDOW);
            }
        } catch (DataAccessException e) {
            log.error("Redis rate limit record failure failed", e);
            throw new AppException(ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE);
        }
    }

    @Override
    public void resetLoginAttempts(String email, String ip) {
        String key = rateLimitKey(email, ip);
        try {
            stringRedisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.error("Redis rate limit reset failed", e);
            throw new AppException(ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE);
        }
    }

    @Override
    public void checkServiceTokenRateLimit(String clientId) {
        String key = buildServiceTokenKey(clientId);
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null && Integer.parseInt(value) >= MAX_LOGIN_ATTEMPTS) {
                log.warn("Service token rate limit exceeded for clientId={}", clientId);
                throw new AppException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED);
            }
        } catch (DataAccessException e) {
            log.error("Redis service token rate limit check failed", e);
            throw new AppException(ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE);
        }
    }

    @Override
    public void recordServiceTokenFailure(String clientId) {
        String key = buildServiceTokenKey(clientId);
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, RATE_LIMIT_WINDOW);
            }
        } catch (DataAccessException e) {
            log.error("Redis service token rate limit record failure failed", e);
            throw new AppException(ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE);
        }
    }

    @Override
    public void resetServiceTokenAttempts(String clientId) {
        try {
            stringRedisTemplate.delete(buildServiceTokenKey(clientId));
        } catch (DataAccessException e) {
            log.error("Redis service token rate limit reset failed", e);
            throw new AppException(ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE);
        }
    }

    private String buildServiceTokenKey(String clientId) {
        return RedisKeys.RATE_LIMIT_SERVICE_TOKEN + sha256Hex(clientId.trim().toLowerCase());
    }

    private String rateLimitKey(String email, String ip) {
        String normalized = (email != null ? email.trim().toLowerCase() : "") + ":" + (ip != null ? ip.trim() : "unknown");
        return RedisKeys.RATE_LIMIT_LOGIN + sha256Hex(normalized);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
