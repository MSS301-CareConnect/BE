package mss301.bookingservice.enums;

// Loại sự kiện trong booking_events — đồng bộ CHECK ck_booking_events_type. BK-05 bổ sung CAREGIVER_CHANGED và API lịch sử.
public enum BookingEventType {
    OCCURRENCE_ACKNOWLEDGED,
    OCCURRENCE_STATUS_CHANGED,
    CAREGIVER_CHANGED,
    // Caregiver báo khách vắng mặt (OPS-06) — buổi chưa đổi trạng thái tới khi manager quyết định.
    NO_SHOW_REPORTED,
    // CR-2: địa chỉ chăm sóc của booking đổi (old_value / new_value) — áp cho các buổi chưa bắt đầu.
    SERVICE_ADDRESS_CHANGED,
    // Spec v3: Đổi caregiver theo yêu cầu khách, thay thế khẩn cấp, dời lịch buổi lẻ.
    CAREGIVER_CHANGE_REQUESTED,
    CAREGIVER_CHANGE_APPROVED,
    CAREGIVER_CHANGE_REJECTED,
    EMERGENCY_REPLACEMENT_ASSIGNED,
    OCCURRENCE_RESCHEDULED,
    // 2026-11-09: caregiver xin hủy buổi được giao — buổi chờ trung tâm phân người khác.
    CAREGIVER_WITHDRAWN
}
