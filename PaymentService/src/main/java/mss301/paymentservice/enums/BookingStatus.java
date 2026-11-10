package mss301.paymentservice.enums;

// Bản sao enum BookingStatus của BookingService (spec booking v1.0 §11.1) — chỉ để đọc payment-context / refund-quote.
public enum BookingStatus {
    PENDING_CENTER_CONFIRMATION,
    PENDING_CAREGIVER_ASSIGNMENT,
    AWAITING_INITIAL_PAYMENT,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
