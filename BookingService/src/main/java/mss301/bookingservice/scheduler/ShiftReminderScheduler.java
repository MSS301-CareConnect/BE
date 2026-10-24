package mss301.bookingservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.service.BookingOccurrenceService;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ShiftReminderScheduler {
    BookingOccurrenceService bookingOccurrenceService;

    @Scheduled(fixedDelayString = "${BOOKING_SHIFT_REMINDER_INTERVAL:15m}")
    public void remind() {
        for (UUID id : bookingOccurrenceService.findShiftReminderIds()) {
            try {
                bookingOccurrenceService.remindShift(id);
            } catch (RuntimeException exception) {
                log.error("Không nhắc được buổi chăm sóc {}", id, exception);
            }
        }
    }
}
