package mss301.bookingservice.repository.projection;

import mss301.bookingservice.enums.OccurrenceStatus;
import java.time.Instant;
import java.util.UUID;

// Số buổi của một caregiver theo trạng thái, kèm tổng phút và buổi bắt đầu gần nhất của nhóm.
public interface CaregiverVisitCountView {
    UUID getCaregiverId();
    OccurrenceStatus getStatus();
    Long getVisits();
    Long getMinutes();
    Instant getLastStartAt();
}
