package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mss301.providerservice.dto.request.*;
import mss301.providerservice.dto.response.*;
import mss301.providerservice.enums.*;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.IdentityAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Caregiver Profiles", description = "Trung tâm quản lý hồ sơ người chăm sóc")
@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
public class CaregiverManagementController {
    private final CaregiverService caregiverService;
    private final IdentityAccountService identityAccountService;

    @PostMapping("/centers/{centerId}/caregivers")
    @Operation(summary = "Tạo hồ sơ caregiver", description = "Tạo hồ sơ PENDING/INACTIVE thuộc trung tâm.")
    public APIResponse<CaregiverResponse> create(@CurrentUserId UUID actorId, @PathVariable UUID centerId,
                                                 @Valid @RequestBody CaregiverCreateRequest request) {
        identityAccountService.requireActiveRole(request.getUserId(), "CAREGIVER");
        return caregiverService.create(actorId, centerId, request);
    }

    @GetMapping("/centers/{centerId}/caregivers")
    @Operation(summary = "Danh sách caregiver của trung tâm")
    public APIResponse<PageResponse<CaregiverResponse>> list(
            @CurrentUserId UUID actorId, @PathVariable UUID centerId,
            @RequestParam(required = false) VerificationStatus verification,
            @RequestParam(required = false) CaregiverStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return caregiverService.listManaged(actorId, centerId, verification, status, page, size);
    }

    @GetMapping("/caregivers/{caregiverId}")
    @Operation(summary = "Chi tiết hồ sơ caregiver")
    public APIResponse<CaregiverResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID caregiverId) {
        return caregiverService.get(actorId, caregiverId);
    }

    @PutMapping("/caregivers/{caregiverId}")
    @Operation(summary = "Cập nhật hồ sơ caregiver")
    public APIResponse<CaregiverResponse> update(@CurrentUserId UUID actorId, @PathVariable UUID caregiverId,
                                                 @Valid @RequestBody CaregiverUpdateRequest request) {
        return caregiverService.update(actorId, caregiverId, request);
    }

    @DeleteMapping("/caregivers/{caregiverId}")
    @Operation(summary = "Lưu trữ hồ sơ caregiver", description = "Xóa mềm, không xóa vật lý dữ liệu.")
    public APIResponse<Void> archive(@CurrentUserId UUID actorId, @PathVariable UUID caregiverId,
                                     @RequestParam Long version) {
        return caregiverService.archive(actorId, caregiverId, version);
    }
}
