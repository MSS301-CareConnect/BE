package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterRestoreRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.dto.request.CenterSelfUpdateRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.ProviderStatisticsResponse;
import mss301.providerservice.dto.response.CenterCardResponse;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.dto.response.TimeOffResponse;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CareCenterService;
import mss301.providerservice.service.CaregiverScheduleService;
import mss301.providerservice.service.CaregiverService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-centers")
@Tag(name = "Care Centers", description = "Quản lý và tra cứu trung tâm chăm sóc")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CareCenterController {

    CareCenterService careCenterService;
    CaregiverScheduleService caregiverScheduleService;
    CaregiverService caregiverService;

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<ProviderStatisticsResponse> statistics() {
        return careCenterService.statistics();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<CenterResponse>> list(
            @RequestParam(required = false) CenterStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return careCenterService.list(status, keyword, includeDeleted, page, size);
    }

    // SRCH-02: danh bạ trung tâm ACTIVE, công khai cho cả khách chưa đăng nhập (CR-6, 2026-11-03) — chỉ DTO thẻ công khai.
    @GetMapping("/public")
    @SecurityRequirements({})
    public APIResponse<PageResponse<CenterCardResponse>> listPublic(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID careServiceId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return careCenterService.listPublic(keyword, careServiceId, minPrice, maxPrice, page, size);
    }

    @GetMapping("/public/{centerId}")
    @SecurityRequirements({})
    public APIResponse<CenterCardResponse> getPublic(@PathVariable UUID centerId) {
        return careCenterService.getPublic(centerId);
    }

    @GetMapping("/{centerId}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CenterResponse> get(@PathVariable UUID centerId) {
        return careCenterService.get(centerId);
    }

    @GetMapping("/{centerId}/schedules")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<ScheduleResponse>> listSchedules(
            @CurrentUserId UUID actorId,
            @PathVariable UUID centerId,
            @RequestParam(required = false) UUID caregiverId,
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caregiverScheduleService.listByCenter(actorId, centerId, caregiverId, status, fromDate, toDate,
                page, size);
    }

    // Ngày nghỉ của mọi caregiver trong trung tâm giao khoảng [from, to) — trang "Lịch làm việc" của manager.
    @GetMapping("/{centerId}/time-offs")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<TimeOffResponse>> listTimeOffs(
            @CurrentUserId UUID actorId,
            @PathVariable UUID centerId,
            @RequestParam(required = false) UUID caregiverId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return caregiverService.listCenterTimeOffs(actorId, centerId, caregiverId, from, to, page, size);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> create(@CurrentUserId UUID actorId,
                                              @RequestBody @Valid CenterCreateRequest request) {
        return careCenterService.create(actorId, request);
    }

    // D31: trung tâm đã lưu trữ → INACTIVE để admin rà lại trước khi mở lại.
    @PostMapping("/{centerId}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> restore(@CurrentUserId UUID actorId,
                                               @PathVariable UUID centerId,
                                               @RequestBody @Valid CenterRestoreRequest request) {
        return careCenterService.restore(actorId, centerId, request);
    }

    // ─── PUT OPERATIONS ───────────────────────────────────────────────────────

    @PutMapping("/me")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CenterResponse> updateMine(@CurrentUserId UUID actorId, @Valid @RequestBody CenterSelfUpdateRequest request) {
        return careCenterService.updateMine(actorId, request);
    }

    @PutMapping("/{centerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> update(@CurrentUserId UUID actorId,
                                              @PathVariable UUID centerId,
                                              @RequestBody @Valid CenterUpdateRequest request) {
        return careCenterService.update(actorId, centerId, request);
    }

    // ─── PATCH OPERATIONS ─────────────────────────────────────────────────────

    @PatchMapping("/{centerId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> updateStatus(@CurrentUserId UUID actorId,
                                                    @PathVariable UUID centerId,
                                                    @RequestBody @Valid CenterStatusUpdateRequest request) {
        return careCenterService.updateStatus(actorId, centerId, request);
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    @DeleteMapping("/{centerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID centerId,
                                    @RequestParam Long version) {
        return careCenterService.delete(actorId, centerId, version);
    }
}
