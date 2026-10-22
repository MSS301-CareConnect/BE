package mss301.bookingservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.service.BookingOutboxService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Gửi lại các dòng booking_outbox chưa gửi được (bên nhận lỗi lúc commit). Lỗi một dòng không dừng lô.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingOutboxScheduler {

    BookingOutboxService bookingOutboxService;

    @Scheduled(fixedDelayString = "${booking.outbox.scan-interval:30s}",
            initialDelayString = "${booking.outbox.scan-interval:30s}")
    public void dispatchDueMessages() {
        List<UUID> outboxIds = bookingOutboxService.findDueIds();
        for (UUID outboxId : outboxIds) {
            try {
                bookingOutboxService.dispatch(outboxId);
            } catch (RuntimeException e) {
                log.error("Outbox dispatch crashed for {} ({})", outboxId, e.getClass().getSimpleName(), e);
            }
        }
    }
}
