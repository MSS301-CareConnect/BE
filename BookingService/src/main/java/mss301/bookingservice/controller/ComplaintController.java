package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.*;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.ComplaintResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.ComplaintAction;
import mss301.bookingservice.enums.ComplaintStatus;
import mss301.bookingservice.enums.ComplaintLevel;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.service.ComplaintService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Complaints", description = "Khiếu nại cấp trung tâm và quyết định của admin")
public class ComplaintController {
    ComplaintService complaintService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER')")
    public APIResponse<PageResponse<ComplaintResponse>> list(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID scopeCenterId, @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) ComplaintLevel level, @RequestParam(required = false) UUID centerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return complaintService.list(actorId, roles, scopeCenterId, status, level, centerId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER')")
    public APIResponse<ComplaintResponse> get(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID centerId, @PathVariable UUID id) {
        return complaintService.get(actorId, roles, centerId, id);
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<ComplaintResponse> create(@CurrentUserId UUID actorId, @Valid @RequestBody ComplaintRequest request) {
        return complaintService.create(actorId, request);
    }

    @PostMapping("/{id}/escalate")
    @PreAuthorize("hasAnyRole('CENTER_MANAGER','CUSTOMER')")
    public APIResponse<ComplaintResponse> escalate(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID centerId, @PathVariable UUID id,
            @Valid @RequestBody ComplaintEscalateRequest request) {
        return complaintService.transition(actorId, roles, centerId, id, ComplaintAction.ESCALATE, request.getReason());
    }

    // D20: khách rút khiếu nại chưa được giải quyết.
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<ComplaintResponse> withdraw(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
            @PathVariable UUID id) {
        return complaintService.transition(actorId, roles, null, id, ComplaintAction.WITHDRAW, null);
    }

    @PatchMapping("/{id}/accept")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<ComplaintResponse> accept(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId UUID centerId, @PathVariable UUID id) {
        return complaintService.transition(actorId, roles, centerId, id, ComplaintAction.ACCEPT, null);
    }

    @PatchMapping("/{id}/resolve")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<ComplaintResponse> resolve(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId UUID centerId, @PathVariable UUID id, @Valid @RequestBody ComplaintResolutionRequest request) {
        return complaintService.transition(actorId, roles, centerId, id, ComplaintAction.RESOLVE, request.getResolution());
    }

    @PatchMapping("/{id}/admin-resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<ComplaintResponse> adminResolve(@CurrentUserId UUID actorId, @PathVariable UUID id,
            @Valid @RequestBody ComplaintAdminResolutionRequest request) {
        return complaintService.adminResolve(actorId, id, request);
    }
}
