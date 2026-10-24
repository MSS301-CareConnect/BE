package mss301.bookingservice.repository;

import java.util.UUID;

public interface TopCaregiverView {
    UUID getCaregiverId();
    Long getCompletedSessions();
    Double getAvgRating();
}
