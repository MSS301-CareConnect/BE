package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.ShiftRegistrationService;
import mss301.providerservice.service.WorkShiftService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-shifts")
@Tag(name = "Work Shifts", description = "Quản lý ca làm việc và đăng ký ca")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WorkShiftController {

    WorkShiftService workShiftService;
    ShiftRegistrationService shiftRegistrationService;

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<PageResponse<WorkShiftResponse>> list(
            @CurrentUserId UUID actorId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return workShiftService.list(actorId, centerId, status, fromDate, toDate, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<WorkShiftResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return workShiftService.get(actorId, id);
    }

    @GetMapping("/registrations/me")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listMyRegistrations(
            @CurrentUserId UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return shiftRegistrationService.listMine(actorId, page, size);
    }

    @GetMapping("/{id}/registrations")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listRegistrations(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return shiftRegistrationService.listByShift(actorId, id, page, size);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    @PostMapping("/centers/{centerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftResponse> create(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID centerId,
                                                 @Valid @RequestBody WorkShiftRequest request) {
        return workShiftService.create(actorId, centerId, request);
    }

    @PostMapping("/{shiftId}/registrations")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<ShiftRegistrationResponse> register(
            @CurrentUserId UUID actorId,
            @PathVariable UUID shiftId,
            @Valid @RequestBody ShiftRegistrationRequest request) {
        return shiftRegistrationService.register(actorId, shiftId, request);
    }

    // ─── PUT OPERATIONS ───────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftResponse> update(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody WorkShiftRequest request) {
        return workShiftService.update(actorId, id, request);
    }

    // ─── PATCH OPERATIONS ─────────────────────────────────────────────────────

    @PatchMapping("/registrations/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<ShiftRegistrationResponse> reviewRegistration(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationReviewRequest request) {
        return shiftRegistrationService.review(actorId, id, request);
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID id,
                                    @RequestParam Long version) {
        return workShiftService.delete(actorId, id, version);
    }

    @DeleteMapping("/registrations/{id}")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<Void> cancelRegistration(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @RequestParam Long version) {
        return shiftRegistrationService.cancel(actorId, id, version);
    }
}
