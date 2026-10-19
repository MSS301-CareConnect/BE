package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.CaregiverUpdateRequest;
import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CaregiverScheduleService;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.IdentityAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/caregivers")
@Tag(name = "Caregivers", description = "Quản lý và tra cứu hồ sơ người chăm sóc")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CaregiverController {

    CaregiverService caregiverService;
    IdentityAccountService identityAccountService;
    CaregiverScheduleService scheduleService;

    @PostMapping("/centers/{centerId}")
    @Operation(summary = "Tạo hồ sơ caregiver thuộc trung tâm")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<CaregiverResponse> create(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID centerId,
                                                 @Valid @RequestBody CaregiverCreateRequest request) {
        identityAccountService.requireActiveRole(request.getUserId(), "CAREGIVER");
        return caregiverService.create(actorId, centerId, request);
    }

    @GetMapping("/centers/{centerId}")
    @Operation(summary = "Danh sách caregiver do trung tâm quản lý")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<CaregiverResponse>> listManaged(
            @CurrentUserId UUID actorId,
            @PathVariable UUID centerId,
            @RequestParam(required = false) VerificationStatus verification,
            @RequestParam(required = false) CaregiverStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caregiverService.listManaged(actorId, centerId, verification, status, page, size);
    }

    @GetMapping
    @Operation(summary = "Tìm kiếm caregiver đang hoạt động (Catalog)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<PageResponse<CaregiverResponse>> catalog(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caregiverService.catalog(centerId, keyword, page, size);
    }

    @GetMapping("/me")
    @Operation(summary = "Hồ sơ caregiver của tôi")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CaregiverResponse> myProfile(@CurrentUserId UUID actorId) {
        return caregiverService.myProfile(actorId);
    }

    @GetMapping("/{caregiverId}")
    @Operation(summary = "Chi tiết hồ sơ caregiver (public / inter-service)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CaregiverResponse> get(@PathVariable UUID caregiverId) {
        return caregiverService.get(caregiverId);
    }

    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Tra cứu hồ sơ caregiver theo userId tài khoản Identity (public / inter-service)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CaregiverResponse> getByUser(@PathVariable UUID userId) {
        return caregiverService.getByUser(userId);
    }

    @PutMapping("/{caregiverId}")
    @Operation(summary = "Cập nhật hồ sơ caregiver")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<CaregiverResponse> update(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID caregiverId,
                                                 @Valid @RequestBody CaregiverUpdateRequest request) {
        return caregiverService.update(actorId, caregiverId, request);
    }

    @DeleteMapping("/{caregiverId}")
    @Operation(summary = "Lưu trữ hồ sơ caregiver (xóa mềm)")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<Void> archive(@CurrentUserId UUID actorId,
                                     @PathVariable UUID caregiverId,
                                     @RequestParam Long version) {
        return caregiverService.archive(actorId, caregiverId, version);
    }

    @PatchMapping("/{id}/verification")
    @Operation(summary = "Duyệt hoặc từ chối hồ sơ caregiver (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CaregiverResponse> verify(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody VerificationRequest request) {
        return caregiverService.verify(actorId, id, request);
    }

    @GetMapping("/me/schedules")
    @Operation(summary = "Lịch làm việc của tôi (Caregiver)")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<PageResponse<ScheduleResponse>> mySchedules(
            @CurrentUserId UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scheduleService.mySchedules(actorId, page, size);
    }

    @PatchMapping("/schedules/{id}/status")
    @Operation(summary = "Bắt đầu hoặc hoàn thành lịch làm việc (Caregiver / Manager / Admin)")
    @PreAuthorize("hasAnyRole('CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<ScheduleResponse> updateScheduleStatus(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleStatusRequest request) {
        return scheduleService.updateStatus(actorId, id, request);
    }
}
