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
import mss301.bookingservice.service.CenterBookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/center/bookings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Center - Booking", description = "Booking của trung tâm (center_id lấy từ token CENTER_MANAGER). Booking trung tâm khác → 404.")
public class CenterBookingController {
    CenterBookingService centerBookingService;

    @GetMapping
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PageResponse<BookingSummaryResponse>> list(@CurrentCenterId UUID centerId,
                                                                  @RequestParam(required = false) BookingStatus status,
                                                                  @RequestParam(required = false) UUID caregiverId,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return centerBookingService.list(centerId, status, caregiverId, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> get(@CurrentCenterId UUID centerId, @PathVariable UUID id) {
        return centerBookingService.get(centerId, id);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> accept(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody(required = false) AcceptRequest request) {
        return centerBookingService.accept(actorId, centerId, id, request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> reject(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody ReasonRequest request) {
        return centerBookingService.reject(actorId, centerId, id, request);
    }

    @PatchMapping("/{id}/caregiver")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> updateCaregiver(@CurrentUserId UUID actorId,
                                                              @CurrentCenterId UUID centerId,
                                                              @PathVariable UUID id,
                                                              @Valid @RequestBody CaregiverAssignRequest request) {
        return centerBookingService.updateCaregiver(actorId, centerId, id, request);
    }

    @PatchMapping("/occurrences/{occurrenceId}/caregiver")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(@CurrentUserId UUID actorId,
                                                                     @CurrentCenterId UUID centerId,
                                                                     @PathVariable UUID occurrenceId,
                                                                     @Valid @RequestBody CaregiverAssignRequest request) {
        return centerBookingService.updateOccurrenceCaregiver(actorId, centerId, occurrenceId, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> cancel(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody ReasonRequest request) {
        return centerBookingService.cancel(actorId, centerId, id, request);
    }
}
