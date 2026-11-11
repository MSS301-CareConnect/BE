package mss301.bookingservice.repository.projection;

import mss301.bookingservice.repository.projection.NotificationBookingLinkView;
import java.util.UUID;

// Đích điều hướng của thông báo: booking (và buổi, nếu có) mà ref của thông báo thuộc về.
public interface NotificationLinkView extends NotificationBookingLinkView {
    UUID getOccurrenceId();
}
