package mss301.bookingservice.repository.projection;

import java.util.UUID;

// Điểm trung bình và số đánh giá đang hiển thị của một caregiver.
public interface CaregiverRatingView {
    UUID getCaregiverId();
    Double getRatingAverage();
    Long getRatingCount();
}
