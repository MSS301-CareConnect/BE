package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CaregiverChangeReasonCode;
import mss301.bookingservice.enums.CaregiverChangeRequestStatus;
import mss301.bookingservice.enums.CaregiverChangeScopeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverChangeRequestResponse {
    UUID id;
    UUID bookingId;
    UUID customerUserId;
    UUID centerId;
    CaregiverChangeScopeType scopeType;
    LocalDate fromDate;
    CaregiverChangeReasonCode reasonCode;
    String reasonDetail;
    UUID preferredCaregiverId;
    CaregiverChangeRequestStatus status;
    UUID reviewedBy;
    Instant reviewedAt;
    String rejectionReason;
    Instant createdAt;
    Instant updatedAt;
    List<UUID> occurrenceIds;
}
