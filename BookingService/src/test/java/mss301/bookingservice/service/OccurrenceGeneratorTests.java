package mss301.bookingservice.service;

import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.enums.ScheduleType;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.Impl.OccurrenceGeneratorImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sinh buổi chăm sóc — thuần, không Spring. "Bây giờ" cố định: 2026-10-09 02:00 UTC = 09:00 giờ Việt Nam (Thứ Sáu).
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
        properties.getSchedule().setMinLeadTime(Duration.ofHours(12));
        return new OccurrenceGeneratorImpl(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static BookingCreateRequest request(ScheduleType type, String startDate, String endDate, String startTime,
                                        String endTime, List<Integer> daysOfWeek) {
        return BookingCreateRequest.builder()
                .scheduleType(type)
                .startDate(LocalDate.parse(startDate))
                .endDate(endDate == null ? null : LocalDate.parse(endDate))
                .startTime(LocalTime.parse(startTime))
                .endTime(LocalTime.parse(endTime))
                .daysOfWeek(daysOfWeek)
                .build();
    }

    @Test
    void oneTimeCreatesSingleOccurrenceConvertedToUtc() {
        List<OccurrenceSlot> slots = generator.generate(request(ScheduleType.ONE_TIME, "2026-10-12", null,
                "08:00", "10:30", null));
        assertThat(slots).hasSize(1);
        assertThat(slots.getFirst().getOccurrenceNo()).isEqualTo(1);
        assertThat(slots.getFirst().getStartAt()).isEqualTo(Instant.parse("2026-10-12T01:00:00Z"));
        assertThat(slots.getFirst().getEndAt()).isEqualTo(Instant.parse("2026-10-12T03:30:00Z"));
        assertThat(slots.getFirst().getDurationMinutes()).isEqualTo(150);
        // endDate trùng startDate cũng hợp lệ
        assertThat(generator.generate(request(ScheduleType.ONE_TIME, "2026-10-12", "2026-10-12", "08:00", "10:00", null)))
                .hasSize(1);
    }

    @Test
    void oneTimeRejectsDifferentEndDateAndDaysOfWeek() {
        assertError(request(ScheduleType.ONE_TIME, "2026-10-12", "2026-10-13", "08:00", "10:00", null),
                ErrorCode.INVALID_DATE_RANGE);
        assertError(request(ScheduleType.ONE_TIME, "2026-10-12", null, "08:00", "10:00", List.of(1)),
                ErrorCode.DAYS_OF_WEEK_NOT_ALLOWED);
    }

    @Test
    void dailyCoversEveryDayInclusiveAndNumbersSequentially() {
        List<OccurrenceSlot> slots = generator.generate(request(ScheduleType.DAILY, "2026-10-12", "2026-10-18",
                "19:00", "21:00", null));
        assertThat(slots).hasSize(7);
        assertThat(slots).extracting(OccurrenceSlot::getOccurrenceNo).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(slots.get(6).getStartAt()).isEqualTo(Instant.parse("2026-10-18T12:00:00Z"));
    }

    @Test
    void dailyRequiresEndDateAndNoDays() {
        assertError(request(ScheduleType.DAILY, "2026-10-12", null, "08:00", "10:00", null), ErrorCode.END_DATE_REQUIRED);
        assertError(request(ScheduleType.DAILY, "2026-10-12", "2026-10-13", "08:00", "10:00", List.of(2)),
                ErrorCode.DAYS_OF_WEEK_NOT_ALLOWED);
        assertError(request(ScheduleType.DAILY, "2026-10-13", "2026-10-12", "08:00", "10:00", null),
                ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    void weeklyPicksOnlySelectedDaysInDateOrder() {
        // 2026-10-12 là Thứ Hai; Thứ Hai / Tư / Sáu trong hai tuần, khai báo lộn xộn
        List<OccurrenceSlot> slots = generator.generate(request(ScheduleType.WEEKLY, "2026-10-12", "2026-10-25",
                "08:00", "10:00", List.of(5, 1, 3)));
        assertThat(slots).extracting(OccurrenceSlot::getStartAt).containsExactly(
                Instant.parse("2026-10-12T01:00:00Z"), Instant.parse("2026-10-14T01:00:00Z"),
                Instant.parse("2026-10-16T01:00:00Z"), Instant.parse("2026-10-19T01:00:00Z"),
                Instant.parse("2026-10-21T01:00:00Z"), Instant.parse("2026-10-23T01:00:00Z"));
    }

    @Test
    void weeklyIncludesBoundaryDaysOfRange() {
        // Bắt đầu Chủ Nhật, kết thúc Chủ Nhật: cả hai ngày biên đều được tính
        List<OccurrenceSlot> slots = generator.generate(request(ScheduleType.WEEKLY, "2026-10-11", "2026-10-18",
                "08:00", "09:00", List.of(7)));
        assertThat(slots).extracting(OccurrenceSlot::getStartAt).containsExactly(
                Instant.parse("2026-10-11T01:00:00Z"), Instant.parse("2026-10-18T01:00:00Z"));
    }

    @Test
    void weeklyAcrossMonthAndYearBoundary() {
        List<OccurrenceSlot> slots = generator.generate(request(ScheduleType.WEEKLY, "2026-12-28", "2027-01-05",
                "22:30", "23:30", List.of(4)));
        // Thứ Năm 31/12/2026 22:30 VN = 15:30 UTC cùng ngày; ngày đầu năm vẫn đúng thứ tự
        assertThat(slots).extracting(OccurrenceSlot::getStartAt).containsExactly(Instant.parse("2026-12-31T15:30:00Z"));
    }

    @Test
    void weeklyRangeWithoutSelectedDayFails() {
        // Thứ Hai → Thứ Tư nhưng chỉ chọn Thứ Bảy
        assertError(request(ScheduleType.WEEKLY, "2026-10-12", "2026-10-14", "08:00", "10:00", List.of(6)),
                ErrorCode.NO_OCCURRENCE_IN_RANGE);
    }

    @Test
    void weeklyValidatesDaysOfWeek() {
        assertError(request(ScheduleType.WEEKLY, "2026-10-12", "2026-10-25", "08:00", "10:00", null),
                ErrorCode.DAYS_OF_WEEK_REQUIRED);
        assertError(request(ScheduleType.WEEKLY, "2026-10-12", "2026-10-25", "08:00", "10:00", List.of()),
                ErrorCode.DAYS_OF_WEEK_REQUIRED);
        assertError(request(ScheduleType.WEEKLY, "2026-10-12", "2026-10-25", "08:00", "10:00", List.of(0)),
                ErrorCode.INVALID_DAYS_OF_WEEK);
        assertError(request(ScheduleType.WEEKLY, "2026-10-12", "2026-10-25", "08:00", "10:00", List.of(8)),
                ErrorCode.INVALID_DAYS_OF_WEEK);
        assertError(request(ScheduleType.WEEKLY, "2026-10-12", "2026-10-25", "08:00", "10:00", List.of(2, 2)),
                ErrorCode.INVALID_DAYS_OF_WEEK);
        assertError(request(ScheduleType.WEEKLY, "2026-10-12", null, "08:00", "10:00", List.of(2)),
                ErrorCode.END_DATE_REQUIRED);
    }

    @Test
    void timeRangeMustBeSameDay() {
        assertError(request(ScheduleType.ONE_TIME, "2026-10-12", null, "22:00", "06:00", null),
                ErrorCode.OVERNIGHT_NOT_SUPPORTED);
        assertError(request(ScheduleType.ONE_TIME, "2026-10-12", null, "08:00", "08:00", null),
                ErrorCode.OVERNIGHT_NOT_SUPPORTED);
    }

    @Test
    void startDateAndLeadTimeAreChecked() {
        assertError(request(ScheduleType.ONE_TIME, "2026-10-08", null, "08:00", "10:00", null),
                ErrorCode.START_DATE_IN_PAST);
        // Hôm nay 20:00 VN chỉ cách bây giờ (09:00) 11 giờ < 12 giờ báo trước
        assertError(request(ScheduleType.ONE_TIME, "2026-10-09", null, "20:00", "21:00", null),
                ErrorCode.LEAD_TIME_TOO_SHORT);
        // Hôm nay 21:00 VN cách đúng 12 giờ → hợp lệ (ranh giới)
        assertThat(generator.generate(request(ScheduleType.ONE_TIME, "2026-10-09", null, "21:00", "22:00", null)))
                .hasSize(1);
    }

    @Test
    void limitsOnSpanAndOccurrenceCount() {
        // 91 ngày > 90
        assertError(request(ScheduleType.WEEKLY, "2026-10-12", "2027-01-10", "08:00", "10:00", List.of(1)),
                ErrorCode.SCHEDULE_SPAN_TOO_LONG);
        // 90 ngày DAILY = 90 buổi > 60
        assertError(request(ScheduleType.DAILY, "2026-10-12", "2027-01-09", "08:00", "10:00", null),
                ErrorCode.TOO_MANY_OCCURRENCES);
        // đúng 60 buổi → hợp lệ
        assertThat(generator.generate(request(ScheduleType.DAILY, "2026-10-12", "2026-12-10", "08:00", "10:00", null)))
                .hasSize(60);
    }

    // L-08: bắt đầu xa nhất 90 ngày tới (2026-10-09 + 90 = 2027-01-07); mỗi buổi ít nhất 1 giờ.
    @Test
    void startTooFarAndVisitTooShortAreRejected() {
        assertError(request(ScheduleType.ONE_TIME, "2027-01-08", null, "08:00", "10:00", null), ErrorCode.START_TOO_FAR);
        assertThat(generator.generate(request(ScheduleType.ONE_TIME, "2027-01-07", null, "08:00", "10:00", null)))
                .hasSize(1);
        assertError(request(ScheduleType.ONE_TIME, "2026-10-12", null, "08:00", "08:45", null),
                ErrorCode.OCCURRENCE_TOO_SHORT);
        assertThat(generator.generate(request(ScheduleType.ONE_TIME, "2026-10-12", null, "08:00", "09:00", null)))
                .hasSize(1);
    }

    @Test
    void zoneWithDaylightSavingKeepsLocalWallClock() {
        OccurrenceGenerator newYork = newGenerator(ZoneId.of("America/New_York"));
        // DST kết thúc 01/11/2026: 09:00 New York là 13:00Z trước đó và 14:00Z sau đó
        List<OccurrenceSlot> slots = newYork.generate(request(ScheduleType.DAILY, "2026-10-31", "2026-11-01",
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
}
