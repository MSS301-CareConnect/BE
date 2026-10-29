package mss301.paymentservice.enums;

// Loại lệnh gửi sang service khác qua payment_outbox — đồng bộ CHECK payment_outbox.event_type.
public enum OutboxEventType {
    DEPOSIT_CONFIRMATION
}
