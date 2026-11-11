package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.response.OperationalStatisticsResponse;
import mss301.bookingservice.dto.response.OccurrenceStatisticsResponse;
import mss301.bookingservice.dto.response.ComplaintStatisticsResponse;
import mss301.bookingservice.dto.response.TopCaregiverResponse;
import mss301.bookingservice.dto.response.CenterOperationalStatsResponse;
import mss301.bookingservice.dto.response.ResponsivenessStatisticsResponse;
import mss301.bookingservice.dto.response.StatisticsTrendPointResponse;
import mss301.bookingservice.repository.projection.TopCaregiverView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Mapper(config = MapStructConfig.class)
public interface StatisticsMapper {
    @Mapping(target = "byCenter", ignore = true)
    @Mapping(target = "responsiveness", ignore = true)
    @Mapping(target = "occurrencesByStatus", ignore = true)
    @Mapping(target = "bookingsByType", ignore = true)
    @Mapping(target = "ratingDistribution", ignore = true)
    @Mapping(target = "trendGroupBy", ignore = true)
    @Mapping(target = "trend", ignore = true)
    OperationalStatisticsResponse toResponse(Map<String, Long> bookingsByStatus, OccurrenceStatisticsResponse occurrences,
            double completionRate, BigDecimal avgRating, long reviewCount, ComplaintStatisticsResponse complaints,
            List<TopCaregiverResponse> topCaregivers);
    OccurrenceStatisticsResponse toOccurrenceResponse(long completed, long cancelled, long missed, long customerNoShow);
    ComplaintStatisticsResponse toComplaintResponse(long open, long inProgress, long resolved, long escalated);
    List<TopCaregiverResponse> toTopResponseList(List<TopCaregiverView> caregivers);
    CenterOperationalStatsResponse toCenterResponse(UUID centerId, OperationalStatisticsResponse statistics);
    ResponsivenessStatisticsResponse toResponsivenessResponse(long answeredCount, Long avgResponseMinutes,
                                                              long expiredCount);
    StatisticsTrendPointResponse toTrendPointResponse(String period, long bookings, long completedVisits,
                                                      long cancelledVisits, long missedVisits);
}
