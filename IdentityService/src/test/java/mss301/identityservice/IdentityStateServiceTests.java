package mss301.identityservice;

import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.service.Impl.IdentityStateServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;

class IdentityStateServiceTests {
    @Test void redisFailureNeverBypassesRevocation() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("test outage"));
        var service = new IdentityStateServiceImpl(redis, new IdentityProperties());
        assertThatThrownBy(() -> service.isBlacklisted("test-jti")).isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE);
    }
}
