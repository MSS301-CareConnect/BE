package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import mss301.bookingservice.enums.ComplaintStatus;
import mss301.bookingservice.enums.ComplaintLevel;
import mss301.bookingservice.enums.ComplaintTargetType;
import mss301.bookingservice.enums.ComplaintCategory;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ComplaintResponse {
    UUID id;
    String complaintCode;
    UUID bookingId;
    UUID occurrenceId;
    UUID customerId;
    UUID centerId;
    UUID caregiverId;
    ComplaintTargetType targetType;
    ComplaintCategory category;
    String title;
    String content;
    ComplaintStatus status;
    ComplaintLevel level;
    String resolution;
    UUID resolvedBy;
    Instant resolvedAt;
    Instant escalatedAt;
    String escalateReason;
    BigDecimal refundAmount;
    Long version;
    Instant createdAt;
    List<ComplaintEventResponse> events;
}
