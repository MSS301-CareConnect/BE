package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.AcceptRequest;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.CaregiverAssignRequest;
import mss301.bookingservice.dto.request.DepositPaidRequest;
import mss301.bookingservice.dto.request.ReasonRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.BookingStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Booking domain: tạo / xem / thao tác booking cho mọi role.
 * Phân quyền (CUSTOMER / CENTER_MANAGER / ADMIN) do controller kiểm bằng @PreAuthorize.
 */
public interface BookingService {

    // ─── CUSTOMER ────────────────────────────────────────────────────────────

    // Booking không phải của mình → 404 BOOKING_NOT_FOUND.
    APIResponse<BookingDetailResponse> create(UUID actorId, String idempotencyKey, BookingCreateRequest request);

    APIResponse<PageResponse<BookingSummaryResponse>> listByCustomer(UUID actorId, BookingStatus status, UUID recipientId,
                                                                     int page, int size);

    APIResponse<BookingDetailResponse> getByCustomer(UUID actorId, UUID id);

    APIResponse<List<OccurrenceResponse>> listOccurrences(UUID actorId, UUID id);

    APIResponse<BookingDetailResponse> cancelByCustomer(UUID actorId, UUID id, ReasonRequest request);

    // ─── CENTER_MANAGER ───────────────────────────────────────────────────────

    // Booking của trung tâm khác → 404.
    APIResponse<PageResponse<BookingSummaryResponse>> listByCenter(UUID centerId, BookingStatus status, UUID caregiverId,
                                                                   LocalDate from, LocalDate to, int page, int size);

    APIResponse<BookingDetailResponse> getByCenter(UUID centerId, UUID id);

    APIResponse<BookingDetailResponse> accept(UUID actorId, UUID centerId, UUID id, AcceptRequest request);

    APIResponse<BookingDetailResponse> reject(UUID actorId, UUID centerId, UUID id, ReasonRequest request);

    APIResponse<BookingDetailResponse> updateCaregiver(UUID actorId, UUID centerId, UUID id, CaregiverAssignRequest request);

    APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(UUID actorId, UUID centerId, UUID occurrenceId,
                                                              CaregiverAssignRequest request);

    APIResponse<BookingDetailResponse> cancelByCenter(UUID actorId, UUID centerId, UUID id, ReasonRequest request);

    // ─── ADMIN ───────────────────────────────────────────────────────────────

    // ADMIN xem toàn bộ, chỉ đọc.
    APIResponse<PageResponse<BookingSummaryResponse>> listAll(BookingStatus status, UUID centerId, UUID customerId,
                                                             UUID recipientId, LocalDate from, LocalDate to,
                                                             int page, int size);

    APIResponse<BookingDetailResponse> getAny(UUID id);

    // ─── INTER-SERVICE / SHARED ───────────────────────────────────────────────

    APIResponse<CaregiverAccessResponse> checkCaregiverAccess(UUID caregiverUserId, UUID recipientId);

    APIResponse<BookingDetailResponse> markDepositPaid(UUID id, DepositPaidRequest request);

    // ─── SCHEDULER ───────────────────────────────────────────────────────────

    List<UUID> findExpirableIds();

    void expire(UUID id);

    // ─── CAREGIVER OCCURRENCES ────────────────────────────────────────────────
    APIResponse<List<CaregiverOccurrenceResponse>> listCaregiverOccurrences(UUID actorId, LocalDate from, LocalDate to,
                                                                             OccurrenceStatus status);

    APIResponse<CaregiverOccurrenceResponse> getCaregiverOccurrence(UUID actorId, UUID id);

    APIResponse<CaregiverOccurrenceResponse> getCaregiverOccurrenceById(UUID id);
}
