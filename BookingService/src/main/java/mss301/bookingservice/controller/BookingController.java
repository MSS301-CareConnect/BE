package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.AcceptRequest;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.CaregiverAssignRequest;
import mss301.bookingservice.dto.request.DepositPaidRequest;
import mss301.bookingservice.dto.request.ReasonRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
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
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Booking", description = "Booking domain — tạo / xem / thao tác lịch chăm sóc (Customer, Center Manager, Admin, Inter-service)")
public class BookingController {

    BookingService bookingService;

    // ─── CUSTOMER ENDPOINTS ───────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Tạo booking mới (Customer)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> create(@CurrentUserId UUID actorId,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                     @Valid @RequestBody BookingCreateRequest request) {
        return bookingService.create(actorId, idempotencyKey, request);
    }

    @GetMapping
    @Operation(summary = "Danh sách booking của khách hàng (Customer)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<PageResponse<BookingSummaryResponse>> listByCustomer(@CurrentUserId UUID actorId,
                                                                            @RequestParam(required = false) BookingStatus status,
                                                                            @RequestParam(required = false) UUID recipientId,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return bookingService.listByCustomer(actorId, status, recipientId, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết booking của khách hàng (Customer)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> getByCustomer(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return bookingService.getByCustomer(actorId, id);
    }

    @GetMapping("/{id}/occurrences")
    @Operation(summary = "Danh sách ca/buổi chăm sóc của booking (Customer)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<OccurrenceResponse>> listOccurrences(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return bookingService.listOccurrences(actorId, id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy booking (Customer)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> cancelByCustomer(@CurrentUserId UUID actorId,
                                                               @PathVariable UUID id,
                                                               @Valid @RequestBody ReasonRequest request) {
        return bookingService.cancelByCustomer(actorId, id, request);
    }

    // ─── CENTER MANAGER ENDPOINTS ─────────────────────────────────────────────

    @GetMapping("/center")
    @Operation(summary = "Danh sách booking của trung tâm (Center Manager)")
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

    @GetMapping("/center/{id}")
    @Operation(summary = "Chi tiết booking của trung tâm (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> getByCenter(@CurrentCenterId UUID centerId, @PathVariable UUID id) {
        return bookingService.getByCenter(centerId, id);
    }

    @PostMapping("/center/{id}/accept")
    @Operation(summary = "Chấp nhận booking (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> accept(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody(required = false) AcceptRequest request) {
        return bookingService.accept(actorId, centerId, id, request);
    }

    @PostMapping("/center/{id}/reject")
    @Operation(summary = "Từ chối booking (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> reject(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody ReasonRequest request) {
        return bookingService.reject(actorId, centerId, id, request);
    }

    @PatchMapping("/center/{id}/caregiver")
    @Operation(summary = "Phân công / đổi caregiver cho booking (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> updateCaregiver(@CurrentUserId UUID actorId,
                                                              @CurrentCenterId UUID centerId,
                                                              @PathVariable UUID id,
                                                              @Valid @RequestBody CaregiverAssignRequest request) {
        return bookingService.updateCaregiver(actorId, centerId, id, request);
    }

    @PatchMapping("/center/occurrences/{occurrenceId}/caregiver")
    @Operation(summary = "Phân công / đổi caregiver riêng cho 1 ca chăm sóc (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(@CurrentUserId UUID actorId,
                                                                     @CurrentCenterId UUID centerId,
                                                                     @PathVariable UUID occurrenceId,
                                                                     @Valid @RequestBody CaregiverAssignRequest request) {
        return bookingService.updateOccurrenceCaregiver(actorId, centerId, occurrenceId, request);
    }

    @PostMapping("/center/{id}/cancel")
    @Operation(summary = "Trung tâm hủy booking (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> cancelByCenter(@CurrentUserId UUID actorId,
                                                             @CurrentCenterId UUID centerId,
                                                             @PathVariable UUID id,
                                                             @Valid @RequestBody ReasonRequest request) {
        return bookingService.cancelByCenter(actorId, centerId, id, request);
    }

    // ─── ADMIN ENDPOINTS ──────────────────────────────────────────────────────

    @GetMapping("/admin")
    @Operation(summary = "Xem toàn bộ booking trong hệ thống (Admin)")
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

    @GetMapping("/admin/{id}")
    @Operation(summary = "Chi tiết booking bất kỳ (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<BookingDetailResponse> getAny(@PathVariable UUID id) {
        return bookingService.getAny(id);
    }

    // ─── INTER-SERVICE & SHARED ENDPOINTS ─────────────────────────────────────

    @GetMapping("/caregiver-access")
    @Operation(summary = "Kiểm tra caregiver có được phân công cho người bệnh không (Inter-service / Authenticated)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CaregiverAccessResponse> checkCaregiverAccess(@RequestParam UUID caregiverUserId,
                                                                     @RequestParam UUID recipientId) {
        return bookingService.checkCaregiverAccess(caregiverUserId, recipientId);
    }

    @PostMapping("/{id}/deposit-paid")
    @Operation(summary = "Xác nhận đặt cọc thành công (Inter-service / Authenticated)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<BookingDetailResponse> markDepositPaid(@PathVariable UUID id,
                                                              @Valid @RequestBody(required = false) DepositPaidRequest request) {
        return bookingService.markDepositPaid(id, request);
    }
}
