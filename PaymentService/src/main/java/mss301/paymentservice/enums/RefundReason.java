package mss301.paymentservice.enums;

// Lý do hoàn tiền — bản sao enum RefundReason của BookingService (hợp đồng C2) + DUPLICATE (trả trùng, Đợt 1b).
public enum RefundReason {
    CUSTOMER_CANCEL,
    CENTER_REJECT,
    CENTER_CANCEL,
    CENTER_LOCKED,
    CAREGIVER_MISSED,
    EXPIRED,
    COMPLAINT_ACCEPTED,
    LATE_PAYMENT,
    BOOKING_NOT_CONFIRMABLE,
    DUPLICATE
}
