package mss301.providerservice.config;

import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;

/**
 * Circuit breaker cho Feign → IdentityService / CareCatalogService. Cấu hình bằng Java (không qua resilience4j.* yaml) để không phụ thuộc
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
    // Phản hồi nghiệp vụ hợp lệ của service được gọi — không phải dấu hiệu service đó hỏng.
    private static final Set<ErrorCode> BUSINESS_ERRORS = Set.of(
            ErrorCode.IDENTITY_USER_NOT_FOUND, ErrorCode.CAREGIVER_SERVICE_NOT_ACTIVE);

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
                && BUSINESS_ERRORS.contains(appException.getErrorCode());
    }
}
