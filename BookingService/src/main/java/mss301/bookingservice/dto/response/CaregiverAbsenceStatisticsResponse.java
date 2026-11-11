package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

// Số lần vắng ca theo kết luận trong phạm vi người gọi (trung tâm / một caregiver / toàn hệ thống).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverAbsenceStatisticsResponse {
    long pending;

    long excused;

    long confirmed;

    long total;
}
