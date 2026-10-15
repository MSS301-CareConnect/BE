package mss301.bookingservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;

/**
 * Circuit breaker cho Feign → Identity / CareRecipient / Provider. Cấu hình bằng Java (không qua resilience4j.* yaml)
 * như CareRecipientService. Time limiter tắt: timeout do connect/read timeout của Feign, lời gọi chạy trên thread request.
 */
@Configuration
public class ResilienceConfig {

    private static final int SLIDING_WINDOW_SIZE = 10;
    private static final int MINIMUM_CALLS = 5;
    private static final float FAILURE_RATE_THRESHOLD = 50;
    private static final Duration WAIT_IN_OPEN_STATE = Duration.ofSeconds(10);
    private static final int CALLS_IN_HALF_OPEN_STATE = 2;

    // Phản hồi nghiệp vụ hợp lệ của service được gọi (không tồn tại) — không tính là lỗi để mở mạch.
    private static final Set<ErrorCode> BUSINESS_ERRORS = Set.of(
            ErrorCode.RECIPIENT_NOT_FOUND, ErrorCode.CENTER_NOT_FOUND, ErrorCode.CAREGIVER_NOT_FOUND);

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(SLIDING_WINDOW_SIZE)
                .minimumNumberOfCalls(MINIMUM_CALLS)
                .failureRateThreshold(FAILURE_RATE_THRESHOLD)
                .waitDurationInOpenState(WAIT_IN_OPEN_STATE)
                .permittedNumberOfCallsInHalfOpenState(CALLS_IN_HALF_OPEN_STATE)
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
