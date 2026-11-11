package mss301.bookingservice.repository.projection;

import java.util.UUID;

public interface TopCaregiverView {
    UUID getCaregiverId();
    Long getCompletedSessions();
    Double getAvgRating();
}
