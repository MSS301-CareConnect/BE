package mss301.paymentservice.enums;

// Loại lệnh gửi sang service khác qua payment_outbox — đồng bộ CHECK payment_outbox.event_type.
// DEPOSIT_CONFIRMATION → /deposit-paid, REMAINING_CONFIRMATION → /remaining-paid, PAYMENT_OVERDUE → /payment-overdue.
public enum OutboxEventType {
    DEPOSIT_CONFIRMATION,
    REMAINING_CONFIRMATION,
    PAYMENT_OVERDUE
}
