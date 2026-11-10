package mss301.bookingservice.enums;

// Loại booking theo spec §4 — đồng bộ CHECK bookings.booking_type.
// SHIFT: một ca caregiver đã công bố (caregiverScheduleId); RECURRING: lặp DAILY / WEEKLY; CUSTOM: danh sách buổi tự chọn.
public enum BookingType {
    SHIFT,
    RECURRING,
    CUSTOM
}
