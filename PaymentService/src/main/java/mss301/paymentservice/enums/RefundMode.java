package mss301.paymentservice.enums;

// Cách dispatcher thực hiện hoàn tiền (PAYMENT_REFUND_MODE): SIMULATED đánh REFUNDED ngay, VNPAY gọi API refund.
public enum RefundMode {
    SIMULATED,
    VNPAY
}
