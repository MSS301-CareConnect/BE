package mss301.bookingservice.enums;

import java.util.EnumSet;
import java.util.Set;

// Trạng thái một buổi chăm sóc — đồng bộ CHECK booking_occurrences.status.
// SCHEDULED → ACKNOWLEDGED (caregiver nhận ca, OPS-01) → IN_PROGRESS (check-in, OPS-02) → COMPLETED (check-out, OPS-04);
// buổi chưa bắt đầu → CANCELLED | MISSED | CUSTOMER_NO_SHOW (OPS-06). Đổi caregiver đưa ACKNOWLEDGED về SCHEDULED.
public enum OccurrenceStatus {
    SCHEDULED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    MISSED,
    CUSTOMER_NO_SHOW;

    // Buổi còn chiếm giờ của caregiver / người bệnh (kiểm trùng lịch, quyền xem hồ sơ người bệnh).
    public static final Set<OccurrenceStatus> ACTIVE = EnumSet.of(SCHEDULED, ACKNOWLEDGED, IN_PROGRESS);

    // Buổi chưa bắt đầu: còn đổi caregiver, hủy, check-in được.
    public static final Set<OccurrenceStatus> NOT_STARTED = EnumSet.of(SCHEDULED, ACKNOWLEDGED);

    public boolean canTransitionTo(OccurrenceStatus targetStatus) {
        return switch (this) {
            case SCHEDULED -> targetStatus == ACKNOWLEDGED || targetStatus == IN_PROGRESS || targetStatus == CANCELLED
                    || targetStatus == MISSED || targetStatus == CUSTOMER_NO_SHOW;
            case ACKNOWLEDGED -> targetStatus == SCHEDULED || targetStatus == IN_PROGRESS || targetStatus == CANCELLED
                    || targetStatus == MISSED || targetStatus == CUSTOMER_NO_SHOW;
            case IN_PROGRESS -> targetStatus == COMPLETED;
            case COMPLETED, CANCELLED, MISSED, CUSTOMER_NO_SHOW -> false;
        };
    }
}
