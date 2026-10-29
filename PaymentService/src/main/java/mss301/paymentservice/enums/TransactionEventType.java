package mss301.paymentservice.enums;

// Loại dòng nhật ký payment_transactions (append-only) — đồng bộ CHECK payment_transactions.event_type.
public enum TransactionEventType {
    CHECKOUT_CREATED,
    IPN,
    RETURN,
    QUERYDR,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    PAYMENT_EXPIRED,
    BOOKING_CONFIRMED,
    BOOKING_REJECTED,
    REFUND_REQUESTED,
    REFUND_CALL,
    REFUND_SUCCEEDED,
    REFUND_FAILED,
    REFUND_RETRIED,
    INVOICE_CREATED,
    INVOICE_PAID,
    INVOICE_WAIVED,
    INVOICE_REDUCED,
    INVOICE_OVERDUE
}
