package mss301.paymentservice.enums;

// PENDING → SENT | FAILED — đồng bộ CHECK payment_outbox.status.
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
