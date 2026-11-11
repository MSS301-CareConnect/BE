package mss301.bookingservice.enums;

// Ca hệ thống tự đóng phải được trung tâm xác nhận trước khi quyết toán (2026-11-11) — đồng bộ CHECK
// ck_care_sessions_completion_review. NULL = ca kết thúc bình thường, không cần xem lại.
public enum SessionCompletionReview {
    PENDING,
    CONFIRMED,
    NOT_DELIVERED
}
