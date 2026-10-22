package mss301.bookingservice.enums;

// BK-04: Provider báo sự kiện làm booking không thực hiện được. CENTER_LOCKED: hủy + hoàn 100%; CAREGIVER_DEACTIVATED:
// không hủy, đánh dấu buổi tương lai cần đổi caregiver.
public enum SystemCancellationReason {
    CENTER_LOCKED,
    CAREGIVER_DEACTIVATED
}
