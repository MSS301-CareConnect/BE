package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.AcceptRequest;
import mss301.bookingservice.dto.request.CaregiverAssignRequest;
import mss301.bookingservice.dto.request.ReasonRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.BookingStatus;

import java.time.LocalDate;
import java.util.UUID;

// Booking của trung tâm (centerId = claim center_id của CENTER_MANAGER). Booking trung tâm khác → 404.
public interface CenterBookingService {
    APIResponse<PageResponse<BookingSummaryResponse>> list(UUID centerId, BookingStatus status, UUID caregiverId,
                                                           LocalDate from, LocalDate to, int page, int size);

    APIResponse<BookingDetailResponse> get(UUID centerId, UUID id);

    APIResponse<BookingDetailResponse> accept(UUID actorId, UUID centerId, UUID id, AcceptRequest request);

    APIResponse<BookingDetailResponse> reject(UUID actorId, UUID centerId, UUID id, ReasonRequest request);

    APIResponse<BookingDetailResponse> updateCaregiver(UUID actorId, UUID centerId, UUID id, CaregiverAssignRequest request);

    APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(UUID actorId, UUID centerId, UUID occurrenceId,
                                                              CaregiverAssignRequest request);

    APIResponse<BookingDetailResponse> cancel(UUID actorId, UUID centerId, UUID id, ReasonRequest request);
}
