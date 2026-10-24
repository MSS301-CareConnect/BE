package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.OperationalStatisticsResponse;
import mss301.bookingservice.dto.response.OccurrenceStatisticsResponse;
import mss301.bookingservice.dto.response.ComplaintStatisticsResponse;
import mss301.bookingservice.dto.response.CenterOperationalStatsResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.enums.FeedbackVisibility;
import mss301.bookingservice.enums.CareSessionStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.StatisticsMapper;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.StatisticsCountView;
import mss301.bookingservice.repository.FeedbackRatingView;
import mss301.bookingservice.service.StatisticsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StatisticsServiceImpl implements StatisticsService {
    static ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    BookingRepository bookingRepository;
    StatisticsMapper statisticsMapper;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<OperationalStatisticsResponse> centers(Set<RoleCode> roles, UUID scopeCenterId, UUID centerId, LocalDate from, LocalDate to) {
        UUID effectiveCenterId = roles.contains(RoleCode.ADMIN) ? centerId : scopeCenterId;
        if (!roles.contains(RoleCode.ADMIN) && effectiveCenterId == null) {
            throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
        }
        LocalDate end = to == null ? LocalDate.now(clock.withZone(ZONE)) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        validateRange(start, end);
        OperationalStatisticsResponse response = aggregate(effectiveCenterId, start.atStartOfDay(ZONE).toInstant(),
                end.plusDays(1).atStartOfDay(ZONE).toInstant());
        return APIResponse.success("Lấy thống kê trung tâm thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<OperationalStatisticsResponse> system(LocalDate from, LocalDate to, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        LocalDate end = to == null ? LocalDate.now(clock.withZone(ZONE)) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        validateRange(start, end);
        Instant fromInstant = start.atStartOfDay(ZONE).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(ZONE).toInstant();
        OperationalStatisticsResponse response = aggregate(null, fromInstant, toInstant);
        Page<UUID> centers = bookingRepository.findCenterIds(fromInstant, toInstant, PageRequest.of(page, size));
        List<CenterOperationalStatsResponse> items = centers.getContent().stream()
                .map(id -> statisticsMapper.toCenterResponse(id, aggregate(id, fromInstant, toInstant))).toList();
        response.setByCenter(PageResponse.of(centers, items));
        return APIResponse.success("Lấy thống kê hệ thống thành công", response);
    }

    private OperationalStatisticsResponse aggregate(UUID centerId, Instant from, Instant to) {
        Map<String, Long> bookings = toCounts(bookingRepository.countBookings(centerId, from, to));
        Map<String, Long> occurrences = toCounts(bookingRepository.countOccurrences(centerId, from, to));
        Map<String, Long> complaints = toCounts(bookingRepository.countComplaints(centerId, from, to));
        long completed = occurrences.getOrDefault("COMPLETED", 0L);
        long cancelled = occurrences.getOrDefault("CANCELLED", 0L);
        long missed = occurrences.getOrDefault("MISSED", 0L);
        long noShow = occurrences.getOrDefault("CUSTOMER_NO_SHOW", 0L);
        long ended = completed + cancelled + missed + noShow;
        double rate = ended == 0 ? 0 : BigDecimal.valueOf(completed * 100L)
                .divide(BigDecimal.valueOf(ended), 2, RoundingMode.HALF_UP).doubleValue();
        OccurrenceStatisticsResponse occurrenceResponse = statisticsMapper.toOccurrenceResponse(completed, cancelled, missed, noShow);
        ComplaintStatisticsResponse complaintResponse = statisticsMapper.toComplaintResponse(complaints.getOrDefault("OPEN", 0L),
                complaints.getOrDefault("IN_PROGRESS", 0L), complaints.getOrDefault("RESOLVED", 0L) + complaints.getOrDefault("RESOLVED_BY_ADMIN", 0L),
                complaints.getOrDefault("ESCALATED", 0L));
        FeedbackRatingView rating = bookingRepository.summarizeRating(centerId, from, to, FeedbackVisibility.VISIBLE);
        BigDecimal average = rating.getRatingAverage() == null ? BigDecimal.ZERO : BigDecimal.valueOf(rating.getRatingAverage()).setScale(2, RoundingMode.HALF_UP);
        return statisticsMapper.toResponse(bookings, occurrenceResponse, rate, average, rating.getRatingCount(), complaintResponse,
                statisticsMapper.toTopResponseList(bookingRepository.topCaregivers(centerId, from, to, FeedbackVisibility.VISIBLE,
                        CareSessionStatus.ENDED, PageRequest.of(0, 10))));
    }

    private Map<String, Long> toCounts(List<StatisticsCountView> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        rows.forEach(row -> counts.put(row.getCategory(), row.getCount()));
        return counts;
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new AppException(ErrorCode.INVALID_DATE_FILTER);
        }
    }
}
