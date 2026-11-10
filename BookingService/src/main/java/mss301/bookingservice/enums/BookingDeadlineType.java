package mss301.bookingservice.enums;

// Hạn đang chạy của booking theo trạng thái hiện tại (util/BookingDeadlines) — danh sách trung tâm sắp theo hạn, đếm ngược.
public enum BookingDeadlineType {
    CENTER_CONFIRMATION,
    CAREGIVER_CONFIRMATION,
    CAREGIVER_ASSIGNMENT,
    INITIAL_PAYMENT,
    REMAINING_PAYMENT
}
