package mss301.bookingservice.controller;

import mss301.bookingservice.dto.response.RecipientActivityResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.*;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingHistoryResponse;
import mss301.bookingservice.dto.response.BookingQuoteResponse;
import mss301.bookingservice.dto.response.BookingStatusCountsResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.CancellationPreviewResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.CaregiverBookingRequestResponse;
import mss301.bookingservice.dto.response.CaregiverHeldScheduleResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.dto.response.PaymentContextResponse;
import mss301.bookingservice.dto.response.SystemCancellationResponse;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.BookingService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Booking", description = "Booking — một API cho mọi role; phạm vi dữ liệu theo role (Customer, Center Manager, Admin, Service)")
public class BookingController {

    BookingService bookingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<PageResponse<BookingSummaryResponse>> list(@CurrentUserId UUID actorId,
                                                                  @CurrentRoles Set<RoleCode> roles,
                                                                  @CurrentCenterId(required = false) UUID scopeCenterId,
                                                                  @ParameterObject BookingSearchRequest request) {
        return bookingService.list(actorId, roles, scopeCenterId, request);
    }

    @GetMapping("/status-counts")
    @PreAuthorize("hasAnyRole('CENTER_MANAGER', 'ADMIN')")
    public APIResponse<BookingStatusCountsResponse> countByStatus(@CurrentRoles Set<RoleCode> roles,
                                                                  @CurrentCenterId(required = false) UUID scopeCenterId,
                                                                  @RequestParam(required = false) UUID centerId) {
        return bookingService.countByStatus(roles, scopeCenterId, centerId);
    }

    // Spec §3: khách chọn lịch SHIFT — lịch Provider đã bị booking giữ thì giao diện khóa lựa chọn.
    @GetMapping("/held-schedules")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<CaregiverHeldScheduleResponse> listHeldSchedules(
            @RequestParam UUID caregiverId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bookingService.listHeldSchedules(caregiverId, from, to);
    }

    // CR-3: đơn khách gửi thẳng cho caregiver đang chờ trả lời (khu vực + tình trạng sức khỏe, không số nhà / SĐT).
    @GetMapping("/caregiver-requests")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<PageResponse<CaregiverBookingRequestResponse>> listCaregiverRequests(
            @CurrentUserId UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return bookingService.listCaregiverRequests(actorId, page, size);
    }

    @GetMapping("/caregiver-requests/{id}")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CaregiverBookingRequestResponse> getCaregiverRequest(@CurrentUserId UUID actorId,
                                                                           @PathVariable UUID id) {
        return bookingService.getCaregiverRequest(actorId, id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<BookingDetailResponse> get(@CurrentUserId UUID actorId,
                                                  @CurrentRoles Set<RoleCode> roles,
                                                  @CurrentCenterId(required = false) UUID scopeCenterId,
                                                  @PathVariable UUID id) {
        return bookingService.get(actorId, roles, scopeCenterId, id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<PageResponse<BookingHistoryResponse>> listHistory(@CurrentUserId UUID actorId,
                                                                         @CurrentRoles Set<RoleCode> roles,
                                                                         @CurrentCenterId(required = false) UUID scopeCenterId,
                                                                         @PathVariable UUID id,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "20") int size) {
        return bookingService.listHistory(actorId, roles, scopeCenterId, id, page, size);
    }

    @GetMapping("/{id}/occurrences")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<PageResponse<OccurrenceResponse>> listOccurrences(@CurrentUserId UUID actorId,
                                                                         @CurrentRoles Set<RoleCode> roles,
                                                                         @CurrentCenterId(required = false) UUID scopeCenterId,
                                                                         @PathVariable UUID id,
                                                                         @RequestParam(required = false) OccurrenceStatus status,
                                                                         @RequestParam(defaultValue = "0") int page,
                                                                         @RequestParam(defaultValue = "50") int size) {
        return bookingService.listOccurrences(actorId, roles, scopeCenterId, id, status, page, size);
    }

    @GetMapping("/{id}/payment-context")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<PaymentContextResponse> getPaymentContext(@PathVariable UUID id) {
        return bookingService.getPaymentContext(id);
    }

    // CareRecipientService hỏi bằng token SERVICE (C7.2) — người dùng thường không dò được quan hệ caregiver ↔ người bệnh.
    @GetMapping("/caregiver-access")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<CaregiverAccessResponse> checkCaregiverAccess(@RequestParam UUID caregiverUserId,
                                                                     @RequestParam UUID recipientId) {
        return bookingService.checkCaregiverAccess(caregiverUserId, recipientId);
    }

    // M-07 (token SERVICE): CareRecipientService chặn tạm ngưng / lưu trữ hồ sơ khi người bệnh còn booking chưa kết thúc.
    @GetMapping("/recipient-activity")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<RecipientActivityResponse> getRecipientActivity(@RequestParam UUID recipientId) {
        return bookingService.getRecipientActivity(recipientId);
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> create(@CurrentUserId UUID actorId,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                     @Valid @RequestBody BookingCreateRequest request) {
        return bookingService.create(actorId, idempotencyKey, request);
    }

    @PostMapping("/quotes")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingQuoteResponse> quote(@CurrentUserId UUID actorId,
                                                   @Valid @RequestBody BookingQuoteRequest request) {
        return bookingService.quote(actorId, request);
    }

    @PostMapping("/system-cancellations")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<SystemCancellationResponse> cancelBySystem(@Valid @RequestBody SystemCancellationRequest request) {
        return bookingService.cancelBySystem(request);
    }

    @PostMapping("/{id}/cancellation-previews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CancellationPreviewResponse> previewCancellation(@CurrentUserId UUID actorId,
                                                                        @PathVariable UUID id,
                                                                        @Valid @RequestBody(required = false) CancellationPreviewRequest request) {
        return bookingService.previewCancellation(actorId, id, request);
    }

    // M-11: admin hủy thay mặt nền tảng (hoàn 100 % như trung tâm hủy, ghi nhận bên hủy ADMIN).
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<BookingDetailResponse> cancel(@CurrentUserId UUID actorId,
                                                     @CurrentRoles Set<RoleCode> roles,
                                                     @CurrentCenterId(required = false) UUID scopeCenterId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody BookingCancelRequest request) {
        return bookingService.cancel(actorId, roles, scopeCenterId, id, request);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> accept(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody(required = false) AcceptRequest request) {
        return bookingService.accept(actorId, centerId, id, request);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> reject(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody ReasonRequest request) {
        return bookingService.reject(actorId, centerId, id, request);
    }

    // CR-3: caregiver khách đã chọn nhận đơn — không cần trung tâm duyệt.
    @PostMapping("/{id}/caregiver-accept")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CaregiverBookingRequestResponse> acceptAsCaregiver(@CurrentUserId UUID actorId,
                                                                          @PathVariable UUID id) {
        return bookingService.acceptAsCaregiver(actorId, id);
    }

    // CR-3: caregiver từ chối — RECURRING / CUSTOM chuyển trung tâm phân người khác, SHIFT bị từ chối.
    @PostMapping("/{id}/caregiver-decline")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CaregiverBookingRequestResponse> declineAsCaregiver(@CurrentUserId UUID actorId,
                                                                           @PathVariable UUID id,
                                                                           @Valid @RequestBody ReasonRequest request) {
        return bookingService.declineAsCaregiver(actorId, id, request);
    }

    // Chỉ PaymentService (token SERVICE) xác nhận đã cọc — người dùng không tự đánh dấu đã trả tiền.
    @PostMapping("/{id}/deposit-paid")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<BookingDetailResponse> markDepositPaid(@PathVariable UUID id,
                                                              @Valid @RequestBody(required = false) DepositPaidRequest request) {
        return bookingService.markDepositPaid(id, request);
    }

    // PaymentService báo khách đã trả xong phần còn lại trước buổi đầu (spec §9.1).
    @PostMapping("/{id}/remaining-paid")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<BookingDetailResponse> markRemainingPaid(@PathVariable UUID id,
                                                                @Valid @RequestBody(required = false) DepositPaidRequest request) {
        return bookingService.markRemainingPaid(id, request);
    }

    // PaymentService báo quá hạn trả phần còn lại → booking EXPIRED (spec §11.1, §15).
    @PostMapping("/{id}/payment-overdue")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<BookingDetailResponse> markPaymentOverdue(@PathVariable UUID id) {
        return bookingService.markPaymentOverdue(id);
    }

    @PatchMapping("/{id}/caregiver")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> updateCaregiver(@CurrentUserId UUID actorId,
                                                              @CurrentCenterId UUID centerId,
                                                              @PathVariable UUID id,
                                                              @Valid @RequestBody CaregiverAssignRequest request) {
        return bookingService.updateCaregiver(actorId, centerId, id, request);
    }

    // CR-2: khách hoặc trung tâm đổi địa chỉ chăm sóc cho các buổi chưa bắt đầu.
    @PatchMapping("/{id}/service-address")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> updateServiceAddress(@CurrentUserId UUID actorId,
                                                                   @CurrentRoles Set<RoleCode> roles,
                                                                   @CurrentCenterId(required = false) UUID scopeCenterId,
                                                                   @PathVariable UUID id,
                                                                   @Valid @RequestBody ServiceAddressRequest request) {
        return bookingService.updateServiceAddress(actorId, roles, scopeCenterId, id, request);
    }

    @PatchMapping("/{id}/occurrences/{occurrenceId}/caregiver")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(@CurrentUserId UUID actorId,
                                                                     @CurrentCenterId UUID centerId,
                                                                     @PathVariable UUID id,
                                                                     @PathVariable UUID occurrenceId,
                                                                     @Valid @RequestBody CaregiverAssignRequest request) {
        return bookingService.updateOccurrenceCaregiver(actorId, centerId, id, occurrenceId, request);
    }
}
