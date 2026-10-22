package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CenterCardResponse;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CareCenterService;
import mss301.providerservice.service.CaregiverScheduleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-centers")
@Tag(name = "Care Centers", description = "Quản lý và tra cứu trung tâm chăm sóc")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CareCenterController {

    CareCenterService careCenterService;
    CaregiverScheduleService caregiverScheduleService;

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

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

    // SRCH-02: khách xem trung tâm ACTIVE (lọc dịch vụ / giá qua Catalog). Mặc định cần đăng nhập (chưa mở public).
    @GetMapping("/public")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caregiverScheduleService.listByCenter(actorId, centerId, page, size);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> create(@CurrentUserId UUID actorId,
                                              @RequestBody @Valid CenterCreateRequest request) {
        return careCenterService.create(actorId, request);
    }

    // ─── PUT OPERATIONS ───────────────────────────────────────────────────────

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
