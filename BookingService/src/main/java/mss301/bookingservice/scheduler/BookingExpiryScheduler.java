package mss301.bookingservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.service.BookingExpiryService;
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

    BookingExpiryService bookingExpiryService;

    @Scheduled(fixedDelayString = "${booking.schedule.expiry-scan-interval:5m}",
            initialDelayString = "${booking.schedule.expiry-scan-interval:5m}")
    public void expireOverdueBookings() {
        List<UUID> bookingIds = bookingExpiryService.findExpirableIds();
        int expired = 0;
        for (UUID bookingId : bookingIds) {
            try {
                bookingExpiryService.expire(bookingId);
                expired++;
            } catch (RuntimeException e) {
                log.error("Failed to expire booking {} ({})", bookingId, e.getClass().getSimpleName(), e);
            }
        }
        if (!bookingIds.isEmpty()) {
            log.info("Expiry scan processed {} overdue bookings ({} ok)", bookingIds.size(), expired);
        }
    }
}
