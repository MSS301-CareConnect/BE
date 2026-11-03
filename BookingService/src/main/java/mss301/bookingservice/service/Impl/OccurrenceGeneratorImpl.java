package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.client.dto.ProviderScheduleResponse;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.BookingScheduleRequest;
import mss301.bookingservice.dto.request.TimeSlotRequest;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.enums.BookingType;
import mss301.bookingservice.enums.RecurrenceFrequency;
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
import java.util.Comparator;
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
    public void checkShape(BookingScheduleRequest request) {
        boolean hasRecurrence = request.getFrequency() != null || request.getStartDate() != null
                || request.getEndDate() != null || request.getStartTime() != null || request.getEndTime() != null
                || (request.getDaysOfWeek() != null && !request.getDaysOfWeek().isEmpty());
        boolean hasSlots = request.getSlots() != null && !request.getSlots().isEmpty();
        switch (request.getBookingType()) {
            case SHIFT -> {
                if (request.getCaregiverId() == null) {
                    throw new AppException(ErrorCode.CAREGIVER_ID_REQUIRED);
                }
                if (request.getCaregiverScheduleId() == null) {
                    throw new AppException(ErrorCode.CAREGIVER_SCHEDULE_ID_REQUIRED);
                }
                if (hasRecurrence || hasSlots) {
                    throw new AppException(ErrorCode.SCHEDULE_FIELD_NOT_ALLOWED);
                }
            }
            case RECURRING -> {
                // slots đi kèm quy tắc = danh sách buổi khách đã chỉnh từ quy tắc (CR-1, 2026-11-03).
                if (request.getCaregiverScheduleId() != null) {
                    throw new AppException(ErrorCode.SCHEDULE_FIELD_NOT_ALLOWED);
                }
                checkRecurrenceRequired(request);
            }
            case CUSTOM -> {
                if (request.getCaregiverScheduleId() != null || hasRecurrence) {
                    throw new AppException(ErrorCode.SCHEDULE_FIELD_NOT_ALLOWED);
                }
                if (!hasSlots) {
                    throw new AppException(ErrorCode.SLOTS_REQUIRED);
                }
            }
        }
    }

    @Override
    public List<OccurrenceSlot> generate(BookingScheduleRequest request) {
        checkShape(request);
        List<OccurrenceSlot> slots = switch (request.getBookingType()) {
            case RECURRING -> generateEditedRecurring(request);
            case CUSTOM -> generateCustom(request.getSlots());
            case SHIFT -> throw new AppException(ErrorCode.CAREGIVER_SCHEDULE_ID_REQUIRED);
        };
        checkCommonRules(slots);
        return slots;
    }

    @Override
    public List<OccurrenceSlot> generateShift(BookingScheduleRequest request, ProviderScheduleResponse schedule) {
        checkShape(request);
        ZoneId zone = bookingProperties.getSchedule().getTimezone();
        // Ca của Provider theo giờ Việt Nam, kết thúc sau giờ bắt đầu trong cùng ngày (CHECK work_shifts).
        OccurrenceSlot slot = buildSlot(1, ZonedDateTime.of(schedule.getWorkDate(), schedule.getStartTime(), zone).toInstant(),
                ZonedDateTime.of(schedule.getWorkDate(), schedule.getEndTime(), zone).toInstant());
        slot.setCaregiverScheduleId(schedule.getId());
        List<OccurrenceSlot> slots = List.of(slot);
        checkCommonRules(slots);
        return slots;
    }

    private void checkRecurrenceRequired(BookingScheduleRequest request) {
        if (request.getFrequency() == null) {
            throw new AppException(ErrorCode.RECURRENCE_FREQUENCY_REQUIRED);
        }
        if (request.getStartDate() == null) {
            throw new AppException(ErrorCode.START_DATE_REQUIRED);
        }
        if (request.getEndDate() == null) {
            throw new AppException(ErrorCode.END_DATE_REQUIRED);
        }
        if (request.getStartTime() == null) {
            throw new AppException(ErrorCode.START_TIME_REQUIRED);
        }
        if (request.getEndTime() == null) {
            throw new AppException(ErrorCode.END_TIME_REQUIRED);
        }
    }

    private List<OccurrenceSlot> generateRecurring(BookingScheduleRequest request) {
        BookingProperties.Schedule schedule = bookingProperties.getSchedule();
        ZoneId zone = schedule.getTimezone();
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();
        LocalTime startTime = request.getStartTime();
        LocalTime endTime = request.getEndTime();
        // TODO(ca qua đêm): RECURRING giờ kết thúc phải sau giờ bắt đầu trong cùng ngày (CUSTOM thì không giới hạn).
        if (!endTime.isAfter(startTime)) {
            throw new AppException(ErrorCode.OVERNIGHT_NOT_SUPPORTED);
        }
        if (endDate.isBefore(startDate)) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }
        // Khoảng ngày khách chọn (không chỉ các ngày có buổi) không được dài quá giới hạn.
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > schedule.getMaxSpanDays()) {
            throw new AppException(ErrorCode.SCHEDULE_SPAN_TOO_LONG);
        }
        Set<DayOfWeek> daysOfWeek = resolveDaysOfWeek(request);
        List<OccurrenceSlot> slots = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (!daysOfWeek.contains(date.getDayOfWeek())) {
                continue;
            }
            if (slots.size() == schedule.getMaxOccurrences()) {
                throw new AppException(ErrorCode.TOO_MANY_OCCURRENCES);
            }
            Instant startAt = ZonedDateTime.of(date, startTime, zone).toInstant();
            Instant endAt = ZonedDateTime.of(date, endTime, zone).toInstant();
            slots.add(buildSlot(slots.size() + 1, startAt, endAt));
        }
        if (slots.isEmpty()) {
            throw new AppException(ErrorCode.NO_OCCURRENCE_IN_RANGE);
        }
        return slots;
    }

    // RECURRING (CR-1): quy tắc luôn phải hợp lệ; có slots thì dùng danh sách khách đã chỉnh (đổi giờ, bỏ hoặc thêm
    // buổi) thay cho các buổi sinh ra, miễn mọi buổi nằm trong khoảng ngày của quy tắc (giờ Việt Nam).
    private List<OccurrenceSlot> generateEditedRecurring(BookingScheduleRequest request) {
        List<OccurrenceSlot> generated = generateRecurring(request);
        List<TimeSlotRequest> requestedSlots = request.getSlots();
        if (requestedSlots == null || requestedSlots.isEmpty()) {
            return generated;
        }
        ZoneId zone = bookingProperties.getSchedule().getTimezone();
        for (TimeSlotRequest requestedSlot : requestedSlots) {
            if (requestedSlot.getStartAt() == null || requestedSlot.getEndAt() == null) {
                throw new AppException(ErrorCode.INVALID_TIME_SLOT);
            }
            LocalDate date = LocalDate.ofInstant(requestedSlot.getStartAt(), zone);
            if (date.isBefore(request.getStartDate()) || date.isAfter(request.getEndDate())) {
                throw new AppException(ErrorCode.SLOT_OUTSIDE_RECURRENCE_RANGE);
            }
        }
        return generateCustom(requestedSlots);
    }

    // CUSTOM (spec §4): mỗi buổi một khung giờ riêng; sắp theo giờ bắt đầu rồi đánh số lại.
    private List<OccurrenceSlot> generateCustom(List<TimeSlotRequest> requestedSlots) {
        if (requestedSlots.size() > bookingProperties.getSchedule().getMaxOccurrences()) {
            throw new AppException(ErrorCode.TOO_MANY_OCCURRENCES);
        }
        List<TimeSlotRequest> sortedSlots = requestedSlots.stream()
                .sorted(Comparator.comparing(TimeSlotRequest::getStartAt))
                .toList();
        List<OccurrenceSlot> slots = new ArrayList<>();
        for (TimeSlotRequest requestedSlot : sortedSlots) {
            slots.add(buildSlot(slots.size() + 1, requestedSlot.getStartAt(), requestedSlot.getEndAt()));
        }
        return slots;
    }

    // Quy tắc chung mọi loại (spec §6): khoảng hợp lệ, đủ thời lượng tối thiểu, không chồng nhau trong booking, không ở
    // quá khứ / quá xa / kéo quá dài.
    private void checkCommonRules(List<OccurrenceSlot> slots) {
        BookingProperties.Schedule schedule = bookingProperties.getSchedule();
        ZoneId zone = schedule.getTimezone();
        long minMinutes = schedule.getMinOccurrenceDuration().toMinutes();
        for (int index = 0; index < slots.size(); index++) {
            OccurrenceSlot slot = slots.get(index);
            if (slot.getDurationMinutes() < minMinutes) {
                throw new AppException(ErrorCode.OCCURRENCE_TOO_SHORT);
            }
            // Khoảng nửa mở [start, end): buổi sau bắt đầu đúng lúc buổi trước kết thúc không tính là chồng.
            if (index > 0 && slot.getStartAt().isBefore(slots.get(index - 1).getEndAt())) {
                throw new AppException(ErrorCode.OCCURRENCES_OVERLAP);
            }
        }
        Instant now = clock.instant();
        Instant firstStart = slots.getFirst().getStartAt();
        if (!firstStart.isAfter(now)) {
            throw new AppException(ErrorCode.START_DATE_IN_PAST);
        }
        LocalDate today = LocalDate.ofInstant(now, zone);
        LocalDate firstDate = LocalDate.ofInstant(firstStart, zone);
        LocalDate lastDate = LocalDate.ofInstant(slots.getLast().getStartAt(), zone);
        if (firstDate.isAfter(today.plusDays(schedule.getMaxAdvanceDays()))) {
            throw new AppException(ErrorCode.START_TOO_FAR);
        }
        if (ChronoUnit.DAYS.between(firstDate, lastDate) + 1 > schedule.getMaxSpanDays()) {
            throw new AppException(ErrorCode.SCHEDULE_SPAN_TOO_LONG);
        }
    }

    // Thứ được sinh buổi: WEEKLY theo daysOfWeek (ISO 1..7, không trùng); DAILY mọi ngày, không nhận daysOfWeek.
    private Set<DayOfWeek> resolveDaysOfWeek(BookingScheduleRequest request) {
        List<Integer> requestedDays = request.getDaysOfWeek();
        boolean hasDays = requestedDays != null && !requestedDays.isEmpty();
        if (request.getFrequency() != RecurrenceFrequency.WEEKLY) {
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

    // Giờ địa phương đã đổi sang UTC qua ZonedDateTime (giờ rơi vào khoảng nhảy DST được dời tới giờ hợp lệ kế tiếp).
    private OccurrenceSlot buildSlot(int occurrenceNo, Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new AppException(ErrorCode.INVALID_TIME_SLOT);
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
