package mss301.paymentservice.enums;

// REFUND_PENDING → REFUNDED | FAILED; FAILED → REFUND_PENDING khi admin thử lại — đồng bộ CHECK refunds.status.
public enum RefundStatus {
    REFUND_PENDING,
    REFUNDED,
    FAILED
}
