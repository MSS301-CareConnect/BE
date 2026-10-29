package mss301.paymentservice.enums;

// Loại khoản thu của booking: cọc trước khi xác nhận, phần còn lại sau khi booking COMPLETED (PAY-11).
public enum PaymentType {
    DEPOSIT,
    REMAINING
}
