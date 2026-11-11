package mss301.bookingservice.service;

import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface BookingNotificationService {
    // Gửi thông báo theo hành động trên booking.
    void notifyLifecycle(Booking booking, List<BookingOccurrence> occurrences, BookingAction action);
    // Gửi thông báo về một buổi chăm sóc.
    void notifyOccurrence(BookingOccurrence occurrence, NotificationType type);

    // M-10: một thông báo gộp khi nhiều buổi của cùng booking bị hủy cùng lúc.
    void notifyOccurrencesCancelled(Booking booking, List<BookingOccurrence> occurrences);
    // Gửi thông báo cho manager của trung tâm.
    void notifyCenter(UUID centerId, NotificationType type, String refType, UUID refId);
    // Như notifyCenter, kèm params (vd "body") cho nội dung thông báo.
    void notifyCenter(UUID centerId, NotificationType type, Map<String, Object> params, String refType, UUID refId);
    // Nhắc trung tâm booking sắp quá hạn xác nhận / phân người chăm sóc.
    void notifyCenterActionDue(Booking booking);

    // Nhắc khách trả phần còn lại trước hạn (spec §9.1, M6).
    void notifyRemainingDue(Booking booking);

    // CR-3: báo caregiver khách đã chọn rằng có đơn chờ họ nhận / từ chối.
    void notifyCaregiverRequest(Booking booking);

    // CR-2: địa chỉ chăm sóc đổi — báo khách, trung tâm và các caregiver đang được giao buổi chưa bắt đầu.
    void notifyAddressChanged(Booking booking, List<UUID> caregiverUserIds);

    // H-08 / H-09: báo các admin cấu hình ở booking.notification.admin-user-ids (không gọi mạng).
    void notifyAdmins(NotificationType type, String refType, UUID refId);
}
