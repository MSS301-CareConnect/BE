package mss301.paymentservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.service.PaymentOutboxService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Gửi lại xác nhận cọc sang Booking chưa gửi được (Booking tạm lỗi lúc IPN). Lỗi một dòng không dừng lô.
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentOutboxScheduler {
    PaymentOutboxService paymentOutboxService;

    @Scheduled(fixedDelayString = "${payment.outbox.scan-interval:30s}",
            initialDelayString = "${payment.outbox.scan-interval:30s}")
    public void dispatchDueMessages() {
        List<UUID> outboxIds = paymentOutboxService.findDueIds();
        for (UUID outboxId : outboxIds) {
            try {
                paymentOutboxService.process(outboxId);
            } catch (RuntimeException e) {
                log.error("Outbox dispatch crashed for {} ({})", outboxId, e.getClass().getSimpleName(), e);
            }
        }
    }
}
