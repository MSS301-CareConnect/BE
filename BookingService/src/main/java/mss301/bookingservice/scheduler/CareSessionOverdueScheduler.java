package mss301.bookingservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.service.CareSessionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Nhắc ca chăm sóc còn mở quá giờ kết thúc buổi + booking.session.overdue-reminder-after (OPS-04) — mỗi session một lần.
 * Không tự đóng ca: manager dùng force-end. Lỗi một session không dừng lô.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareSessionOverdueScheduler {
    // D21: sau khi nhắc, ca vẫn quên check-out quá endAt + 4h thì hệ thống tự đóng (autoClose).

    CareSessionService careSessionService;

    @Scheduled(fixedDelayString = "${booking.session.overdue-scan-interval:5m}",
            initialDelayString = "${booking.session.overdue-scan-interval:5m}")
    public void remindOverdueSessions() {
        List<UUID> sessionIds = careSessionService.findOverdueIds();
        for (UUID sessionId : sessionIds) {
            try {
                careSessionService.remindOverdue(sessionId);
            } catch (RuntimeException e) {
                log.error("Failed to remind overdue care session {} ({})", sessionId, e.getClass().getSimpleName(), e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${booking.session.overdue-scan-interval:5m}",
            initialDelayString = "${booking.session.overdue-scan-interval:5m}")
    public void autoCloseForgottenSessions() {
        for (UUID sessionId : careSessionService.findAutoCloseIds()) {
            try {
                careSessionService.autoClose(sessionId);
            } catch (RuntimeException e) {
                log.error("Auto-close crashed for care session {} ({})", sessionId, e.getClass().getSimpleName(), e);
            }
        }
    }
}
