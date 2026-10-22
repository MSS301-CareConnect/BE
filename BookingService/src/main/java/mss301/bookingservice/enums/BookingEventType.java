package mss301.bookingservice.enums;

// Loại sự kiện trong booking_events — đồng bộ CHECK ck_booking_events_type. BK-05 bổ sung CAREGIVER_CHANGED và API lịch sử.
public enum BookingEventType {
    OCCURRENCE_ACKNOWLEDGED,
    OCCURRENCE_STATUS_CHANGED,
    CAREGIVER_CHANGED,
    // Caregiver báo khách vắng mặt (OPS-06) — buổi chưa đổi trạng thái tới khi manager quyết định.
    NO_SHOW_REPORTED
}
