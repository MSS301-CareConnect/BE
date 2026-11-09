package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CaregiverWithdrawalReason;
import mss301.bookingservice.enums.CaregiverWithdrawalState;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.time.Instant;
import java.util.UUID;

// Một yêu cầu hủy buổi của caregiver kèm buổi liên quan — hàng đợi "Caregiver xin hủy" của trung tâm, lịch sử của caregiver.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverWithdrawalResponse {
    UUID id;
    UUID bookingId;
    String bookingCode;
    String recipientName;
    UUID occurrenceId;
    Integer occurrenceNo;
    Instant startAt;
    Instant endAt;
    OccurrenceStatus occurrenceStatus;
    // Caregiver đã xin hủy (Provider + Identity id).
    UUID caregiverId;
    UUID caregiverUserId;
    // Caregiver hiện tại của buổi — khác caregiverId khi trung tâm đã phân người khác.
    UUID currentCaregiverId;
    CaregiverWithdrawalReason reasonCode;
    String reasonDetail;
    CaregiverWithdrawalState state;
    Instant createdAt;
}
