package mss301.paymentservice.enums;

// Loại lệnh gửi sang service khác qua payment_outbox — đồng bộ CHECK payment_outbox.event_type.
// DEPOSIT_CONFIRMATION → /deposit-paid, REMAINING_CONFIRMATION → /remaining-paid, PAYMENT_OVERDUE → /payment-overdue,
// REFUND_COMPLETED → /notifications (2026-11-10: báo khách một lệnh hủy / khiếu nại đã hoàn xong).
public enum OutboxEventType {
    DEPOSIT_CONFIRMATION,
    REMAINING_CONFIRMATION,
    PAYMENT_OVERDUE,
    REFUND_COMPLETED
}
