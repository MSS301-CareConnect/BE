package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Caregiver giải trình một lần vắng ca (2026-11-10) — gửi lại được tới khi trung tâm kết luận.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverAbsenceExplanationRequest {
    @NotBlank(message = "ABSENCE_EXPLANATION_REQUIRED")
    @Size(min = 10, max = 1000, message = "ABSENCE_EXPLANATION_REQUIRED")
    String explanation;
}
