package mss301.paymentservice.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.entity.PlatformPolicy;
import mss301.paymentservice.enums.PolicyScope;
import mss301.paymentservice.repository.PlatformPolicyRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;

/**
 * D17: % cọc thật lấy từ chính sách GLOBAL trong DB. Lúc khởi động chỉ đọc và cảnh báo khi DB thiếu chính sách
 * hoặc lệch PAYMENT_DEPOSIT_PERCENT (thường do .env cũ còn 100) — không ghi DB, không chặn khởi động.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PolicyStartupCheck implements ApplicationRunner {
    PlatformPolicyRepository platformPolicyRepository;
    PaymentProperties paymentProperties;
    Clock clock;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Optional<PlatformPolicy> globalPolicy = platformPolicyRepository
                    .findFirstByScopeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(PolicyScope.GLOBAL, clock.instant());
            if (globalPolicy.isEmpty()) {
                log.warn("No effective GLOBAL payment policy — checkout will return NO_EFFECTIVE_POLICY until seeded");
                return;
            }
            if (globalPolicy.get().getDepositPercent().compareTo(paymentProperties.getPolicy().getDepositPercent()) != 0) {
                log.warn("PAYMENT_DEPOSIT_PERCENT={} differs from GLOBAL policy {} — the database value is used",
                        paymentProperties.getPolicy().getDepositPercent(), globalPolicy.get().getDepositPercent());
            }
        } catch (DataAccessException e) {
            log.warn("Cannot read payment policy at startup ({})", e.getClass().getSimpleName());
        }
    }
}
