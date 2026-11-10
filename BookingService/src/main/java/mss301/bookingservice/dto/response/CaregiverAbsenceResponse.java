package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CaregiverAbsenceSource;
import mss301.bookingservice.enums.CaregiverAbsenceStatus;

import java.time.Instant;
import java.util.UUID;

// Một lần caregiver vắng ca kèm buổi liên quan — hàng đợi của trung tâm, danh sách của admin, lịch sử của caregiver.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverAbsenceResponse {
    UUID id;
    UUID bookingId;
    String bookingCode;
    String recipientName;
    UUID occurrenceId;
    Integer occurrenceNo;
    Instant startAt;
    Instant endAt;
    UUID centerId;
    // Caregiver đã vắng (Provider + Identity id).
    UUID caregiverId;
    UUID caregiverUserId;
    CaregiverAbsenceSource source;
    CaregiverAbsenceStatus status;
    String explanation;
    Instant explainedAt;
    String conclusionNote;
    UUID concludedBy;
    Instant concludedAt;
    Instant createdAt;
}
