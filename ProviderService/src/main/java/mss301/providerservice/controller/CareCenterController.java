package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CareCenterService;
import mss301.providerservice.service.CaregiverScheduleService;
import mss301.providerservice.service.IdentityAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-centers")
@Tag(name = "Care Centers", description = "Quản lý và tra cứu trung tâm chăm sóc")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CareCenterController {

    CareCenterService careCenterService;
    IdentityAccountService identityAccountService;
    CaregiverScheduleService scheduleService;

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Danh sách trung tâm chăm sóc")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<CenterResponse>> list(
            @RequestParam(required = false) CenterStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return careCenterService.list(status, keyword, includeDeleted, page, size);
    }

    @GetMapping("/{centerId}")
    @Operation(summary = "Chi tiết trung tâm chăm sóc (public / inter-service)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CenterResponse> get(@PathVariable UUID centerId) {
        return careCenterService.get(centerId);
    }

    @GetMapping("/{centerId}/schedules")
    @Operation(summary = "Danh sách lịch người chăm sóc của trung tâm")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<ScheduleResponse>> listSchedules(
            @CurrentUserId UUID actorId,
            @PathVariable UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scheduleService.centerSchedules(actorId, centerId, page, size);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Tạo trung tâm chăm sóc mới (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> create(@CurrentUserId UUID actorId,
                                              @RequestBody @Valid CenterCreateRequest request) {
        if (request.getManagerUserId() != null) {
            identityAccountService.requireActiveRole(request.getManagerUserId(), "CENTER_MANAGER");
        }
        return careCenterService.create(actorId, request);
    }

    // ─── PUT OPERATIONS ───────────────────────────────────────────────────────

    @PutMapping("/{centerId}")
    @Operation(summary = "Cập nhật thông tin trung tâm chăm sóc (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> update(@CurrentUserId UUID actorId,
                                              @PathVariable UUID centerId,
                                              @RequestBody @Valid CenterUpdateRequest request) {
        if (request.getManagerUserId() != null) {
            identityAccountService.requireActiveRole(request.getManagerUserId(), "CENTER_MANAGER");
        }
        return careCenterService.update(actorId, centerId, request);
    }

    // ─── PATCH OPERATIONS ─────────────────────────────────────────────────────

    @PatchMapping("/{centerId}/status")
    @Operation(summary = "Cập nhật trạng thái trung tâm chăm sóc (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> updateStatus(@CurrentUserId UUID actorId,
                                                    @PathVariable UUID centerId,
                                                    @RequestBody @Valid CenterStatusUpdateRequest request) {
        return careCenterService.updateStatus(actorId, centerId, request);
    }

    @PatchMapping("/schedules/{id}/status")
    @Operation(summary = "Cập nhật hoặc hủy lịch người chăm sóc từ phía trung tâm")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<ScheduleResponse> updateScheduleStatus(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleStatusRequest request) {
        return scheduleService.updateStatus(actorId, id, request);
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    @DeleteMapping("/{centerId}")
    @Operation(summary = "Xóa mềm trung tâm chăm sóc (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID centerId,
                                    @RequestParam Long version) {
        return careCenterService.delete(actorId, centerId, version);
    }
}
