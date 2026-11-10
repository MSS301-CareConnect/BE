package mss301.bookingservice.mapper;

import mss301.bookingservice.enums.UnitType;
import mss301.bookingservice.client.dto.CareCatalogQuoteRequest;
import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.dto.response.ServiceQuote;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.BookingType;
import mss301.bookingservice.enums.RecurrenceFrequency;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapping không hiển nhiên (AGENTS.md §3.8): nhiều nguồn trùng tên (recipientId, caregiverId, id/status), giá trị dẫn
 * xuất (serviceAddress, endDate của ONE_TIME, thứ trong tuần), field server quản lý không bị chép từ request. Dùng thẳng
 * *MapperImpl, không cần Spring.
 */
class BookingMapperTests {

    OccurrenceMapper occurrenceMapper = new OccurrenceMapperImpl();
    BookingDetailMapper bookingDetailMapper = new BookingDetailMapperImpl();
    BookingMapper bookingMapper = new BookingMapperImpl(occurrenceMapper, bookingDetailMapper);

    static RecipientSnapshot snapshot(UUID recipientId) {
        return RecipientSnapshot.builder()
                .recipientId(recipientId)
                .fullName("Bà Lan")
                .addressLine(" 12 Lý Thường Kiệt ")
                .ward("")
                .district("Hoàn Kiếm")
                .province("Hà Nội")
                .status("ACTIVE")
                .build();
    }

    static BookingCreateRequest request(UUID recipientId, UUID requestedCaregiverId) {
        return BookingCreateRequest.builder()
                .recipientId(recipientId)
                .centerId(UUID.randomUUID())
                .caregiverId(requestedCaregiverId)
                .bookingType(BookingType.RECURRING)
                .frequency(RecurrenceFrequency.WEEKLY)
                .startDate(LocalDate.parse("2026-10-12"))
                .endDate(LocalDate.parse("2026-10-25"))
                .startTime(LocalTime.parse("08:00"))
                .endTime(LocalTime.parse("10:00"))
                .daysOfWeek(Arrays.asList(5, 1, 3, 1))
                .customerNote("Ghi chú")
                .services(List.of(BookingServiceRequest.builder().careServiceId(UUID.randomUUID()).build()))
                .build();
    }

    @Test
    void toBookingTakesCaregiverFromProviderAndDerivesFields() {
        UUID recipientId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID requestedCaregiverId = UUID.randomUUID();
        AssignedCaregiver caregiver = AssignedCaregiver.builder()
                .caregiverId(requestedCaregiverId)
                .caregiverUserId(UUID.randomUUID())
                .build();
        BookingQuote quote = BookingQuote.builder().subtotal(new BigDecimal("100.00")).build();
        RecipientSnapshot recipientSnapshot = snapshot(recipientId);

        Booking booking = bookingMapper.toBooking(request(recipientId, requestedCaregiverId), customerId,
                recipientSnapshot, caregiver, quote, LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-23"), new BigDecimal("30"));

        assertThat(booking.getCustomerId()).isEqualTo(customerId);
        assertThat(booking.getRecipientId()).isEqualTo(recipientId);
        assertThat(booking.getCaregiverId()).isEqualTo(caregiver.getCaregiverId());
        assertThat(booking.getCaregiverUserId()).isEqualTo(caregiver.getCaregiverUserId());
        assertThat(booking.getRecipientSnapshot()).isSameAs(recipientSnapshot);
        // Phần trống bị bỏ, khoảng trắng bị cắt
        assertThat(booking.getServiceAddress()).isEqualTo("12 Lý Thường Kiệt, Hoàn Kiếm, Hà Nội");
        // Thứ trong tuần: sắp xếp, bỏ trùng
        assertThat(booking.getDaysOfWeek()).containsExactly((short) 1, (short) 3, (short) 5);
        assertThat(booking.getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(booking.getBookingType()).isEqualTo(BookingType.RECURRING);
        assertThat(booking.getRecurrenceFrequency()).isEqualTo(RecurrenceFrequency.WEEKLY);
        assertThat(booking.getStartDate()).isEqualTo(LocalDate.parse("2026-10-12"));
        assertThat(booking.getEndDate()).isEqualTo(LocalDate.parse("2026-10-23"));
        assertThat(booking.getInitialPaymentPercent()).isEqualByComparingTo("30");
        // Field server quản lý không được map
        assertThat(booking.getId()).isNull();
        assertThat(booking.getStatus()).isNull();
        assertThat(booking.getBookingCode()).isNull();
        assertThat(booking.getVersion()).isNull();
        assertThat(booking.getIdempotencyKey()).isNull();
    }

    @Test
    void toBookingWithoutCaregiverLeavesBothIdsNullEvenIfRequestHasOne() {
        UUID recipientId = UUID.randomUUID();
        Booking booking = bookingMapper.toBooking(request(recipientId, UUID.randomUUID()), UUID.randomUUID(),
                snapshot(recipientId), null, BookingQuote.builder().build(), LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-23"), new BigDecimal("30"));
        // Không bao giờ lấy caregiver từ request khi chưa xác minh qua Provider
        assertThat(booking.getCaregiverId()).isNull();
        assertThat(booking.getCaregiverUserId()).isNull();
    }

    @Test
    void customTakesDatesFromOccurrencesAndEmptyDaysBecomeNull() {
        UUID recipientId = UUID.randomUUID();
        BookingCreateRequest oneTime = request(recipientId, null);
        oneTime.setBookingType(BookingType.CUSTOM);
        oneTime.setFrequency(null);
        oneTime.setStartDate(null);
        oneTime.setEndDate(null);
        oneTime.setStartTime(null);
        oneTime.setEndTime(null);
        oneTime.setDaysOfWeek(List.of());
        Booking booking = bookingMapper.toBooking(oneTime, UUID.randomUUID(), snapshot(recipientId), null,
                BookingQuote.builder().build(), LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-23"), new BigDecimal("30"));
        assertThat(booking.getStartDate()).isEqualTo(LocalDate.parse("2026-10-12"));
        assertThat(booking.getEndDate()).isEqualTo(LocalDate.parse("2026-10-23"));
        assertThat(booking.getRecurrenceFrequency()).isNull();
        assertThat(booking.getStartTime()).isNull();
        assertThat(booking.getDaysOfWeek()).isNull();
    }

    @Test
    void occurrencesInheritCaregiverAndInitialStatusFromContext() {
        Booking booking = new Booking();
        booking.setCaregiverId(UUID.randomUUID());
        booking.setCaregiverUserId(UUID.randomUUID());
        OccurrenceSlot slot = OccurrenceSlot.builder()
                .occurrenceNo(2)
                .startAt(Instant.parse("2026-10-12T01:00:00Z"))
                .endAt(Instant.parse("2026-10-12T03:00:00Z"))
                .durationMinutes(120)
                .build();
        List<BookingOccurrence> occurrences = occurrenceMapper.toOccurrenceList(List.of(slot), booking,
                OccurrenceStatus.SCHEDULED);
        BookingOccurrence occurrence = occurrences.getFirst();
        assertThat(occurrence.getBooking()).isSameAs(booking);
        assertThat(occurrence.getOccurrenceNo()).isEqualTo(2);
        assertThat(occurrence.getCaregiverId()).isEqualTo(booking.getCaregiverId());
        assertThat(occurrence.getCaregiverUserId()).isEqualTo(booking.getCaregiverUserId());
        assertThat(occurrence.getStatus()).isEqualTo(OccurrenceStatus.SCHEDULED);
        assertThat(occurrence.getId()).isNull();
        assertThat(occurrence.getCaregiverScheduleId()).isNull();
        assertThat(occurrenceMapper.toOccurrenceList(null, booking, OccurrenceStatus.SCHEDULED)).isEmpty();
    }

    @Test
    void catalogRequestCarriesOnlyIdsAndDetailKeepsComputedUnits() {
        BookingServiceRequest service = BookingServiceRequest.builder()
                .careServiceId(UUID.randomUUID())
                .centerServiceId(UUID.randomUUID())
                .pricePlanId(UUID.randomUUID())
                .build();
        UUID centerId = UUID.randomUUID();
        CareCatalogQuoteRequest catalogRequest = bookingDetailMapper.toCatalogQuoteRequest(centerId, List.of(service));
        assertThat(catalogRequest.getCenterId()).isEqualTo(centerId);
        assertThat(catalogRequest.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getPricePlanId()).isEqualTo(service.getPricePlanId()));
        ServiceQuote quote = ServiceQuote.builder()
                .careServiceId(service.getCareServiceId())
                .centerServiceId(service.getCenterServiceId())
                .pricePlanId(service.getPricePlanId())
                .serviceName("Tắm gội")
                .unitType(UnitType.HOUR)
                .unitPrice(new BigDecimal("100000"))
                .unitsPerOccurrence(new BigDecimal("1.5"))
                .quantity(new BigDecimal("4.5"))
                .totalPrice(new BigDecimal("900000"))
                .build();
        Booking booking = new Booking();
        BookingDetail detail = bookingDetailMapper.toBookingDetailList(List.of(quote), booking).getFirst();
        assertThat(detail.getBooking()).isSameAs(booking);
        assertThat(detail.getCenterServiceId()).isEqualTo(service.getCenterServiceId());
        assertThat(detail.getUnitsPerOccurrence()).isEqualByComparingTo("1.5");
        assertThat(detail.getQuantity()).isEqualByComparingTo("4.5");
        assertThat(detail.getId()).isNull();
    }

    @Test
    void responsesPickBookingIdAndStatusAndOptionalCustomer() {
        UUID recipientId = UUID.randomUUID();
        Booking booking = bookingMapper.toBooking(request(recipientId, null), UUID.randomUUID(), snapshot(recipientId),
                null, BookingQuote.builder().build(), LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-23"), new BigDecimal("30"));
        booking.setId(UUID.randomUUID());
        booking.setStatus(BookingStatus.PENDING_CENTER_CONFIRMATION);
        IdentityUserResponse customer = IdentityUserResponse.builder()
                .id(booking.getCustomerId())
                .fullName("Khách Hàng")
                .phoneNumber("0987654321")
                .status("ACTIVE")
                .roles(Set.of("CUSTOMER"))
                .build();

        BookingDetailResponse detail = bookingMapper.toDetailResponse(booking, List.of(), List.of(), customer, null);
        assertThat(detail.getId()).isEqualTo(booking.getId());
        assertThat(detail.getStatus()).isEqualTo(BookingStatus.PENDING_CENTER_CONFIRMATION);
        assertThat(detail.getCustomer().getFullName()).isEqualTo("Khách Hàng");
        assertThat(detail.getDaysOfWeek()).containsExactly(1, 3, 5);
        assertThat(bookingMapper.toDetailResponse(booking, List.of(), List.of(), null, null).getCustomer()).isNull();

        List<BookingSummaryResponse> withCustomer = bookingMapper.toSummaryResponseList(List.of(booking),
                Map.of(booking.getCustomerId(), customer), Map.of(), Map.of());
        assertThat(withCustomer.getFirst().getRecipientName()).isEqualTo("Bà Lan");
        // Không có thống kê buổi (booking không còn buổi) → 0, không NPE.
        assertThat(withCustomer.getFirst().getTotalCount()).isZero();
        assertThat(withCustomer.getFirst().getNextOccurrenceAt()).isNull();
        assertThat(withCustomer.getFirst().getCustomer().getPhoneNumber()).isEqualTo("0987654321");
        assertThat(bookingMapper.toSummaryResponseList(List.of(booking), Map.of(), Map.of(), Map.of()).getFirst().getCustomer()).isNull();
    }

    @Test
    void caregiverListOmitsSnapshotButDetailIncludesIt() {
        UUID recipientId = UUID.randomUUID();
        Booking booking = bookingMapper.toBooking(request(recipientId, null), UUID.randomUUID(), snapshot(recipientId),
                null, BookingQuote.builder().build(), LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-23"), new BigDecimal("30"));
        booking.setId(UUID.randomUUID());
        booking.setCheckInEarlyMinutes(15);
        booking.setCheckInLateMinutes(45);
        BookingOccurrence occurrence = new BookingOccurrence();
        occurrence.setId(UUID.randomUUID());
        occurrence.setBooking(booking);
        occurrence.setStartAt(Instant.parse("2026-10-12T01:00:00Z"));
        assertThat(occurrenceMapper.toCaregiverResponseList(List.of(occurrence)).getFirst().getRecipientSnapshot()).isNull();
        // Q12: cửa sổ check-in theo policy chụp trên booking.
        assertThat(occurrenceMapper.toCaregiverResponse(occurrence).getCheckInOpensAt())
                .isEqualTo(Instant.parse("2026-10-12T00:45:00Z"));
        assertThat(occurrenceMapper.toCaregiverResponse(occurrence).getCheckInClosesAt())
                .isEqualTo(Instant.parse("2026-10-12T01:45:00Z"));
        assertThat(occurrenceMapper.toCaregiverResponseList(List.of(occurrence)).getFirst().getBookingId())
                .isEqualTo(booking.getId());
        assertThat(occurrenceMapper.toCaregiverDetailResponse(occurrence).getRecipientSnapshot().getFullName())
                .isEqualTo("Bà Lan");
        assertThat(occurrenceMapper.toCaregiverDetailResponse(occurrence).getRecipientId()).isEqualTo(recipientId);
    }

    @Test
    void responseDtosNeverExposeIdempotencyOrInternalFields() {
        for (Class<?> type : List.of(BookingDetailResponse.class, BookingSummaryResponse.class)) {
            List<String> fieldNames = Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
            assertThat(fieldNames).doesNotContain("idempotencyKey", "idempotencyHash", "acceptedBy", "rejectedBy");
        }
    }
}
