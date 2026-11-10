package mss301.paymentservice.enums;

// Trạng thái tiền của booking phía Payment — đồng bộ CHECK booking_financials.status.
public enum BookingFinancialStatus {
    AWAITING_DEPOSIT,
    DEPOSIT_PAID,
    CLOSED
}
