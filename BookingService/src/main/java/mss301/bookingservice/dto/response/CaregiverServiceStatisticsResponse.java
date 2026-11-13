package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;

// Số buổi đã hoàn thành của caregiver cho một dịch vụ; tên lấy từ snapshot dịch vụ trong booking.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverServiceStatisticsResponse {
    UUID careServiceId;

    String serviceName;

    long completedVisits;
}
