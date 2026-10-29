package mss301.paymentservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.service.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// PAY-07: phiên PENDING quá hạn (+ thời gian chờ IPN trễ) → hỏi lại VNPay (querydr, nếu bật) rồi SUCCESS hoặc EXPIRED.
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentExpiryScheduler {
    PaymentService paymentService;

    @Scheduled(fixedDelayString = "${payment.expiry.scan-interval:5m}",
            initialDelayString = "${payment.expiry.scan-interval:5m}")
    public void reconcileStaleAttempts() {
        List<UUID> attemptIds = paymentService.findStaleAttemptIds();
        for (UUID attemptId : attemptIds) {
            try {
                paymentService.reconcileAttempt(attemptId);
            } catch (RuntimeException e) {
                log.error("Reconcile crashed for attempt {} ({})", attemptId, e.getClass().getSimpleName(), e);
            }
        }
    }
}
