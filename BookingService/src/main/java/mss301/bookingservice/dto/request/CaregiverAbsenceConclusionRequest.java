package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CaregiverAbsenceStatus;

// Center Manager kết luận một lần vắng ca (2026-11-10): EXCUSED (có lý do chính đáng) hoặc CONFIRMED (vi phạm).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverAbsenceConclusionRequest {
    @NotNull(message = "ABSENCE_DECISION_REQUIRED")
    CaregiverAbsenceStatus decision;

    // Bắt buộc khi decision = EXCUSED (kiểm ở service → ABSENCE_CONCLUSION_NOTE_REQUIRED).
    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String note;
}
