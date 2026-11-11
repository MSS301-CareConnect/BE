package mss301.bookingservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.service.BookingService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingExpiryScheduler {

    BookingService bookingService;

    // Định kỳ chuyển booking quá hạn xử lý sang hết hạn.
    @Scheduled(fixedDelayString = "${booking.schedule.expiry-scan-interval:1m}",
            initialDelayString = "${booking.schedule.expiry-initial-delay:30s}")
    public void expireOverdueBookings() {
        List<UUID> bookingIds = bookingService.findExpirableIds();
        int expired = 0;
        for (UUID bookingId : bookingIds) {
            try {
                bookingService.expire(bookingId);
                expired++;
            } catch (RuntimeException e) {
                log.error("Failed to expire booking {} ({})", bookingId, e.getClass().getSimpleName(), e);
            }
        }
        if (!bookingIds.isEmpty()) {
            log.info("Expiry scan processed {} overdue bookings ({} ok)", bookingIds.size(), expired);
        }
    }

    // Giữ chỗ chờ cọc ngắn (5 phút): quét riêng chu kỳ booking.schedule.hold-scan-interval để slot được nhả ngay sau
    // hạn + grace thay vì chờ lượt quét chung (2026-11-09). expire kiểm lại dưới khóa nên chạy song song lượt chung an toàn.
    // Định kỳ nhả lịch giữ chỗ đã hết hạn (chưa đặt cọc).
    @Scheduled(fixedDelayString = "${booking.schedule.hold-scan-interval:15s}",
            initialDelayString = "${booking.schedule.expiry-initial-delay:30s}")
    public void releaseExpiredHolds() {
        for (UUID bookingId : bookingService.findExpiredHoldIds()) {
            try {
                bookingService.expire(bookingId);
            } catch (RuntimeException e) {
                log.error("Failed to release hold of booking {} ({})", bookingId, e.getClass().getSimpleName(), e);
            }
        }
    }

    // Nhắc trả phần còn lại theo mốc của center policy (mặc định 72h, 48h trước hạn — M6), cùng chu kỳ quét.
    // Định kỳ nhắc khách trả phần còn lại.
    @Scheduled(fixedDelayString = "${booking.schedule.expiry-scan-interval:1m}",
            initialDelayString = "${booking.schedule.expiry-initial-delay:30s}")
    public void remindRemainingPayments() {
        for (UUID bookingId : bookingService.findRemainingReminderIds()) {
            try {
                bookingService.remindRemainingPayment(bookingId);
            } catch (RuntimeException e) {
                log.error("Failed to remind booking {} ({})", bookingId, e.getClass().getSimpleName(), e);
            }
        }
    }

    // Nhắc trung tâm booking sắp quá hạn xác nhận / phân công (booking.schedule.center-reminder-minutes).
    // Định kỳ nhắc trung tâm xử lý booking sắp quá hạn.
    @Scheduled(fixedDelayString = "${booking.schedule.expiry-scan-interval:1m}",
            initialDelayString = "${booking.schedule.expiry-initial-delay:30s}")
    public void remindCenterActions() {
        for (UUID bookingId : bookingService.findCenterReminderIds()) {
            try {
                bookingService.remindCenterAction(bookingId);
            } catch (RuntimeException e) {
                log.error("Failed to remind center of booking {} ({})", bookingId, e.getClass().getSimpleName(), e);
            }
        }
    }
}
