package mss301.bookingservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.service.BookingService;
import mss301.bookingservice.service.CaregiverAssignmentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tự phân công caregiver (2026-11-09) mỗi booking.schedule.auto-assign-interval: booking đã cọc có buổi chưa có người
 * hoặc chờ thay người (caregiver xin hủy / bị khóa). Sau đó buổi đã cọc vẫn chưa có người khi còn ≤
 * unassigned-cancel-lead tới giờ bắt đầu → hủy + hoàn 100 % (Q-B2). Lỗi một booking chỉ log, không dừng lô; mọi ghi DB
 * khóa / kiểm lại trong transaction nên chạy song song nhiều instance vẫn đúng.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverAutoAssignScheduler {

    CaregiverAssignmentService caregiverAssignmentService;
    BookingService bookingService;

    @Scheduled(fixedDelayString = "${booking.schedule.auto-assign-interval:1m}",
            initialDelayString = "${booking.schedule.expiry-initial-delay:30s}")
    public void assignAndCancelUnassigned() {
        for (UUID bookingId : caregiverAssignmentService.findAutoAssignIds()) {
            try {
                caregiverAssignmentService.autoAssign(bookingId);
            } catch (RuntimeException e) {
                log.error("Failed to auto-assign booking {} ({})", bookingId, e.getClass().getSimpleName(), e);
            }
        }
        for (UUID bookingId : bookingService.findUnassignedDueIds()) {
            try {
                bookingService.cancelUnassigned(bookingId);
            } catch (RuntimeException e) {
                log.error("Failed to cancel unassigned visits of booking {} ({})", bookingId,
                        e.getClass().getSimpleName(), e);
            }
        }
    }
}
