package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CaregiverWithdrawalReason;

import java.util.List;
import java.util.UUID;

// Caregiver xin hủy một hoặc nhiều buổi của CÙNG một booking (hủy từng buổi / các buổi còn lại) — 2026-11-09.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverWithdrawalRequest {
    @NotEmpty(message = "OCCURRENCE_ID_REQUIRED")
    @Size(max = 60, message = "TOO_MANY_OCCURRENCES")
    List<@NotNull(message = "OCCURRENCE_ID_REQUIRED") UUID> occurrenceIds;

    @NotNull(message = "WITHDRAWAL_REASON_REQUIRED")
    CaregiverWithdrawalReason reasonCode;

    // Bắt buộc khi reasonCode = OTHER (kiểm ở service → WITHDRAWAL_REASON_DETAIL_REQUIRED).
    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String reasonDetail;
}
