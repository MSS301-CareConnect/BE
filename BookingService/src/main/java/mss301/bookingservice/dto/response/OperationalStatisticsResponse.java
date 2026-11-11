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
    ResponsivenessStatisticsResponse responsiveness;
    // 2026-11-10 — biểu đồ dashboard: mọi trạng thái buổi trong kỳ, booking theo loại, số đánh giá theo sao ("1".."5"),
    // xu hướng theo ngày (kỳ ≤ 62 ngày) hoặc tháng. Chỉ có ở số tổng của kỳ, không có ở từng dòng byCenter.
    Map<String, Long> occurrencesByStatus;
    Map<String, Long> bookingsByType;
    Map<String, Long> ratingDistribution;
    String trendGroupBy;
    List<StatisticsTrendPointResponse> trend;
}
