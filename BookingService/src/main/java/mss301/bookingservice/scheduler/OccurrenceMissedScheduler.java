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
 * OPS-06: đánh dấu MISSED buổi không ai check-in sau giờ bắt đầu + check_in_late_minutes của booking (tự hoàn tiền khách,
 * ghi hồ sơ vắng ca), cảnh báo sớm khi tới giờ mà chưa check-in (2026-11-10) và nhắc manager báo vắng chưa quyết định.
 * Mỗi buổi một transaction; lỗi một buổi không dừng lô.
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
