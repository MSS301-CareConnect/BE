package mss301.bookingservice.util;

import mss301.bookingservice.enums.NotificationType;

public final class NotificationTemplates {
    private NotificationTemplates() {
    }

    public static String title(NotificationType type) {
        return switch (type) {
            case BOOKING_CREATED -> "Có yêu cầu đặt lịch mới";
            case BOOKING_ACCEPTED -> "Lịch được duyệt, vui lòng thanh toán cọc";
            case BOOKING_REJECTED -> "Yêu cầu đặt lịch đã bị từ chối hoặc hết hạn";
            case BOOKING_CONFIRMED -> "Lịch chăm sóc đã được xác nhận";
            case BOOKING_CANCELLED -> "Lịch chăm sóc đã bị hủy";
            case OCCURRENCE_CANCELLED -> "Buổi chăm sóc đã bị hủy";
            case CAREGIVER_ASSIGNED -> "Bạn được phân công lịch chăm sóc";
            case CAREGIVER_UNASSIGNED -> "Phân công chăm sóc đã thay đổi";
            case SESSION_STARTED -> "Ca chăm sóc đã bắt đầu";
            case SESSION_ENDED -> "Ca chăm sóc đã kết thúc";
            case REPORT_SUBMITTED -> "Có báo cáo chăm sóc mới";
            case OCCURRENCE_MISSED -> "Người chăm sóc không đến buổi chăm sóc";
            case NO_SHOW_REPORTED -> "Có báo cáo khách vắng mặt";
            case BOOKING_COMPLETED -> "Lịch đã hoàn thành, mời bạn đánh giá";
            case PAYMENT_SUCCEEDED -> "Thanh toán thành công";
            case REFUND_COMPLETED -> "Hoàn tiền thành công";
            case REMAINING_DUE -> "Có khoản cần thanh toán";
            case REMAINING_OVERDUE -> "Khoản thanh toán đã quá hạn";
            case COMPLAINT_UPDATED -> "Khiếu nại vừa được cập nhật";
            case REVIEW_CREATED -> "Có đánh giá mới về người chăm sóc";
            case REVIEW_REPLIED -> "Trung tâm đã phản hồi đánh giá";
            case SETTLEMENT_UPDATED -> "Đối soát vừa được cập nhật";
            case CENTER_ACTIVATED -> "Trung tâm đã được kích hoạt";
            case SHIFT_REMINDER -> "Nhắc lịch chăm sóc trong hai giờ tới";
            case RECIPIENT_INVITATION -> "Lời mời cùng quản lý hồ sơ người thân";
            case BOOKING_EXPIRED -> "Lịch chăm sóc đã hết hạn";
            case OUTBOX_FAILED -> "Có việc đồng bộ giữa các dịch vụ bị lỗi cần xử lý";
            case NO_SHOW_AUTO_ACCEPTED -> "Hệ thống đã xác nhận khách vắng mặt";
            case SESSION_AUTO_CLOSED -> "Hệ thống đã tự đóng ca chăm sóc quên check-out";
            case CAREGIVER_REQUEST -> "Có khách đặt lịch với bạn, vui lòng nhận hoặc từ chối";
            case BOOKING_HANDED_OVER -> "Người chăm sóc không nhận lịch, trung tâm sẽ phân người khác";
            case BOOKING_ADDRESS_CHANGED -> "Địa chỉ chăm sóc của lịch đã thay đổi";
            case CENTER_ACTION_DUE -> "Lịch đặt sắp quá hạn xử lý";
            case CAREGIVER_CHANGE_REQUEST_CREATED -> "Có yêu cầu đổi người chăm sóc từ khách hàng";
            case CAREGIVER_CHANGE_REQUEST_DECIDED -> "Yêu cầu đổi người chăm sóc đã được xử lý";
            case EMERGENCY_REPLACEMENT_NOTIFIED -> "Thông báo thay đổi người chăm sóc khẩn cấp";
            case OCCURRENCE_RESCHEDULED_NOTIFICATION -> "Buổi chăm sóc đã được dời lịch";
            case CHECK_IN_LATE -> "Đã tới giờ nhưng người chăm sóc chưa check-in";
            case CAREGIVER_ABSENCE_UPDATED -> "Hồ sơ vắng ca vừa được cập nhật";
            case WALLET_WITHDRAWAL_COMPLETED -> "Rút tiền thành công";
            case WALLET_WITHDRAWAL_REJECTED -> "Yêu cầu rút tiền bị từ chối";
        };
    }
}
