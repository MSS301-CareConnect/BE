package mss301.bookingservice.repository;

import java.util.UUID;

// Ref của thông báo → booking chứa nó (ref không gắn buổi, vd đánh giá).
public interface NotificationBookingLinkView {
    UUID getRefId();
    UUID getBookingId();
}
