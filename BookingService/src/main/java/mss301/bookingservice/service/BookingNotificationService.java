package mss301.bookingservice.service;

import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import java.util.List;
import java.util.UUID;

public interface BookingNotificationService {
    void notifyLifecycle(Booking booking, List<BookingOccurrence> occurrences, BookingAction action);
    void notifyOccurrence(BookingOccurrence occurrence, NotificationType type);

    // M-10: một thông báo gộp khi nhiều buổi của cùng booking bị hủy cùng lúc.
    void notifyOccurrencesCancelled(Booking booking, List<BookingOccurrence> occurrences);
    void notifyCenter(UUID centerId, NotificationType type, String refType, UUID refId);

    // H-08 / H-09: báo các admin cấu hình ở booking.notification.admin-user-ids (không gọi mạng).
    void notifyAdmins(NotificationType type, String refType, UUID refId);
}
