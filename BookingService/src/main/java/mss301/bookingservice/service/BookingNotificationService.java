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
    void notifyCenter(UUID centerId, NotificationType type, String refType, UUID refId);
}
