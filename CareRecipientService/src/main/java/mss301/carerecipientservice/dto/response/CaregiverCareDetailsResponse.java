package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

// Phần hồ sơ chỉ caregiver đang được phân công nhận thêm (GET /care-recipients/{id}, 2026-11-05) — không nằm trong
// snapshot nội bộ mà BookingService lưu vào booking.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverCareDetailsResponse {
    String medicalHistory;

    String sleepHabits;

    BigDecimal heightCm;

    BigDecimal weightKg;
}
