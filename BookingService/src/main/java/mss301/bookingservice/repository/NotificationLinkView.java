package mss301.bookingservice.repository;

import java.util.UUID;

// Đích điều hướng của thông báo: booking (và buổi, nếu có) mà ref của thông báo thuộc về.
public interface NotificationLinkView extends NotificationBookingLinkView {
    UUID getOccurrenceId();
}
