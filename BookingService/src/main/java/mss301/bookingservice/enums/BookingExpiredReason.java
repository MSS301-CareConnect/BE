package mss301.bookingservice.enums;

// Vì sao booking EXPIRED — suy từ trạng thái lúc hết hạn để khách / trung tâm thấy đúng bên chưa làm kịp.
public enum BookingExpiredReason {
    CENTER_CONFIRMATION_TIMEOUT,
    CAREGIVER_CONFIRMATION_TIMEOUT,
    ASSIGNMENT_TIMEOUT,
    INITIAL_PAYMENT_TIMEOUT,
    PAYMENT_OVERDUE;

    public static BookingExpiredReason from(BookingStatus status) {
        return switch (status) {
            case PENDING_CENTER_CONFIRMATION -> CENTER_CONFIRMATION_TIMEOUT;
            case PENDING_CAREGIVER_CONFIRMATION -> CAREGIVER_CONFIRMATION_TIMEOUT;
            case PENDING_CAREGIVER_ASSIGNMENT -> ASSIGNMENT_TIMEOUT;
            case AWAITING_INITIAL_PAYMENT -> INITIAL_PAYMENT_TIMEOUT;
            default -> PAYMENT_OVERDUE;
        };
    }
}
