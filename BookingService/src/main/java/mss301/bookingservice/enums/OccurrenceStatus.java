package mss301.bookingservice.enums;

import java.util.EnumSet;
import java.util.Set;

// Trạng thái một buổi chăm sóc — đồng bộ CHECK booking_occurrences.status.
// SCHEDULED → IN_PROGRESS → COMPLETED; SCHEDULED → CANCELLED | MISSED. Giai đoạn này chỉ ghi SCHEDULED và CANCELLED.
public enum OccurrenceStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    MISSED;

    // Buổi còn chiếm giờ của caregiver / người bệnh.
    public static final Set<OccurrenceStatus> ACTIVE = EnumSet.of(SCHEDULED, IN_PROGRESS);

    public boolean canTransitionTo(OccurrenceStatus targetStatus) {
        return switch (this) {
            case SCHEDULED -> targetStatus == IN_PROGRESS || targetStatus == CANCELLED || targetStatus == MISSED;
            case IN_PROGRESS -> targetStatus == COMPLETED;
            case COMPLETED, CANCELLED, MISSED -> false;
        };
    }
}
