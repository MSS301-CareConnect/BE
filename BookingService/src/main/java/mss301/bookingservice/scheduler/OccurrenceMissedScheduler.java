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

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OccurrenceMissedScheduler {

    BookingOccurrenceService bookingOccurrenceService;

    // Định kỳ đánh dấu buổi bị bỏ lỡ (caregiver không check-in).
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

    // Định kỳ cảnh báo caregiver check-in trễ.
    @Scheduled(fixedDelayString = "${booking.session.late-warning-scan-interval:1m}",
            initialDelayString = "${booking.session.late-warning-scan-interval:1m}")
    public void warnLateCheckIns() {
        List<UUID> occurrenceIds = bookingOccurrenceService.findLateCheckInIds();
        for (UUID occurrenceId : occurrenceIds) {
            try {
                bookingOccurrenceService.warnLateCheckIn(occurrenceId);
            } catch (RuntimeException e) {
                log.error("Failed to warn about the late check-in of occurrence {} ({})", occurrenceId,
                        e.getClass().getSimpleName(), e);
            }
        }
    }

    // Định kỳ nhắc trung tâm kết luận báo vắng mặt.
    @Scheduled(fixedDelayString = "${booking.session.missed-scan-interval:5m}",
            initialDelayString = "${booking.session.missed-scan-interval:5m}")
    public void remindUndecidedNoShows() {
        List<UUID> occurrenceIds = bookingOccurrenceService.findUndecidedNoShowIds();
        for (UUID occurrenceId : occurrenceIds) {
            try {
                bookingOccurrenceService.autoAcceptNoShow(occurrenceId);
            } catch (RuntimeException e) {
                log.error("Failed to remind no-show decision for occurrence {} ({})", occurrenceId,
                        e.getClass().getSimpleName(), e);
            }
        }
    }
}
