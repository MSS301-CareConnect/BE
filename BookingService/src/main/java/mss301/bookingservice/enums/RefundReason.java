package mss301.bookingservice.enums;

// Lý do hoàn tiền — bản sao enum dùng chung với PaymentService (WORK_PLAN §7 C2). Payment quyết định số tiền theo lý do.
public enum RefundReason {
    CUSTOMER_CANCEL,
    CENTER_REJECT,
    CENTER_CANCEL,
    CENTER_LOCKED,
    CAREGIVER_MISSED,
    EXPIRED,
    COMPLAINT_ACCEPTED,
    LATE_PAYMENT,
    BOOKING_NOT_CONFIRMABLE
}
