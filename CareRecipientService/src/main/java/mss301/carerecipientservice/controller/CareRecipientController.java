package mss301.carerecipientservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.request.*;
import mss301.carerecipientservice.dto.response.*;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;
import mss301.carerecipientservice.enums.RoleCode;
import mss301.carerecipientservice.security.CurrentRoles;
import mss301.carerecipientservice.security.CurrentUserId;
import mss301.carerecipientservice.service.CareRecipientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-recipients")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Care recipient", description = "Quản lý hồ sơ người bệnh, thuốc, liên hệ khẩn cấp và người cùng quản lý")
public class CareRecipientController {

    CareRecipientService careRecipientService;

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

    // Phạm vi theo role nằm ở service: CUSTOMER → hồ sơ mình quản lý; ADMIN → toàn hệ thống. createdBy / includeDeleted
    // chỉ có tác dụng với ADMIN — CUSTOMER gửi lên bị bỏ qua.
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> list(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @RequestParam(required = false) RecipientStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return careRecipientService.list(actorId, roles, status, keyword, createdBy, includeDeleted, page, size);
    }

    // ADMIN → mọi hồ sơ (kể cả đã lưu trữ); CUSTOMER → hồ sơ mình quản lý; CAREGIVER → chỉ careSnapshot khi
    // BookingService xác nhận đang được phân công. Ngoài phạm vi → 404.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN','CAREGIVER')")
    public APIResponse<CareRecipientDetailResponse> get(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @PathVariable UUID id) {
        return careRecipientService.get(actorId, roles, id);
    }

    // Chỉ service khác (BookingService lúc đặt lịch) — người dùng xem hồ sơ qua GET /{id} theo quyền thành viên.
    @GetMapping("/{id}/snapshot")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<RecipientSnapshotResponse> getSnapshot(@PathVariable UUID id) {
        return careRecipientService.getSnapshot(id);
    }

    // Chỉ service khác: userId tùy ý trong query, người dùng thường không được dò quyền của người khác.
    @GetMapping("/{id}/access")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<AccessCheckResponse> checkAccess(
            @PathVariable UUID id,
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "VIEWER") RecipientAccessLevel minLevel) {
        return careRecipientService.checkAccess(id, userId, minLevel);
    }

    @GetMapping("/{recipientId}/medications")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<MedicationResponse>> listMedications(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @RequestParam(required = false) Boolean active) {
        return careRecipientService.listMedications(actorId, recipientId, active);
    }

    @GetMapping("/{recipientId}/medications/{medicationId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<MedicationResponse> getMedication(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID medicationId) {
        return careRecipientService.getMedication(actorId, recipientId, medicationId);
    }

    @GetMapping("/{recipientId}/emergency-contacts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<EmergencyContactResponse>> listEmergencyContacts(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId) {
        return careRecipientService.listEmergencyContacts(actorId, recipientId);
    }

    @GetMapping("/{recipientId}/emergency-contacts/{contactId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<EmergencyContactResponse> getEmergencyContact(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID contactId) {
        return careRecipientService.getEmergencyContact(actorId, recipientId, contactId);
    }

    @GetMapping("/{recipientId}/managers")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<RecipientManagerResponse>> listManagers(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId) {
        return careRecipientService.listManagers(actorId, recipientId);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientDetailResponse> create(
            @CurrentUserId UUID actorId,
            @Valid @RequestBody CareRecipientCreateRequest request) {
        return careRecipientService.create(actorId, request);
    }

    @PostMapping("/batch")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<CareRecipientSummaryResponse>> listBatch(@Valid @RequestBody RecipientBatchRequest request) {
        return careRecipientService.listBatch(request);
    }

    @PostMapping("/{recipientId}/medications")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<MedicationResponse> createMedication(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @Valid @RequestBody MedicationRequest request) {
        return careRecipientService.createMedication(actorId, recipientId, request);
    }

    @PostMapping("/{recipientId}/emergency-contacts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<EmergencyContactResponse> createEmergencyContact(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @Valid @RequestBody EmergencyContactRequest request) {
        return careRecipientService.createEmergencyContact(actorId, recipientId, request);
    }

    @PostMapping("/{recipientId}/managers")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<RecipientManagerResponse> createManager(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @Valid @RequestBody ManagerCreateRequest request) {
        return careRecipientService.createManager(actorId, recipientId, request);
    }

    // ─── PUT OPERATIONS ───────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientResponse> update(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody CareRecipientUpdateRequest request) {
        return careRecipientService.update(actorId, id, request);
    }

    @PutMapping("/{recipientId}/medications/{medicationId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<MedicationResponse> updateMedication(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID medicationId,
            @Valid @RequestBody MedicationRequest request) {
        return careRecipientService.updateMedication(actorId, recipientId, medicationId, request);
    }

    @PutMapping("/{recipientId}/emergency-contacts/{contactId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<EmergencyContactResponse> updateEmergencyContact(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID contactId,
            @Valid @RequestBody EmergencyContactRequest request) {
        return careRecipientService.updateEmergencyContact(actorId, recipientId, contactId, request);
    }

    // ─── PATCH OPERATIONS ─────────────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientResponse> updateStatus(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @RequestParam RecipientStatus status) {
        return careRecipientService.updateStatus(actorId, id, status);
    }

    @PatchMapping("/{recipientId}/medications/{medicationId}/active")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<MedicationResponse> updateMedicationActive(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID medicationId,
            @RequestParam boolean active) {
        return careRecipientService.updateMedicationActive(actorId, recipientId, medicationId, active);
    }

    @PatchMapping("/{recipientId}/emergency-contacts/{contactId}/primary")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<EmergencyContactResponse> assignPrimaryEmergencyContact(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID contactId) {
        return careRecipientService.assignPrimaryEmergencyContact(actorId, recipientId, contactId);
    }

    @PatchMapping("/{recipientId}/managers/{managerId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<RecipientManagerResponse> updateManager(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID managerId,
            @Valid @RequestBody ManagerUpdateRequest request) {
        return careRecipientService.updateManager(actorId, recipientId, managerId, request);
    }

    @PatchMapping("/{recipientId}/managers/{managerId}/primary")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<RecipientManagerResponse> assignPrimaryManager(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID managerId) {
        return careRecipientService.assignPrimaryManager(actorId, recipientId, managerId);
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return careRecipientService.delete(actorId, id);
    }

    @DeleteMapping("/{recipientId}/medications/{medicationId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> deleteMedication(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID medicationId) {
        return careRecipientService.deleteMedication(actorId, recipientId, medicationId);
    }

    @DeleteMapping("/{recipientId}/emergency-contacts/{contactId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> deleteEmergencyContact(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID contactId) {
        return careRecipientService.deleteEmergencyContact(actorId, recipientId, contactId);
    }

    @DeleteMapping("/{recipientId}/managers/{managerId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> deleteManager(
            @CurrentUserId UUID actorId,
            @PathVariable UUID recipientId,
            @PathVariable UUID managerId) {
        return careRecipientService.deleteManager(actorId, recipientId, managerId);
    }
}
