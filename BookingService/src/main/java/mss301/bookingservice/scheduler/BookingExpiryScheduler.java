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

/**
 * Quét booking quá hạn duyệt / quá hạn cọc theo lô (booking.schedule.expiry-batch-size) mỗi
 * booking.schedule.expiry-scan-interval. Lỗi một booking chỉ log, không dừng lô.
 * TODO(nhiều instance): dùng FOR UPDATE SKIP LOCKED / leader election khi chạy nhiều bản BookingService.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingExpiryScheduler {

    BookingService bookingService;

    @Scheduled(fixedDelayString = "${booking.schedule.expiry-scan-interval:5m}",
            initialDelayString = "${booking.schedule.expiry-scan-interval:5m}")
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

    // Nhắc trả phần còn lại theo mốc của center policy (mặc định 72h, 48h trước hạn — M6), cùng chu kỳ quét.
    @Scheduled(fixedDelayString = "${booking.schedule.expiry-scan-interval:5m}",
            initialDelayString = "${booking.schedule.expiry-scan-interval:5m}")
    public void remindRemainingPayments() {
        for (UUID bookingId : bookingService.findRemainingReminderIds()) {
            try {
                bookingService.remindRemainingPayment(bookingId);
            } catch (RuntimeException e) {
                log.error("Failed to remind booking {} ({})", bookingId, e.getClass().getSimpleName(), e);
            }
        }
    }
}
