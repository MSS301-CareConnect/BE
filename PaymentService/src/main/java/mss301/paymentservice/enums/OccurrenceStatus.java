package mss301.paymentservice.enums;

import java.util.EnumSet;
import java.util.Set;

// Bản sao enum OccurrenceStatus của BookingService (spec booking v1.0 §11.2) — chỉ để đọc payment-context / refund-quote /
// remaining-invoice.
public enum OccurrenceStatus {
    PENDING_ASSIGNMENT,
    RESERVED,
    SCHEDULED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    CUSTOMER_NO_SHOW,
    CAREGIVER_MISSED;

    // Buổi chưa bắt đầu: còn hủy được, được tính cọc. Lúc thanh toán khoản đầu buổi đang RESERVED (giữ lịch).
    public static final Set<OccurrenceStatus> NOT_STARTED = EnumSet.of(PENDING_ASSIGNMENT, RESERVED, SCHEDULED,
            ACKNOWLEDGED);
}
