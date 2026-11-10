package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.CaregiverAbsenceConclusionRequest;
import mss301.bookingservice.dto.request.CaregiverAbsenceExplanationRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverAbsenceResponse;
import mss301.bookingservice.dto.response.CaregiverAbsenceStatisticsResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.CaregiverAbsenceStatus;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.CaregiverAbsenceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/caregiver-absences")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Caregiver Absence", description = "Hồ sơ caregiver vắng ca: caregiver giải trình, Center Manager kết luận, Admin theo dõi toàn hệ thống")
public class CaregiverAbsenceController {

    CaregiverAbsenceService caregiverAbsenceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CAREGIVER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<PageResponse<CaregiverAbsenceResponse>> list(@CurrentUserId UUID actorId,
                                                                    @CurrentRoles Set<RoleCode> roles,
                                                                    @CurrentCenterId(required = false) UUID scopeCenterId,
                                                                    @RequestParam(required = false) CaregiverAbsenceStatus status,
                                                                    @RequestParam(required = false) UUID centerId,
                                                                    @RequestParam(required = false) UUID caregiverId,
                                                                    @RequestParam(required = false) UUID bookingId,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return caregiverAbsenceService.list(actorId, roles, scopeCenterId, status, centerId, caregiverId, bookingId, from,
                to, page, size);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('CAREGIVER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<CaregiverAbsenceStatisticsResponse> getStatistics(@CurrentUserId UUID actorId,
                                                                         @CurrentRoles Set<RoleCode> roles,
                                                                         @CurrentCenterId(required = false) UUID scopeCenterId,
                                                                         @RequestParam(required = false) UUID centerId,
                                                                         @RequestParam(required = false) UUID caregiverId,
                                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return caregiverAbsenceService.getStatistics(actorId, roles, scopeCenterId, centerId, caregiverId, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CAREGIVER', 'CENTER_MANAGER', 'ADMIN')")
    public APIResponse<CaregiverAbsenceResponse> get(@CurrentUserId UUID actorId,
                                                     @CurrentRoles Set<RoleCode> roles,
                                                     @CurrentCenterId(required = false) UUID scopeCenterId,
                                                     @PathVariable UUID id) {
        return caregiverAbsenceService.get(actorId, roles, scopeCenterId, id);
    }

    @PostMapping("/{id}/explanation")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CaregiverAbsenceResponse> explain(@CurrentUserId UUID actorId,
                                                         @PathVariable UUID id,
                                                         @Valid @RequestBody CaregiverAbsenceExplanationRequest request) {
        return caregiverAbsenceService.explain(actorId, id, request);
    }

    @PostMapping("/{id}/conclusion")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CaregiverAbsenceResponse> conclude(@CurrentUserId UUID actorId,
                                                          @CurrentCenterId UUID centerId,
                                                          @PathVariable UUID id,
                                                          @Valid @RequestBody CaregiverAbsenceConclusionRequest request) {
        return caregiverAbsenceService.conclude(actorId, centerId, id, request);
    }
}
