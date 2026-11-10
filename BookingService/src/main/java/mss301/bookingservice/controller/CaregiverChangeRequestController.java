package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.CaregiverChangeApproveRequest;
import mss301.bookingservice.dto.request.CaregiverChangeCreateRequest;
import mss301.bookingservice.dto.request.CaregiverChangeRejectRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverChangeRequestResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.CaregiverChangeRequestStatus;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.CaregiverChangeRequestService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Caregiver Change Request", description = "Khách hàng yêu cầu đổi người chăm sóc và trung tâm phê duyệt")
public class CaregiverChangeRequestController {

    CaregiverChangeRequestService changeRequestService;

    @PostMapping("/bookings/{id}/caregiver-change-requests")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CaregiverChangeRequestResponse> createRequest(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody CaregiverChangeCreateRequest request) {
        return changeRequestService.createRequest(actorId, id, request);
    }

    @GetMapping("/bookings/{id}/caregiver-change-requests")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<List<CaregiverChangeRequestResponse>> listRequestsByBooking(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID centerId,
            @PathVariable UUID id) {
        return changeRequestService.listRequestsByBooking(actorId, roles, centerId, id);
    }

    @GetMapping("/caregiver-change-requests")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PageResponse<CaregiverChangeRequestResponse>> listCenterRequests(
            @CurrentUserId UUID actorId,
            @CurrentCenterId UUID centerId,
            @RequestParam(required = false) CaregiverChangeRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return changeRequestService.listCenterRequests(actorId, centerId, status, page, size);
    }

    @PostMapping("/caregiver-change-requests/{id}/approve")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CaregiverChangeRequestResponse> approveRequest(
            @CurrentUserId UUID actorId,
            @CurrentCenterId UUID centerId,
            @PathVariable UUID id,
            @Valid @RequestBody CaregiverChangeApproveRequest request) {
        return changeRequestService.approveRequest(actorId, centerId, id, request);
    }

    @PostMapping("/caregiver-change-requests/{id}/reject")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CaregiverChangeRequestResponse> rejectRequest(
            @CurrentUserId UUID actorId,
            @CurrentCenterId UUID centerId,
            @PathVariable UUID id,
            @Valid @RequestBody CaregiverChangeRejectRequest request) {
        return changeRequestService.rejectRequest(actorId, centerId, id, request);
    }

    @DeleteMapping("/caregiver-change-requests/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CaregiverChangeRequestResponse> cancelRequest(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id) {
        return changeRequestService.cancelRequest(actorId, id);
    }
}
