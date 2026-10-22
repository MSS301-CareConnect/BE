package mss301.bookingservice.enums;

// Loại việc cần gửi sang service khác qua booking_outbox — đồng bộ CHECK ck_booking_outbox_event_type.
// REMAINING_INVOICE (OPS-04), REFUND (OPS-06 / BK-03 / BK-04); FB-01 thêm RATING.
public enum OutboxEventType {
    REMAINING_INVOICE,
    REFUND
}
