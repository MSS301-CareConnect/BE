package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.*;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.BookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Booking", description = "Booking domain — tạo / xem / thao tác lịch chăm sóc. Quyền phân theo role: CUSTOMER, CENTER_MANAGER, ADMIN.")
public class BookingController {
    BookingService bookingService;

    // ─── CUSTOMER ─────────────────────────────────────────────────────────────

    @PostMapping("/bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> create(@CurrentUserId UUID actorId,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                     @Valid @RequestBody BookingCreateRequest request) {
        return bookingService.create(actorId, idempotencyKey, request);
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<PageResponse<BookingSummaryResponse>> listByCustomer(@CurrentUserId UUID actorId,
                                                                            @RequestParam(required = false) BookingStatus status,
                                                                            @RequestParam(required = false) UUID recipientId,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return bookingService.listByCustomer(actorId, status, recipientId, page, size);
    }

    @GetMapping("/bookings/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> getByCustomer(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return bookingService.getByCustomer(actorId, id);
    }

    @GetMapping("/bookings/{id}/occurrences")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<OccurrenceResponse>> listOccurrences(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return bookingService.listOccurrences(actorId, id);
    }

    @PostMapping("/bookings/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> cancelByCustomer(@CurrentUserId UUID actorId,
                                                               @PathVariable UUID id,
                                                               @Valid @RequestBody ReasonRequest request) {
        return bookingService.cancelByCustomer(actorId, id, request);
    }

    // ─── CENTER_MANAGER ───────────────────────────────────────────────────────

    @GetMapping("/center/bookings")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PageResponse<BookingSummaryResponse>> listByCenter(@CurrentCenterId UUID centerId,
                                                                          @RequestParam(required = false) BookingStatus status,
                                                                          @RequestParam(required = false) UUID caregiverId,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        return bookingService.listByCenter(centerId, status, caregiverId, from, to, page, size);
    }

    @GetMapping("/center/bookings/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> getByCenter(@CurrentCenterId UUID centerId, @PathVariable UUID id) {
        return bookingService.getByCenter(centerId, id);
    }

    @PostMapping("/center/bookings/{id}/accept")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> accept(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody(required = false) AcceptRequest request) {
        return bookingService.accept(actorId, centerId, id, request);
    }

    @PostMapping("/center/bookings/{id}/reject")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> reject(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody ReasonRequest request) {
        return bookingService.reject(actorId, centerId, id, request);
    }

    @PatchMapping("/center/bookings/{id}/caregiver")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> updateCaregiver(@CurrentUserId UUID actorId,
                                                              @CurrentCenterId UUID centerId,
                                                              @PathVariable UUID id,
                                                              @Valid @RequestBody CaregiverAssignRequest request) {
        return bookingService.updateCaregiver(actorId, centerId, id, request);
    }

    @PatchMapping("/center/bookings/occurrences/{occurrenceId}/caregiver")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(@CurrentUserId UUID actorId,
                                                                     @CurrentCenterId UUID centerId,
                                                                     @PathVariable UUID occurrenceId,
                                                                     @Valid @RequestBody CaregiverAssignRequest request) {
        return bookingService.updateOccurrenceCaregiver(actorId, centerId, occurrenceId, request);
    }

    @PostMapping("/center/bookings/{id}/cancel")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> cancelByCenter(@CurrentUserId UUID actorId,
                                                             @CurrentCenterId UUID centerId,
                                                             @PathVariable UUID id,
                                                             @Valid @RequestBody ReasonRequest request) {
        return bookingService.cancelByCenter(actorId, centerId, id, request);
    }

    // ─── ADMIN ────────────────────────────────────────────────────────────────

    @GetMapping("/admin/bookings")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<PageResponse<BookingSummaryResponse>> listAll(@RequestParam(required = false) BookingStatus status,
                                                                     @RequestParam(required = false) UUID centerId,
                                                                     @RequestParam(required = false) UUID customerId,
                                                                     @RequestParam(required = false) UUID recipientId,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return bookingService.listAll(status, centerId, customerId, recipientId, from, to, page, size);
    }

    @GetMapping("/admin/bookings/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<BookingDetailResponse> getAny(@PathVariable UUID id) {
        return bookingService.getAny(id);
    }
}
