package mss301.paymentservice.enums;

// Bản sao enum BookingStatus của BookingService — chỉ để đọc payment-context / refund-quote.
public enum BookingStatus {
    PENDING,
    AWAITING_DEPOSIT,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
