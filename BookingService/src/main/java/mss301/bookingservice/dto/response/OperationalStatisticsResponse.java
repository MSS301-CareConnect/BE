package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.Map;
import java.util.List;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OperationalStatisticsResponse {
    Map<String, Long> bookingsByStatus;
    OccurrenceStatisticsResponse occurrences;
    double completionRate;
    BigDecimal avgRating;
    long reviewCount;
    ComplaintStatisticsResponse complaints;
    List<TopCaregiverResponse> topCaregivers;
    PageResponse<CenterOperationalStatsResponse> byCenter;
}
