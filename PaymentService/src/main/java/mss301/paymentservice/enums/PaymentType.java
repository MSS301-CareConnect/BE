package mss301.paymentservice.enums;

// Loại khoản thu của booking: DEPOSIT = khoản thanh toán đầu (SHIFT 100 %, RECURRING / CUSTOM cọc); REMAINING = phần còn
// lại trước buổi đầu (spec v1 §9.1) hoặc sau khi COMPLETED với booking cũ (PAY-11).
public enum PaymentType {
    DEPOSIT,
    REMAINING
}
