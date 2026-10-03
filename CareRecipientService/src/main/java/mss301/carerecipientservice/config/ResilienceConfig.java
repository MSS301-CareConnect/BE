package mss301.carerecipientservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breaker cho Feign → IdentityService. Cấu hình bằng Java (không qua resilience4j.* yaml) để không phụ thuộc
 * module resilience4j-spring-boot. Time limiter tắt (spring.cloud.circuitbreaker.resilience4j.disable-time-limiter):
 * timeout do connect/read timeout của Feign đảm nhiệm, lời gọi chạy trên chính thread request.
 */
@Configuration
public class ResilienceConfig {

    private static final int SLIDING_WINDOW_SIZE = 10;
    private static final int MINIMUM_CALLS = 5;
    private static final float FAILURE_RATE_THRESHOLD = 50;
    private static final Duration WAIT_IN_OPEN_STATE = Duration.ofSeconds(10);
    private static final int CALLS_IN_HALF_OPEN_STATE = 2;

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(SLIDING_WINDOW_SIZE)
                .minimumNumberOfCalls(MINIMUM_CALLS)
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .waitDurationInOpenState(WAIT_IN_OPEN_STATE)
                .permittedNumberOfCallsInHalfOpenState(CALLS_IN_HALF_OPEN_STATE)
                // Lỗi nghiệp vụ (user không tồn tại) là phản hồi hợp lệ của Identity — không tính là lỗi để mở mạch.
                .ignoreException(ResilienceConfig::isBusinessError)
                .build();
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(circuitBreakerConfig)
                .timeLimiterConfig(TimeLimiterConfig.ofDefaults())
                .build());
    }

    private static boolean isBusinessError(Throwable throwable) {
        return throwable instanceof AppException appException
                && appException.getErrorCode() == ErrorCode.IDENTITY_USER_NOT_FOUND;
    }
}
