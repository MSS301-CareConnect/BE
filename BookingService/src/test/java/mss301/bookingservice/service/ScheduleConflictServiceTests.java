package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.service.Impl.ScheduleConflictServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleConflictServiceTests {

    @Mock
    BookingRepository bookingRepository;

    @Mock
    BookingOccurrenceRepository bookingOccurrenceRepository;

    @InjectMocks
    ScheduleConflictServiceImpl scheduleConflictService;

    static OccurrenceSlot slot(String start, String end) {
        return OccurrenceSlot.builder()
                .occurrenceNo(1)
                .startAt(Instant.parse(start))
                .endAt(Instant.parse(end))
                .durationMinutes(60)
                .build();
    }

    static BookingOccurrence existing(UUID bookingId, String start, String end) {
        Booking booking = new Booking();
        booking.setId(bookingId);
        BookingOccurrence occurrence = new BookingOccurrence();
        occurrence.setBooking(booking);
        occurrence.setStartAt(Instant.parse(start));
        occurrence.setEndAt(Instant.parse(end));
        occurrence.setStatus(OccurrenceStatus.SCHEDULED);
        return occurrence;
    }

    @Test
    void overlappingCaregiverSlotIsRejected() {
        UUID caregiverId = UUID.randomUUID();
        when(bookingOccurrenceRepository.findCaregiverOverlaps(eq(caregiverId), any(), any(), any(), any()))
                .thenReturn(List.of(existing(UUID.randomUUID(), "2026-10-12T01:30:00Z", "2026-10-12T02:30:00Z")));
        assertThatThrownBy(() -> scheduleConflictService.checkCaregiverAvailable(caregiverId,
                List.of(slot("2026-10-12T01:00:00Z", "2026-10-12T02:00:00Z")), null))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CAREGIVER_SCHEDULE_CONFLICT);
    }

    @Test
    void touchingBoundariesAreNotConflicts() {
        UUID caregiverId = UUID.randomUUID();
        // Khoảng bao [01:00, 05:00) trả về ứng viên, nhưng không ứng viên nào giao CHÍNH XÁC với từng slot
        when(bookingOccurrenceRepository.findCaregiverOverlaps(eq(caregiverId), any(), any(), any(), any()))
                .thenReturn(List.of(
                        existing(UUID.randomUUID(), "2026-10-12T02:00:00Z", "2026-10-12T04:00:00Z"),
                        existing(UUID.randomUUID(), "2026-10-12T00:00:00Z", "2026-10-12T01:00:00Z")));
        assertThatCode(() -> scheduleConflictService.checkCaregiverAvailable(caregiverId, List.of(
                slot("2026-10-12T01:00:00Z", "2026-10-12T02:00:00Z"),
                slot("2026-10-12T04:00:00Z", "2026-10-12T05:00:00Z")), null))
                .doesNotThrowAnyException();
    }

    @Test
    void ownBookingIsExcludedWhenReassigning() {
        UUID caregiverId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(bookingOccurrenceRepository.findCaregiverOverlaps(eq(caregiverId), any(), any(), any(), any()))
                .thenReturn(List.of(existing(bookingId, "2026-10-12T01:00:00Z", "2026-10-12T02:00:00Z")));
        assertThatCode(() -> scheduleConflictService.checkCaregiverAvailable(caregiverId,
                List.of(slot("2026-10-12T01:00:00Z", "2026-10-12T02:00:00Z")), bookingId))
                .doesNotThrowAnyException();
    }

    @Test
    void queryUsesBoundingRangeAndActiveStatusesOnly() {
        UUID recipientId = UUID.randomUUID();
        when(bookingOccurrenceRepository.findRecipientOverlaps(any(), any(), any(), any(), any())).thenReturn(List.of());
        scheduleConflictService.checkRecipientAvailable(recipientId, List.of(
                slot("2026-10-14T01:00:00Z", "2026-10-14T02:00:00Z"),
                slot("2026-10-12T01:00:00Z", "2026-10-12T02:00:00Z")), null);
        ArgumentCaptor<Instant> rangeStart = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> rangeEnd = ArgumentCaptor.forClass(Instant.class);
        verify(bookingOccurrenceRepository).findRecipientOverlaps(eq(recipientId), rangeStart.capture(),
                rangeEnd.capture(), eq(OccurrenceStatus.ACTIVE), eq(BookingStatus.SCHEDULE_HOLDING));
        assertThat(rangeStart.getValue()).isEqualTo(Instant.parse("2026-10-12T01:00:00Z"));
        assertThat(rangeEnd.getValue()).isEqualTo(Instant.parse("2026-10-14T02:00:00Z"));
    }

    @Test
    void recipientConflictUsesOwnErrorCode() {
        UUID recipientId = UUID.randomUUID();
        when(bookingOccurrenceRepository.findRecipientOverlaps(eq(recipientId), any(), any(), any(), any()))
                .thenReturn(List.of(existing(UUID.randomUUID(), "2026-10-12T00:00:00Z", "2026-10-12T05:00:00Z")));
        assertThatThrownBy(() -> scheduleConflictService.checkRecipientAvailable(recipientId,
                List.of(slot("2026-10-12T01:00:00Z", "2026-10-12T02:00:00Z")), null))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECIPIENT_SCHEDULE_CONFLICT);
    }

    @Test
    void noCaregiverOrNoSlotsSkipsQuery() {
        scheduleConflictService.checkCaregiverAvailable(null, List.of(slot("2026-10-12T01:00:00Z", "2026-10-12T02:00:00Z")), null);
        scheduleConflictService.checkCaregiverAvailable(UUID.randomUUID(), List.of(), null);
        verifyNoInteractions(bookingOccurrenceRepository);
    }

    @Test
    void locksCaregiverBeforeRecipient() {
        UUID caregiverId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        scheduleConflictService.lock(caregiverId, recipientId);
        var order = inOrder(bookingRepository);
        order.verify(bookingRepository).lockScheduleKey("booking:caregiver:" + caregiverId);
        order.verify(bookingRepository).lockScheduleKey("booking:recipient:" + recipientId);
    }

    @Test
    void lockSkipsMissingCaregiver() {
        UUID recipientId = UUID.randomUUID();
        scheduleConflictService.lock(null, recipientId);
        verify(bookingRepository).lockScheduleKey("booking:recipient:" + recipientId);
        verify(bookingRepository, never()).lockScheduleKey(startsWith("booking:caregiver:"));
        verify(bookingRepository).lockScheduleKey(anyString());
    }
}
