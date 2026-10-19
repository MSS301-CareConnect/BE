package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CertificateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/certificates")
@Tag(name = "Certificates", description = "Quản lý chứng chỉ caregiver")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CertificateController {

    CertificateService certificateService;

    @PostMapping("/caregivers/{caregiverId}")
    @Operation(summary = "Thêm chứng chỉ cho caregiver")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<CertificateResponse> create(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID caregiverId,
                                                   @Valid @RequestBody CertificateRequest request) {
        return certificateService.create(actorId, caregiverId, request);
    }

    @GetMapping("/caregivers/{caregiverId}")
    @Operation(summary = "Danh sách chứng chỉ của caregiver")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<List<CertificateResponse>> list(@CurrentUserId UUID actorId,
                                                       @PathVariable UUID caregiverId) {
        return certificateService.list(actorId, caregiverId);
    }

    @GetMapping("/caregivers/{caregiverId}/verified")
    @Operation(summary = "Xem danh sách chứng chỉ đã xác minh của caregiver (Catalog public)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<CertificateResponse>> verifiedList(@PathVariable UUID caregiverId) {
        return certificateService.verifiedList(caregiverId);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật chứng chỉ")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<CertificateResponse> update(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody CertificateRequest request) {
        return certificateService.update(actorId, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa mềm chứng chỉ")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID id,
                                    @RequestParam Long version) {
        return certificateService.delete(actorId, id, version);
    }

    @PatchMapping("/{id}/verification")
    @Operation(summary = "Duyệt hoặc từ chối chứng chỉ (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CertificateResponse> verify(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody VerificationRequest request) {
        return certificateService.verify(actorId, id, request);
    }
}
