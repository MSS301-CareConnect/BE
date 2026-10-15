package mss301.bookingservice.service;

import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.BookingAction;

import java.util.UUID;

/**
 * NƠI DUY NHẤT đổi trạng thái booking: áp {@link BookingAction} (state machine), ghi mốc thời gian, đồng bộ buổi chăm
 * sóc, ghi booking_status_history và tính hoàn tiền. Chỉ gọi trong transaction ghi, với booking ĐÃ khóa dòng.
 */
public interface BookingLifecycleService {
    // Dòng lịch sử đầu tiên (null → PENDING) khi tạo booking.
    void recordCreated(Booking booking, UUID customerId);

    // changedBy null = hệ thống (scheduler) / service nội bộ (Payment).
    void apply(Booking booking, BookingAction action, UUID changedBy, String reason);
}
