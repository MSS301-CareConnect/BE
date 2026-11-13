package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Thành tích làm việc của một caregiver từ trước tới nay: số buổi theo kết quả, giờ đã làm, đánh giá, theo dịch vụ.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverWorkStatisticsResponse {
    UUID caregiverId;

    long completedVisits;

    long upcomingVisits;

    long missedVisits;

    long cancelledVisits;

    long customerNoShowVisits;

    long completedMinutes;

    Instant lastCompletedAt;

    BigDecimal ratingAverage;

    long ratingCount;

    List<CaregiverServiceStatisticsResponse> services;
}
