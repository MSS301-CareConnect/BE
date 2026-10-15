package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.ReasonRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.BookingStatus;

import java.util.List;
import java.util.UUID;

// Booking của khách hàng (customerId = sub). Booking không phải của mình → 404 BOOKING_NOT_FOUND.
public interface BookingService {
    APIResponse<BookingDetailResponse> create(UUID actorId, String idempotencyKey, BookingCreateRequest request);

    APIResponse<PageResponse<BookingSummaryResponse>> list(UUID actorId, BookingStatus status, UUID recipientId,
                                                           int page, int size);

    APIResponse<BookingDetailResponse> get(UUID actorId, UUID id);

    APIResponse<List<OccurrenceResponse>> listOccurrences(UUID actorId, UUID id);

    APIResponse<BookingDetailResponse> cancel(UUID actorId, UUID id, ReasonRequest request);
}
