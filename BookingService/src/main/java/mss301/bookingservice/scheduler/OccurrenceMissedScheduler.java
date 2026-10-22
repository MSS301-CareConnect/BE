package mss301.bookingservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.service.BookingOccurrenceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * OPS-06: đánh dấu MISSED buổi không ai check-in sau giờ bắt đầu + booking.session.missed-grace (tự hoàn tiền khách),
 * và nhắc manager báo vắng chưa quyết định. Mỗi buổi một transaction; lỗi một buổi không dừng lô.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OccurrenceMissedScheduler {

    BookingOccurrenceService bookingOccurrenceService;

    @Scheduled(fixedDelayString = "${booking.session.missed-scan-interval:5m}",
            initialDelayString = "${booking.session.missed-scan-interval:5m}")
    public void markMissedOccurrences() {
        List<UUID> occurrenceIds = bookingOccurrenceService.findMissedIds();
        for (UUID occurrenceId : occurrenceIds) {
            try {
                bookingOccurrenceService.markMissed(occurrenceId);
            } catch (RuntimeException e) {
                log.error("Failed to mark occurrence {} as MISSED ({})", occurrenceId, e.getClass().getSimpleName(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${booking.session.missed-scan-interval:5m}",
            initialDelayString = "${booking.session.missed-scan-interval:5m}")
    public void remindUndecidedNoShows() {
        List<UUID> occurrenceIds = bookingOccurrenceService.findUndecidedNoShowIds();
        for (UUID occurrenceId : occurrenceIds) {
            try {
                bookingOccurrenceService.remindNoShowDecision(occurrenceId);
            } catch (RuntimeException e) {
                log.error("Failed to remind no-show decision for occurrence {} ({})", occurrenceId,
                        e.getClass().getSimpleName(), e);
            }
        }
    }
}
