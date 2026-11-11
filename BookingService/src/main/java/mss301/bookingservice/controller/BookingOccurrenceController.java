package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.BusyCaregiverRequest;
import mss301.bookingservice.dto.request.CustomerVisitReportRequest;
import mss301.bookingservice.dto.request.NoShowDecisionRequest;
import mss301.bookingservice.dto.request.NoShowReportRequest;
import mss301.bookingservice.dto.request.OccurrenceAcknowledgementRequest;
import mss301.bookingservice.dto.request.OccurrenceReschedulePreviewRequest;
import mss301.bookingservice.dto.request.OccurrenceRescheduleRequest;
import mss301.bookingservice.dto.request.ReplacementConsentRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BusyCaregiverResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.dto.response.OccurrenceRescheduleResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.BookingOccurrenceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking-occurrences")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Booking occurrence", description = "Buổi chăm sóc — lịch được giao của caregiver và chi tiết một buổi theo phạm vi role")
public class BookingOccurrenceController {

    BookingOccurrenceService bookingOccurrenceService;

    @GetMapping
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<PageResponse<CaregiverOccurrenceResponse>> list(@CurrentUserId UUID actorId,
                                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                       @RequestParam(required = false) OccurrenceStatus status,
                                                                       @RequestParam(required = false) UUID bookingId,
                                                                       @RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        return bookingOccurrenceService.list(actorId, from, to, status, bookingId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CAREGIVER', 'CUSTOMER', 'CENTER_MANAGER', 'ADMIN', 'SERVICE')")
    public APIResponse<CaregiverOccurrenceResponse> get(@CurrentUserId UUID actorId,
                                                        @CurrentRoles Set<RoleCode> roles,
                                                        @CurrentCenterId(required = false) UUID centerId,
                                                        @PathVariable UUID id) {
        return bookingOccurrenceService.get(actorId, roles, centerId, id);
    }

    @PostMapping("/busy-caregivers")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<BusyCaregiverResponse> listBusyCaregivers(@Valid @RequestBody BusyCaregiverRequest request) {
        return bookingOccurrenceService.listBusyCaregivers(request);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CaregiverOccurrenceResponse> acknowledge(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return bookingOccurrenceService.acknowledge(actorId, id);
    }

    @PostMapping("/acknowledgements")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<List<CaregiverOccurrenceResponse>> acknowledgeMany(@CurrentUserId UUID actorId,
                                                                          @Valid @RequestBody OccurrenceAcknowledgementRequest request) {
        return bookingOccurrenceService.acknowledgeMany(actorId, request);
    }

    @PostMapping("/{id}/no-show-reports")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CaregiverOccurrenceResponse> reportNoShow(@CurrentUserId UUID actorId,
                                                                 @PathVariable UUID id,
                                                                 @Valid @RequestBody NoShowReportRequest request) {
        return bookingOccurrenceService.reportNoShow(actorId, id, request);
    }

    @PostMapping("/{id}/no-show-reports/decision")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CaregiverOccurrenceResponse> decideNoShow(@CurrentUserId UUID actorId,
                                                                 @CurrentCenterId UUID centerId,
                                                                 @PathVariable UUID id,
                                                                 @Valid @RequestBody NoShowDecisionRequest request) {
        return bookingOccurrenceService.decideNoShow(actorId, centerId, id, request);
    }

    // Module 2 (SPEC V3): Khách hàng phản hồi chuyên viên thay thế khẩn cấp (ACCEPT, RESCHEDULE, REJECT_AND_CANCEL)
    @PostMapping("/{id}/replacement-consent")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> replacementConsent(@CurrentUserId UUID actorId,
                                                @PathVariable UUID id,
                                                @Valid @RequestBody ReplacementConsentRequest request) {
        return bookingOccurrenceService.consentReplacement(actorId, id, request);
    }

    // 2026-11-11: khách báo nhanh — chưa thấy người chăm sóc đến, hoặc buổi đã kết thúc nhưng không được thực hiện đúng.
    @PostMapping("/{id}/customer-reports")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<OccurrenceResponse> reportByCustomer(@CurrentUserId UUID actorId,
                                                            @PathVariable UUID id,
                                                            @Valid @RequestBody(required = false) CustomerVisitReportRequest request) {
        return bookingOccurrenceService.reportByCustomer(actorId, id, request);
    }

    // Module 3 (SPEC V3): Khách hàng hoặc trung tâm kiểm tra tính khả thi khi dời lịch
    @PostMapping("/{id}/reschedule-preview")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<OccurrenceRescheduleResponse> reschedulePreview(@CurrentUserId UUID actorId,
                                                                      @CurrentRoles Set<RoleCode> roles,
                                                                      @CurrentCenterId(required = false) UUID centerId,
                                                                      @PathVariable UUID id,
                                                                      @Valid @RequestBody OccurrenceReschedulePreviewRequest request) {
        return bookingOccurrenceService.reschedulePreview(actorId, roles, centerId, id, request);
    }

    // Module 3 (SPEC V3): Khách hàng hoặc trung tâm thực hiện dời lịch buổi chăm sóc
    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<OccurrenceRescheduleResponse> reschedule(@CurrentUserId UUID actorId,
                                                               @CurrentRoles Set<RoleCode> roles,
                                                               @CurrentCenterId(required = false) UUID centerId,
                                                               @PathVariable UUID id,
                                                               @Valid @RequestBody OccurrenceRescheduleRequest request) {
        return bookingOccurrenceService.reschedule(actorId, roles, centerId, id, request);
    }
}
