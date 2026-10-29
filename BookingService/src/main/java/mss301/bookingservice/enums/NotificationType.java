package mss301.bookingservice.enums;

public enum NotificationType {
    BOOKING_CREATED, BOOKING_ACCEPTED, BOOKING_REJECTED, BOOKING_CONFIRMED, BOOKING_CANCELLED,
    OCCURRENCE_CANCELLED, CAREGIVER_ASSIGNED, CAREGIVER_UNASSIGNED, SESSION_STARTED, SESSION_ENDED,
    REPORT_SUBMITTED, OCCURRENCE_MISSED, NO_SHOW_REPORTED, BOOKING_COMPLETED, PAYMENT_SUCCEEDED,
    REFUND_COMPLETED, REMAINING_DUE, REMAINING_OVERDUE, COMPLAINT_UPDATED, REVIEW_CREATED,
    REVIEW_REPLIED, SETTLEMENT_UPDATED, CENTER_ACTIVATED, SHIFT_REMINDER,
    // CareRecipientService báo lời mời cùng quản lý hồ sơ (C-04).
    RECIPIENT_INVITATION,
    // M-10: booking quá hạn duyệt / quá hạn cọc (trước đây báo nhầm là "bị từ chối").
    BOOKING_EXPIRED,
    // H-08: việc gửi sang service khác hết lượt retry — báo admin.
    OUTBOX_FAILED,
    // D21: hệ thống tự chấp nhận báo vắng / tự đóng ca quên check-out.
    NO_SHOW_AUTO_ACCEPTED, SESSION_AUTO_CLOSED
}
