package mss301.paymentservice.enums;

import java.util.EnumSet;
import java.util.Set;

// Bản sao enum OccurrenceStatus của BookingService — chỉ để đọc payment-context / refund-quote / remaining-invoice.
public enum OccurrenceStatus {
    SCHEDULED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    MISSED,
    CUSTOMER_NO_SHOW;

    // Buổi chưa bắt đầu: còn hủy được, được tính cọc.
    public static final Set<OccurrenceStatus> NOT_STARTED = EnumSet.of(SCHEDULED, ACKNOWLEDGED);
}
