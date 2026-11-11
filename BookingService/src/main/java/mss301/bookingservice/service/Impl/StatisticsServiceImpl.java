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
import mss301.bookingservice.dto.response.ResponsivenessStatisticsResponse;
import mss301.bookingservice.dto.response.StatisticsTrendPointResponse;
import mss301.bookingservice.enums.BookingExpiredReason;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.enums.FeedbackVisibility;
import mss301.bookingservice.enums.CareSessionStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.StatisticsMapper;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.BookingResponseTimeView;
import mss301.bookingservice.repository.StatisticsCountView;
import mss301.bookingservice.repository.FeedbackRatingView;
import mss301.bookingservice.repository.OccurrenceStartView;
import mss301.bookingservice.service.StatisticsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
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
    // Trung tâm trả lời booking chờ xác nhận: xác nhận (chờ thanh toán / chờ phân công) hoặc từ chối.
    static Set<BookingStatus> CENTER_ANSWERS = EnumSet.of(BookingStatus.AWAITING_INITIAL_PAYMENT,
            BookingStatus.PENDING_CAREGIVER_ASSIGNMENT, BookingStatus.REJECTED);
    static int MAX_RATING = 5;
    // Kỳ ngắn hơn ~2 tháng vẽ theo ngày, dài hơn theo tháng (kỳ tối đa 366 ngày → ≤ 13 cột).
    static long DAILY_TREND_MAX_DAYS = 62;
    static String TREND_DAY = "DAY";
    static String TREND_MONTH = "MONTH";
    static DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    static Set<BookingExpiredReason> CENTER_TIMEOUTS = EnumSet.of(BookingExpiredReason.CENTER_CONFIRMATION_TIMEOUT,
            BookingExpiredReason.ASSIGNMENT_TIMEOUT);

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
        addCharts(response, effectiveCenterId, start, end);
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
        addCharts(response, null, start, end);
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
        long missed = occurrences.getOrDefault(OccurrenceStatus.CAREGIVER_MISSED.name(), 0L);
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
        OperationalStatisticsResponse response = statisticsMapper.toResponse(bookings, occurrenceResponse, rate, average,
                rating.getRatingCount(), complaintResponse,
                statisticsMapper.toTopResponseList(bookingRepository.topCaregivers(centerId, from, to, FeedbackVisibility.VISIBLE,
                        CareSessionStatus.ENDED, PageRequest.of(0, 10))));
        response.setResponsiveness(responsiveness(centerId, from, to));
        return response;
    }

    // Số liệu biểu đồ chỉ cho số tổng của kỳ (không chạy cho từng trung tâm của byCenter).
    private void addCharts(OperationalStatisticsResponse response, UUID centerId, LocalDate start, LocalDate end) {
        Instant from = start.atStartOfDay(ZONE).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(ZONE).toInstant();
        response.setOccurrencesByStatus(toCounts(bookingRepository.countOccurrences(centerId, from, to)));
        response.setBookingsByType(toCounts(bookingRepository.countBookingTypes(centerId, from, to)));
        Map<String, Long> ratings = new LinkedHashMap<>();
        for (int star = 1; star <= MAX_RATING; star++) {
            ratings.put(String.valueOf(star), 0L);
        }
        bookingRepository.countRatings(centerId, from, to, FeedbackVisibility.VISIBLE).stream()
                .filter(row -> row.getRating() != null)
                .forEach(row -> ratings.merge(String.valueOf(row.getRating()), row.getCount(), Long::sum));
        response.setRatingDistribution(ratings);
        boolean daily = ChronoUnit.DAYS.between(start, end) < DAILY_TREND_MAX_DAYS;
        response.setTrendGroupBy(daily ? TREND_DAY : TREND_MONTH);
        response.setTrend(trend(centerId, start, end, from, to, daily));
    }

    // Mọi kỳ con đều có điểm (kể cả 0) để biểu đồ không bị hụt cột.
    private List<StatisticsTrendPointResponse> trend(UUID centerId, LocalDate start, LocalDate end, Instant from,
                                                     Instant to, boolean daily) {
        DateTimeFormatter format = daily ? DateTimeFormatter.ISO_LOCAL_DATE : MONTH_FORMAT;
        Map<String, long[]> buckets = new LinkedHashMap<>();
        LocalDate cursor = daily ? start : start.withDayOfMonth(1);
        while (!cursor.isAfter(end)) {
            buckets.put(cursor.format(format), new long[4]);
            cursor = daily ? cursor.plusDays(1) : cursor.plusMonths(1);
        }
        for (Instant createdAt : bookingRepository.findCreatedAts(centerId, from, to)) {
            long[] bucket = buckets.get(createdAt.atZone(ZONE).toLocalDate().format(format));
            if (bucket != null) {
                bucket[0]++;
            }
        }
        for (OccurrenceStartView occurrence : bookingRepository.findOccurrenceStarts(centerId, from, to)) {
            long[] bucket = buckets.get(occurrence.getStartAt().atZone(ZONE).toLocalDate().format(format));
            if (bucket == null || occurrence.getStatus() == null) {
                continue;
            }
            switch (occurrence.getStatus()) {
                case COMPLETED -> bucket[1]++;
                case CANCELLED -> bucket[2]++;
                case CAREGIVER_MISSED, CUSTOMER_NO_SHOW -> bucket[3]++;
                default -> {
                }
            }
        }
        List<StatisticsTrendPointResponse> points = new ArrayList<>();
        buckets.forEach((period, values) -> points.add(statisticsMapper.toTrendPointResponse(period, values[0],
                values[1], values[2], values[3])));
        return points;
    }

    private ResponsivenessStatisticsResponse responsiveness(UUID centerId, Instant from, Instant to) {
        List<BookingResponseTimeView> answers = bookingRepository.findCenterResponses(centerId, from, to,
                BookingStatus.PENDING_CENTER_CONFIRMATION, CENTER_ANSWERS);
        Long avgMinutes = answers.isEmpty() ? null : Math.round(answers.stream()
                .mapToLong(answer -> Duration.between(answer.getCreatedAt(), answer.getChangedAt()).toMinutes())
                .average().orElse(0));
        return statisticsMapper.toResponsivenessResponse(answers.size(), avgMinutes,
                bookingRepository.countExpired(centerId, from, to, CENTER_TIMEOUTS));
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
