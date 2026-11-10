package mss301.paymentservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.service.RefundDispatchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Quét refund REFUND_PENDING tới hạn (mặc định 30s). Lỗi một refund không dừng lô.
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RefundDispatchScheduler {
    RefundDispatchService refundDispatchService;

    @Scheduled(fixedDelayString = "${payment.refund.scan-interval:30s}",
            initialDelayString = "${payment.refund.scan-interval:30s}")
    public void dispatchDueRefunds() {
        List<UUID> refundIds = refundDispatchService.findDueIds();
        for (UUID refundId : refundIds) {
            try {
                refundDispatchService.process(refundId);
            } catch (RuntimeException e) {
                log.error("Refund dispatch crashed for {} ({})", refundId, e.getClass().getSimpleName(), e);
            }
        }
    }
}
