package mss301.carerecipientservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;
import mss301.carerecipientservice.security.CurrentUserId;
import mss301.carerecipientservice.service.CareRecipientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-recipients")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Care recipient", description = "Quản lý hồ sơ người bệnh (Customer, Admin, Caregiver, Inter-service)")
public class CareRecipientController {

    CareRecipientService careRecipientService;

    // ===== Customer Endpoints =====

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientDetailResponse> create(@CurrentUserId UUID actorId,
                                                           @Valid @RequestBody CareRecipientCreateRequest request) {
        return careRecipientService.create(actorId, request);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> list(@CurrentUserId UUID actorId,
                                                                        @RequestParam(required = false) RecipientStatus status,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size) {
        return careRecipientService.list(actorId, status, keyword, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientDetailResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return careRecipientService.get(actorId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientResponse> update(@CurrentUserId UUID actorId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody CareRecipientUpdateRequest request) {
        return careRecipientService.update(actorId, id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientResponse> updateStatus(@CurrentUserId UUID actorId,
                                                           @PathVariable UUID id,
                                                           @RequestParam RecipientStatus status) {
        return careRecipientService.updateStatus(actorId, id, status);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return careRecipientService.delete(actorId, id);
    }

    // ===== Admin Endpoints (Merged from AdminCareRecipientController) =====

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> listForAdmin(@RequestParam(required = false) RecipientStatus status,
                                                                                @RequestParam(required = false) String keyword,
                                                                                @RequestParam(required = false) UUID createdBy,
                                                                                @RequestParam(defaultValue = "false") boolean includeDeleted,
                                                                                @RequestParam(defaultValue = "0") int page,
                                                                                @RequestParam(defaultValue = "20") int size) {
        return careRecipientService.listForAdmin(status, keyword, createdBy, includeDeleted, page, size);
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CareRecipientDetailResponse> getForAdmin(@PathVariable UUID id) {
        return careRecipientService.getForAdmin(id);
    }

    // ===== Caregiver Endpoints (Merged from CaregiverCareRecipientController) =====

    @GetMapping("/caregiver/{id}")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<RecipientSnapshotResponse> getForCaregiver(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return careRecipientService.getSnapshotForCaregiver(actorId, id);
    }

    // ===== Inter-Service Endpoints (Migrated from InternalCareRecipientController) =====

    @GetMapping("/{id}/snapshot")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<RecipientSnapshotResponse> getSnapshot(@PathVariable UUID id) {
        return careRecipientService.getSnapshot(id);
    }

    @GetMapping("/{id}/access")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<AccessCheckResponse> checkAccess(@PathVariable UUID id,
                                                        @RequestParam UUID userId,
                                                        @RequestParam(defaultValue = "VIEWER") RecipientAccessLevel minLevel) {
        return careRecipientService.checkAccess(id, userId, minLevel);
    }

    @PostMapping("/batch")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<CareRecipientSummaryResponse>> listBatch(@Valid @RequestBody RecipientBatchRequest request) {
        return careRecipientService.listBatch(request);
    }
}
