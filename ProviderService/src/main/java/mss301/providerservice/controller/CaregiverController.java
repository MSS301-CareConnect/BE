package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.*;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.AvailabilityCheckResponse;
import mss301.providerservice.dto.response.AvailabilityWindowResponse;
import mss301.providerservice.dto.response.CaregiverCardResponse;
import mss301.providerservice.dto.response.CaregiverPublicResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.CaregiverSkillResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.dto.response.TimeOffResponse;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CaregiverScheduleService;
import mss301.providerservice.service.CaregiverService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/caregivers")
@Tag(name = "Caregivers", description = "Quản lý và tra cứu hồ sơ người chăm sóc")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CaregiverController {

    CaregiverService caregiverService;
    CaregiverScheduleService caregiverScheduleService;

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public APIResponse<PageResponse<CaregiverResponse>> list(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caregiverService.list(centerId, keyword, page, size);
    }

    // SRCH-01: khách tìm caregiver làm được TẤT CẢ dịch vụ đã chọn, còn rảnh cho mọi khung giờ (nếu gửi).
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<PageResponse<CaregiverCardResponse>> search(@Valid @ModelAttribute CaregiverSearchRequest request) {
        return caregiverService.search(request);
    }

    @GetMapping("/centers/{centerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<CaregiverResponse>> listByCenter(
            @CurrentUserId UUID actorId,
            @PathVariable UUID centerId,
            @RequestParam(required = false) VerificationStatus verification,
            @RequestParam(required = false) CaregiverStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caregiverService.listByCenter(actorId, centerId, verification, status, page, size);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CaregiverResponse> getMe(@CurrentUserId UUID actorId) {
        return caregiverService.getMe(actorId);
    }

    @GetMapping("/me/schedules")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<PageResponse<ScheduleResponse>> listMySchedules(
            @CurrentUserId UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caregiverScheduleService.listMine(actorId, page, size);
    }

    // Hồ sơ đầy đủ: ADMIN / manager của trung tâm / chính caregiver / SERVICE (Booking). Khách dùng /{id}/public.
    @GetMapping("/{caregiverId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER','SERVICE')")
    public APIResponse<CaregiverResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID caregiverId) {
        return caregiverService.get(actorId, caregiverId);
    }

    @GetMapping("/{caregiverId}/public")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CaregiverPublicResponse> getPublic(@PathVariable UUID caregiverId) {
        return caregiverService.getPublic(caregiverId);
    }

    @GetMapping("/{caregiverId}/services")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<List<CaregiverSkillResponse>> listServices(@CurrentUserId UUID actorId,
                                                                  @PathVariable UUID caregiverId) {
        return caregiverService.listServices(actorId, caregiverId);
    }

    @GetMapping("/{caregiverId}/time-offs")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<List<TimeOffResponse>> listTimeOffs(@CurrentUserId UUID actorId,
                                                           @PathVariable UUID caregiverId,
                                                           @RequestParam(required = false) Instant from,
                                                           @RequestParam(required = false) Instant to) {
        return caregiverService.listTimeOffs(actorId, caregiverId, from, to);
    }

    @GetMapping("/{caregiverId}/availability")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<AvailabilityWindowResponse>> getAvailability(@PathVariable UUID caregiverId,
                                                                         @RequestParam Instant from,
                                                                         @RequestParam Instant to) {
        return caregiverService.getAvailability(caregiverId, from, to);
    }

    @GetMapping("/by-user/{userId}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CaregiverResponse> getByUser(@PathVariable UUID userId) {
        return caregiverService.getByUser(userId);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    @PostMapping("/centers/{centerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<CaregiverResponse> create(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID centerId,
                                                 @Valid @RequestBody CaregiverCreateRequest request) {
        return caregiverService.create(actorId, centerId, request);
    }

    @PostMapping("/{caregiverId}/time-offs")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<TimeOffResponse> createTimeOff(@CurrentUserId UUID actorId,
                                                      @PathVariable UUID caregiverId,
                                                      @Valid @RequestBody TimeOffRequest request) {
        return caregiverService.createTimeOff(actorId, caregiverId, request);
    }

    // C5.2 — chỉ BookingService (token SERVICE).
    @PostMapping("/availability-checks")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<AvailabilityCheckResponse> checkAvailability(@Valid @RequestBody AvailabilityCheckRequest request) {
        return caregiverService.checkAvailability(request);
    }

    // ─── PUT OPERATIONS ───────────────────────────────────────────────────────

    @PutMapping("/{caregiverId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<CaregiverResponse> update(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID caregiverId,
                                                 @Valid @RequestBody CaregiverUpdateRequest request) {
        return caregiverService.update(actorId, caregiverId, request);
    }

    @PutMapping("/{caregiverId}/services")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<List<CaregiverSkillResponse>> replaceServices(@CurrentUserId UUID actorId,
                                                                     @PathVariable UUID caregiverId,
                                                                     @Valid @RequestBody CaregiverSkillRequest request) {
        return caregiverService.replaceServices(actorId, caregiverId, request);
    }

    // ─── PATCH OPERATIONS ─────────────────────────────────────────────────────

    @PatchMapping("/{id}/verification")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CaregiverResponse> verify(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody VerificationRequest request) {
        return caregiverService.verify(actorId, id, request);
    }

    // Chỉ còn hủy lịch (status = CANCELLED kèm lý do); đã có buổi được giao trong khung → 409.
    @PatchMapping("/schedules/{id}/status")
    @PreAuthorize("hasAnyRole('CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<ScheduleResponse> updateScheduleStatus(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleStatusRequest request) {
        return caregiverScheduleService.updateStatus(actorId, id, request);
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    @DeleteMapping("/{caregiverId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<Void> archive(@CurrentUserId UUID actorId,
                                     @PathVariable UUID caregiverId,
                                     @RequestParam Long version) {
        return caregiverService.archive(actorId, caregiverId, version);
    }

    @DeleteMapping("/{caregiverId}/time-offs/{timeOffId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<Void> deleteTimeOff(@CurrentUserId UUID actorId,
                                           @PathVariable UUID caregiverId,
                                           @PathVariable UUID timeOffId) {
        return caregiverService.deleteTimeOff(actorId, caregiverId, timeOffId);
    }
}
