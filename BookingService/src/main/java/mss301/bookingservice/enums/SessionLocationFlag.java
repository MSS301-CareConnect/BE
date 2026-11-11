package mss301.bookingservice.enums;

// Check-in bị gắn cờ vị trí (2026-11-11) — đồng bộ CHECK ck_care_sessions_location_flag. Chỉ để trung tâm xem lại, không
// chặn check-in. NO_LOCATION: caregiver không gửi tọa độ; OUT_OF_RANGE: xa địa chỉ chăm sóc hơn bán kính cho phép.
public enum SessionLocationFlag {
    NO_LOCATION,
    OUT_OF_RANGE
}
