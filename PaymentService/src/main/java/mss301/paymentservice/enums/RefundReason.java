package mss301.paymentservice.enums;

// Lý do hoàn tiền — bản sao enum RefundReason của BookingService (hợp đồng C2) + DUPLICATE (trả trùng, Đợt 1b) +
// PAYMENT_OVERDUE (quá hạn trả phần còn lại: hoàn phần cọc không bị giữ, M3) — hai lý do này chỉ Payment tạo.
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
    DUPLICATE,
    PAYMENT_OVERDUE
}
