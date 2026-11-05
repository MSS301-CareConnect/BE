package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceResponse {
    UUID id;

    Integer occurrenceNo;

    Instant startAt;

    Instant endAt;

    Integer durationMinutes;

    // Dịch vụ của buổi (NULL = mọi dịch vụ của booking).
    UUID careServiceId;

    // Địa chỉ riêng của buổi; NULL = địa chỉ của booking.
    String serviceAddress;

    BigDecimal priceAmount;

    Instant actualStartAt;

    Instant actualEndAt;

    UUID caregiverId;

    // SHIFT: lịch caregiver mà buổi giữ.
    UUID caregiverScheduleId;

    OccurrenceStatus status;

    String cancelReason;

    Instant cancelledAt;

    CancelledBy cancelledByParty;

    // Caregiver của buổi đã bị khóa — manager cần đổi người (BK-04).
    Boolean needsReassignment;

    String note;
}
