package mss301.bookingservice.service;

import mss301.bookingservice.client.dto.ProviderScheduleResponse;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.TimeSlotRequest;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.enums.BookingType;
import mss301.bookingservice.enums.RecurrenceFrequency;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.Impl.OccurrenceGeneratorImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sinh buổi chăm sóc theo loại booking (spec §4) — thuần, không Spring. "Bây giờ" cố định: 2026-10-09 02:00 UTC = 09:00
 * giờ Việt Nam (Thứ Sáu). Thời gian đặt trước tối thiểu theo center policy kiểm ở BookingService (BookingApiTests).
 */
class OccurrenceGeneratorTests {

    static final Instant NOW = Instant.parse("2026-10-09T02:00:00Z");
    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    OccurrenceGenerator generator = newGenerator(VIETNAM);

    static OccurrenceGenerator newGenerator(ZoneId zone) {
        BookingProperties properties = new BookingProperties();
        properties.getSchedule().setTimezone(zone);
        properties.getSchedule().setMaxOccurrences(60);
        properties.getSchedule().setMaxSpanDays(90);
        return new OccurrenceGeneratorImpl(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static BookingCreateRequest recurring(RecurrenceFrequency frequency, String startDate, String endDate, String startTime,
                                          String endTime, List<Integer> daysOfWeek) {
        return BookingCreateRequest.builder()
                .bookingType(BookingType.RECURRING)
                .frequency(frequency)
                .startDate(startDate == null ? null : LocalDate.parse(startDate))
                .endDate(endDate == null ? null : LocalDate.parse(endDate))
                .startTime(LocalTime.parse(startTime))
                .endTime(LocalTime.parse(endTime))
                .daysOfWeek(daysOfWeek)
                .build();
    }

    static BookingCreateRequest custom(TimeSlotRequest... slots) {
        return BookingCreateRequest.builder()
                .bookingType(BookingType.CUSTOM)
                .slots(List.of(slots))
                .build();
    }

    static TimeSlotRequest slot(String startAt, String endAt) {
        return TimeSlotRequest.builder()
                .startAt(Instant.parse(startAt))
                .endAt(Instant.parse(endAt))
                .build();
    }

    @Test
    void dailyCoversEveryDayInclusiveAndNumbersSequentially() {
        List<OccurrenceSlot> slots = generator.generate(recurring(RecurrenceFrequency.DAILY, "2026-10-12", "2026-10-18",
                "19:00", "21:00", null));
        assertThat(slots).hasSize(7);
        assertThat(slots).extracting(OccurrenceSlot::getOccurrenceNo).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(slots.get(6).getStartAt()).isEqualTo(Instant.parse("2026-10-18T12:00:00Z"));
        assertThat(slots).extracting(OccurrenceSlot::getCaregiverScheduleId).containsOnlyNulls();
    }

    @Test
    void recurringRequiresFrequencyDatesAndTimes() {
        BookingCreateRequest noFrequency = recurring(RecurrenceFrequency.DAILY, "2026-10-12", "2026-10-13", "08:00",
                "10:00", null);
        noFrequency.setFrequency(null);
        assertError(noFrequency, ErrorCode.RECURRENCE_FREQUENCY_REQUIRED);
        assertError(recurring(RecurrenceFrequency.DAILY, "2026-10-12", null, "08:00", "10:00", null),
                ErrorCode.END_DATE_REQUIRED);
        assertError(recurring(RecurrenceFrequency.DAILY, null, "2026-10-13", "08:00", "10:00", null),
                ErrorCode.START_DATE_REQUIRED);
        assertError(recurring(RecurrenceFrequency.DAILY, "2026-10-12", "2026-10-13", "08:00", "10:00", List.of(2)),
                ErrorCode.DAYS_OF_WEEK_NOT_ALLOWED);
        assertError(recurring(RecurrenceFrequency.DAILY, "2026-10-13", "2026-10-12", "08:00", "10:00", null),
                ErrorCode.INVALID_DATE_RANGE);
        assertError(recurring(RecurrenceFrequency.DAILY, "2026-10-12", "2026-10-13", "22:00", "06:00", null),
                ErrorCode.OVERNIGHT_NOT_SUPPORTED);
    }

    @Test
    void weeklyPicksOnlySelectedDaysInDateOrder() {
        // 2026-10-12 là Thứ Hai; Thứ Hai / Tư / Sáu trong hai tuần, khai báo lộn xộn
        List<OccurrenceSlot> slots = generator.generate(recurring(RecurrenceFrequency.WEEKLY, "2026-10-12", "2026-10-25",
                "08:00", "10:00", List.of(5, 1, 3)));
        assertThat(slots).extracting(OccurrenceSlot::getStartAt).containsExactly(
                Instant.parse("2026-10-12T01:00:00Z"), Instant.parse("2026-10-14T01:00:00Z"),
                Instant.parse("2026-10-16T01:00:00Z"), Instant.parse("2026-10-19T01:00:00Z"),
                Instant.parse("2026-10-21T01:00:00Z"), Instant.parse("2026-10-23T01:00:00Z"));
    }

    @Test
    void weeklyAcrossMonthAndYearBoundary() {
        List<OccurrenceSlot> slots = generator.generate(recurring(RecurrenceFrequency.WEEKLY, "2026-12-28", "2027-01-05",
                "22:30", "23:30", List.of(4)));
        // Thứ Năm 31/12/2026 22:30 VN = 15:30 UTC cùng ngày
        assertThat(slots).extracting(OccurrenceSlot::getStartAt).containsExactly(Instant.parse("2026-12-31T15:30:00Z"));
    }

    @Test
    void weeklyValidatesDaysOfWeek() {
        assertError(recurring(RecurrenceFrequency.WEEKLY, "2026-10-12", "2026-10-25", "08:00", "10:00", null),
                ErrorCode.DAYS_OF_WEEK_REQUIRED);
        assertError(recurring(RecurrenceFrequency.WEEKLY, "2026-10-12", "2026-10-25", "08:00", "10:00", List.of(0)),
                ErrorCode.INVALID_DAYS_OF_WEEK);
        assertError(recurring(RecurrenceFrequency.WEEKLY, "2026-10-12", "2026-10-25", "08:00", "10:00", List.of(2, 2)),
                ErrorCode.INVALID_DAYS_OF_WEEK);
        // Thứ Hai → Thứ Tư nhưng chỉ chọn Thứ Bảy
        assertError(recurring(RecurrenceFrequency.WEEKLY, "2026-10-12", "2026-10-14", "08:00", "10:00", List.of(6)),
                ErrorCode.NO_OCCURRENCE_IN_RANGE);
    }

    @Test
    void customKeepsEachSlotOwnTimeSortedByStart() {
        // Spec §4 CUSTOM: "Danh sách startAt và endAt của từng buổi" — gửi lộn xộn, mỗi buổi một khung giờ khác nhau.
        List<OccurrenceSlot> slots = generator.generate(custom(
                slot("2026-10-15T07:00:00Z", "2026-10-15T09:30:00Z"),
                slot("2026-10-12T01:00:00Z", "2026-10-12T03:00:00Z"),
                slot("2026-10-13T12:00:00Z", "2026-10-13T13:00:00Z")));
        assertThat(slots).extracting(OccurrenceSlot::getOccurrenceNo).containsExactly(1, 2, 3);
        assertThat(slots).extracting(OccurrenceSlot::getStartAt).containsExactly(
                Instant.parse("2026-10-12T01:00:00Z"), Instant.parse("2026-10-13T12:00:00Z"),
                Instant.parse("2026-10-15T07:00:00Z"));
        assertThat(slots).extracting(OccurrenceSlot::getDurationMinutes).containsExactly(120, 60, 150);
    }

    @Test
    void customOccurrencesMustNotOverlap() {
        // Spec §6: các buổi trong cùng booking không được chồng lấn (khoảng nửa mở).
        assertError(custom(slot("2026-10-12T01:00:00Z", "2026-10-12T03:00:00Z"),
                slot("2026-10-12T02:59:00Z", "2026-10-12T04:00:00Z")), ErrorCode.OCCURRENCES_OVERLAP);
        // Buổi sau bắt đầu đúng lúc buổi trước kết thúc → hợp lệ
        assertThat(generator.generate(custom(slot("2026-10-12T01:00:00Z", "2026-10-12T03:00:00Z"),
                slot("2026-10-12T03:00:00Z", "2026-10-12T04:00:00Z")))).hasSize(2);
    }

    @Test
    void customValidatesEachSlot() {
        assertError(custom(slot("2026-10-12T03:00:00Z", "2026-10-12T03:00:00Z")), ErrorCode.INVALID_TIME_SLOT);
        assertError(custom(slot("2026-10-12T01:00:00Z", "2026-10-12T01:45:00Z")), ErrorCode.OCCURRENCE_TOO_SHORT);
        assertError(custom(slot("2026-10-09T01:00:00Z", "2026-10-09T03:00:00Z")), ErrorCode.START_DATE_IN_PAST);
        assertError(BookingCreateRequest.builder().bookingType(BookingType.CUSTOM).slots(List.of()).build(),
                ErrorCode.SLOTS_REQUIRED);
    }

    @Test
    void fieldsMustMatchBookingType() {
        BookingCreateRequest customWithRecurrence = custom(slot("2026-10-12T01:00:00Z", "2026-10-12T03:00:00Z"));
        customWithRecurrence.setFrequency(RecurrenceFrequency.DAILY);
        assertError(customWithRecurrence, ErrorCode.SCHEDULE_FIELD_NOT_ALLOWED);
        BookingCreateRequest recurringWithSchedule = recurring(RecurrenceFrequency.DAILY, "2026-10-12", "2026-10-13",
                "08:00", "10:00", null);
        recurringWithSchedule.setCaregiverScheduleId(UUID.randomUUID());
        assertError(recurringWithSchedule, ErrorCode.SCHEDULE_FIELD_NOT_ALLOWED);
    }

    @Test
    void recurringUsesEditedSlotsWithinTheRuleRange() {
        // CR-1: khách chỉnh danh sách buổi sinh từ quy tắc (đổi giờ, bỏ một ngày) — dùng đúng danh sách đó.
        BookingCreateRequest edited = recurring(RecurrenceFrequency.DAILY, "2026-10-12", "2026-10-14",
                "08:00", "10:00", null);
        edited.setSlots(List.of(slot("2026-10-14T07:00:00Z", "2026-10-14T09:00:00Z"),
                slot("2026-10-12T01:00:00Z", "2026-10-12T03:00:00Z")));
        List<OccurrenceSlot> slots = generator.generate(edited);
        assertThat(slots).extracting(OccurrenceSlot::getStartAt)
                .containsExactly(Instant.parse("2026-10-12T01:00:00Z"), Instant.parse("2026-10-14T07:00:00Z"));
        assertThat(slots).extracting(OccurrenceSlot::getOccurrenceNo).containsExactly(1, 2);

        BookingCreateRequest outside = recurring(RecurrenceFrequency.DAILY, "2026-10-12", "2026-10-13",
                "08:00", "10:00", null);
        outside.setSlots(List.of(slot("2026-10-15T01:00:00Z", "2026-10-15T03:00:00Z")));
        assertError(outside, ErrorCode.SLOT_OUTSIDE_RECURRENCE_RANGE);

        // Quy tắc vẫn phải hợp lệ dù đã có danh sách buổi.
        BookingCreateRequest invalidRule = recurring(RecurrenceFrequency.DAILY, "2026-10-13", "2026-10-12",
                "08:00", "10:00", null);
        invalidRule.setSlots(List.of(slot("2026-10-12T01:00:00Z", "2026-10-12T03:00:00Z")));
        assertError(invalidRule, ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    void shiftRequiresCaregiverAndScheduleAndBuildsOneOccurrenceFromSchedule() {
        // Spec §16: SHIFT không thể tạo nếu thiếu caregiverId hoặc caregiverScheduleId.
        BookingCreateRequest withoutCaregiver = BookingCreateRequest.builder().bookingType(BookingType.SHIFT)
                .caregiverScheduleId(UUID.randomUUID()).build();
        assertShapeError(withoutCaregiver, ErrorCode.CAREGIVER_ID_REQUIRED);
        BookingCreateRequest withoutSchedule = BookingCreateRequest.builder().bookingType(BookingType.SHIFT)
                .caregiverId(UUID.randomUUID()).build();
        assertShapeError(withoutSchedule, ErrorCode.CAREGIVER_SCHEDULE_ID_REQUIRED);
        UUID scheduleId = UUID.randomUUID();
        BookingCreateRequest shift = BookingCreateRequest.builder().bookingType(BookingType.SHIFT)
                .caregiverId(UUID.randomUUID()).caregiverScheduleId(scheduleId).build();
        ProviderScheduleResponse schedule = ProviderScheduleResponse.builder()
                .id(scheduleId)
                .workDate(LocalDate.parse("2026-10-12"))
                .startTime(LocalTime.parse("08:00"))
                .endTime(LocalTime.parse("12:00"))
                .build();
        List<OccurrenceSlot> slots = generator.generateShift(shift, schedule);
        assertThat(slots).hasSize(1);
        assertThat(slots.getFirst().getStartAt()).isEqualTo(Instant.parse("2026-10-12T01:00:00Z"));
        assertThat(slots.getFirst().getEndAt()).isEqualTo(Instant.parse("2026-10-12T05:00:00Z"));
        assertThat(slots.getFirst().getCaregiverScheduleId()).isEqualTo(scheduleId);
        BookingCreateRequest shiftWithSlots = BookingCreateRequest.builder().bookingType(BookingType.SHIFT)
                .caregiverId(UUID.randomUUID()).caregiverScheduleId(scheduleId)
                .slots(List.of(slot("2026-10-12T01:00:00Z", "2026-10-12T03:00:00Z"))).build();
        assertShapeError(shiftWithSlots, ErrorCode.SCHEDULE_FIELD_NOT_ALLOWED);
    }

    @Test
    void limitsOnSpanOccurrenceCountAndAdvanceDays() {
        // 91 ngày > 90
        assertError(recurring(RecurrenceFrequency.WEEKLY, "2026-10-12", "2027-01-10", "08:00", "10:00", List.of(1)),
                ErrorCode.SCHEDULE_SPAN_TOO_LONG);
        // 90 ngày DAILY = 90 buổi > 60
        assertError(recurring(RecurrenceFrequency.DAILY, "2026-10-12", "2027-01-09", "08:00", "10:00", null),
                ErrorCode.TOO_MANY_OCCURRENCES);
        assertThat(generator.generate(recurring(RecurrenceFrequency.DAILY, "2026-10-12", "2026-12-10", "08:00", "10:00",
                null))).hasSize(60);
        // L-08: bắt đầu xa nhất 90 ngày tới (2026-10-09 + 90 = 2027-01-07)
        assertError(custom(slot("2027-01-08T01:00:00Z", "2027-01-08T03:00:00Z")), ErrorCode.START_TOO_FAR);
        assertThat(generator.generate(custom(slot("2027-01-07T01:00:00Z", "2027-01-07T03:00:00Z")))).hasSize(1);
    }

    @Test
    void zoneWithDaylightSavingKeepsLocalWallClock() {
        OccurrenceGenerator newYork = newGenerator(ZoneId.of("America/New_York"));
        // DST kết thúc 01/11/2026: 09:00 New York là 13:00Z trước đó và 14:00Z sau đó
        List<OccurrenceSlot> slots = newYork.generate(recurring(RecurrenceFrequency.DAILY, "2026-10-31", "2026-11-01",
                "09:00", "10:00", null));
        assertThat(slots).extracting(OccurrenceSlot::getStartAt).containsExactly(
                Instant.parse("2026-10-31T13:00:00Z"), Instant.parse("2026-11-01T14:00:00Z"));
        assertThat(slots).extracting(OccurrenceSlot::getDurationMinutes).containsOnly(60);
    }

    private void assertError(BookingCreateRequest request, ErrorCode expected) {
        assertThatThrownBy(() -> generator.generate(request))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(expected);
    }

    private void assertShapeError(BookingCreateRequest request, ErrorCode expected) {
        assertThatThrownBy(() -> generator.checkShape(request))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
