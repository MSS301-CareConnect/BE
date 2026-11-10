package mss301.bookingservice.enums;

// Trạng thái một dòng booking_outbox — đồng bộ CHECK ck_booking_outbox_status.
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED,
    // H-08: admin đã xử lý tay việc FAILED ở service bên kia — không gửi nữa.
    SKIPPED
}
