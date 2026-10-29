package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.BookingScheduleRequest;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.enums.ScheduleType;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.OccurrenceGenerator;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OccurrenceGeneratorImpl implements OccurrenceGenerator {

    static int MIN_DAY_OF_WEEK = 1;
    static int MAX_DAY_OF_WEEK = 7;

    BookingProperties bookingProperties;
    Clock clock;

    @Override
    public List<OccurrenceSlot> generate(BookingScheduleRequest request) {
        BookingProperties.Schedule schedule = bookingProperties.getSchedule();
        ZoneId zone = schedule.getTimezone();
        LocalDate startDate = request.getStartDate();
        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();
        // TODO(ca qua đêm): hiện giờ kết thúc phải sau giờ bắt đầu trong cùng ngày.
        if (!endTime.isAfter(startTime)) {
            throw new AppException(ErrorCode.OVERNIGHT_NOT_SUPPORTED);
        }
        if (Duration.between(startTime, endTime).compareTo(schedule.getMinOccurrenceDuration()) < 0) {
            throw new AppException(ErrorCode.OCCURRENCE_TOO_SHORT);
        }
        LocalDate endDate = resolveEndDate(request);
        Set<DayOfWeek> daysOfWeek = resolveDaysOfWeek(request);
        if (endDate.isBefore(startDate)) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
        Instant now = clock.instant();
        if (startDate.isBefore(LocalDate.ofInstant(now, zone))) {
            throw new AppException(ErrorCode.START_DATE_IN_PAST);
        }
        if (startDate.isAfter(LocalDate.ofInstant(now, zone).plusDays(schedule.getMaxAdvanceDays()))) {
            throw new AppException(ErrorCode.START_TOO_FAR);
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > schedule.getMaxSpanDays()) {
            throw new AppException(ErrorCode.SCHEDULE_SPAN_TOO_LONG);
        }
        List<OccurrenceSlot> slots = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (!daysOfWeek.contains(date.getDayOfWeek())) {
                continue;
            }
            if (slots.size() == schedule.getMaxOccurrences()) {
                throw new AppException(ErrorCode.TOO_MANY_OCCURRENCES);
            }
            slots.add(buildSlot(slots.size() + 1, date, startTime, endTime, zone));
        }
        if (slots.isEmpty()) {
            throw new AppException(ErrorCode.NO_OCCURRENCE_IN_RANGE);
        }
        Instant earliestStart = now.plus(schedule.getMinLeadTime());
        if (slots.getFirst().getStartAt().isBefore(earliestStart)) {
            throw new AppException(ErrorCode.LEAD_TIME_TOO_SHORT);
        }
        return slots;
    }

    // ONE_TIME: endDate trống hoặc cùng ngày; DAILY / WEEKLY: bắt buộc có endDate.
    private LocalDate resolveEndDate(BookingScheduleRequest request) {
        LocalDate endDate = request.getEndDate();
        if (request.getScheduleType() == ScheduleType.ONE_TIME) {
            if (endDate != null && !endDate.equals(request.getStartDate())) {
                throw new AppException(ErrorCode.INVALID_DATE_RANGE);
            }
            return request.getStartDate();
        }
        if (endDate == null) {
            throw new AppException(ErrorCode.END_DATE_REQUIRED);
        }
        return endDate;
    }

    // Thứ được sinh buổi: WEEKLY theo daysOfWeek (ISO 1..7, không trùng); ONE_TIME / DAILY mọi ngày, không nhận daysOfWeek.
    private Set<DayOfWeek> resolveDaysOfWeek(BookingScheduleRequest request) {
        List<Integer> requestedDays = request.getDaysOfWeek();
        boolean hasDays = requestedDays != null && !requestedDays.isEmpty();
        if (request.getScheduleType() != ScheduleType.WEEKLY) {
            if (hasDays) {
                throw new AppException(ErrorCode.DAYS_OF_WEEK_NOT_ALLOWED);
            }
            return EnumSet.allOf(DayOfWeek.class);
        }
        if (!hasDays) {
            throw new AppException(ErrorCode.DAYS_OF_WEEK_REQUIRED);
        }
        Set<DayOfWeek> daysOfWeek = EnumSet.noneOf(DayOfWeek.class);
        for (Integer day : requestedDays) {
            if (day == null || day < MIN_DAY_OF_WEEK || day > MAX_DAY_OF_WEEK || !daysOfWeek.add(DayOfWeek.of(day))) {
                throw new AppException(ErrorCode.INVALID_DAYS_OF_WEEK);
            }
        }
        return daysOfWeek;
    }

    // Giờ địa phương → UTC qua ZonedDateTime (giờ rơi vào khoảng nhảy DST được dời tới giờ hợp lệ kế tiếp).
    private OccurrenceSlot buildSlot(int occurrenceNo, LocalDate date, LocalTime startTime, LocalTime endTime, ZoneId zone) {
        Instant startAt = ZonedDateTime.of(date, startTime, zone).toInstant();
        Instant endAt = ZonedDateTime.of(date, endTime, zone).toInstant();
        if (!endAt.isAfter(startAt)) {
            throw new AppException(ErrorCode.OVERNIGHT_NOT_SUPPORTED);
        }
        int durationMinutes = (int) Duration.between(startAt, endAt).toMinutes();
        return OccurrenceSlot.builder()
                .occurrenceNo(occurrenceNo)
                .startAt(startAt)
                .endAt(endAt)
                .durationMinutes(durationMinutes)
                .build();
    }
}
