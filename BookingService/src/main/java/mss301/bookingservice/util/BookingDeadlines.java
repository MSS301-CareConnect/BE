package mss301.bookingservice.util;

import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.BookingDeadlineType;

import java.time.Instant;

public final class BookingDeadlines {

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private BookingDeadlines() {
    }

    // Loại hạn chót đang áp dụng theo trạng thái booking.
    public static BookingDeadlineType typeOf(Booking booking) {
        return switch (booking.getStatus()) {
            case PENDING_CENTER_CONFIRMATION -> BookingDeadlineType.CENTER_CONFIRMATION;
            case PENDING_CAREGIVER_CONFIRMATION -> BookingDeadlineType.CAREGIVER_CONFIRMATION;
            case PENDING_CAREGIVER_ASSIGNMENT -> BookingDeadlineType.CAREGIVER_ASSIGNMENT;
            case AWAITING_INITIAL_PAYMENT -> BookingDeadlineType.INITIAL_PAYMENT;
            case CONFIRMED -> booking.getFullyPaidAt() == null && booking.getRemainingPaymentDueAt() != null
                    ? BookingDeadlineType.REMAINING_PAYMENT
                    : null;
            default -> null;
        };
    }

    // Thời điểm hạn chót hiện tại của booking.
    public static Instant dueAt(Booking booking) {
        BookingDeadlineType type = typeOf(booking);
        if (type == null) {
            return null;
        }
        return switch (type) {
            case CENTER_CONFIRMATION, CAREGIVER_CONFIRMATION -> booking.getAcceptanceDueAt();
            case CAREGIVER_ASSIGNMENT -> booking.getAssignmentDueAt();
            case INITIAL_PAYMENT -> booking.getDepositDueAt();
            case REMAINING_PAYMENT -> booking.getRemainingPaymentDueAt();
        };
    }
}
