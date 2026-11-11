package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.CaregiverChangeApproveRequest;
import mss301.bookingservice.dto.request.CaregiverChangeRejectRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverChangeRequestResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.CaregiverChangeRequestStatus;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.CaregiverChangeRequestService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/caregiver-change-requests")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Caregiver Change Request", description = "Khách hàng yêu cầu đổi người chăm sóc và trung tâm phê duyệt")
public class CaregiverChangeRequestController {

    CaregiverChangeRequestService caregiverChangeRequestService;

    // API danh sách yêu cầu đổi người của trung tâm.
    @GetMapping
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PageResponse<CaregiverChangeRequestResponse>> list(
            @CurrentUserId UUID actorId,
            @CurrentCenterId UUID centerId,
            @RequestParam(required = false) CaregiverChangeRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caregiverChangeRequestService.list(actorId, centerId, status, page, size);
    }

    // API trung tâm duyệt yêu cầu đổi người.
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CaregiverChangeRequestResponse> approve(
            @CurrentUserId UUID actorId,
            @CurrentCenterId UUID centerId,
            @PathVariable UUID id,
            @Valid @RequestBody CaregiverChangeApproveRequest request) {
        return caregiverChangeRequestService.approve(actorId, centerId, id, request);
    }

    // API trung tâm từ chối yêu cầu đổi người.
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CaregiverChangeRequestResponse> reject(
            @CurrentUserId UUID actorId,
            @CurrentCenterId UUID centerId,
            @PathVariable UUID id,
            @Valid @RequestBody CaregiverChangeRejectRequest request) {
        return caregiverChangeRequestService.reject(actorId, centerId, id, request);
    }

    // API khách hủy yêu cầu đổi người.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CaregiverChangeRequestResponse> cancel(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id) {
        return caregiverChangeRequestService.cancel(actorId, id);
    }
}
