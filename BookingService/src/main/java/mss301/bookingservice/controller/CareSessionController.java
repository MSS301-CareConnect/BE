package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.*;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CareSessionResponse;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.CareSessionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-sessions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Care session", description = "Ca chăm sóc thực tế — check-in, checklist, check-out, buộc kết thúc")
public class CareSessionController {

    CareSessionService careSessionService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CAREGIVER', 'CUSTOMER', 'CENTER_MANAGER', 'ADMIN', 'SERVICE')")
    public APIResponse<CareSessionResponse> get(@CurrentUserId UUID actorId,
                                                @CurrentRoles Set<RoleCode> roles,
                                                @CurrentCenterId(required = false) UUID scopeCenterId,
                                                @PathVariable UUID id) {
        return careSessionService.get(actorId, roles, scopeCenterId, id);
    }

    @PostMapping
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CareSessionResponse> start(@CurrentUserId UUID actorId,
                                                  @Valid @RequestBody CareSessionStartRequest request) {
        return careSessionService.start(actorId, request);
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CareSessionResponse> end(@CurrentUserId UUID actorId,
                                                @PathVariable UUID id,
                                                @Valid @RequestBody(required = false) CareSessionEndRequest request) {
        return careSessionService.end(actorId, id, request);
    }

    @PostMapping("/{id}/force-end")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CareSessionResponse> forceEnd(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody CareSessionForceEndRequest request) {
        return careSessionService.forceEnd(actorId, centerId, id, request);
    }
}
