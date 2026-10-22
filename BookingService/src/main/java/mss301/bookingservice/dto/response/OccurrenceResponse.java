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

    BigDecimal priceAmount;

    Instant actualStartAt;

    Instant actualEndAt;

    UUID caregiverId;

    OccurrenceStatus status;

    String cancelReason;

    Instant cancelledAt;

    CancelledBy cancelledByParty;

    // Caregiver của buổi đã bị khóa — manager cần đổi người (BK-04).
    Boolean needsReassignment;

    String note;
}
