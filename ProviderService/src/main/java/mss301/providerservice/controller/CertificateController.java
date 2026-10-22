package mss301.providerservice.controller;

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

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

    @GetMapping("/caregivers/{caregiverId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<List<CertificateResponse>> list(@CurrentUserId UUID actorId,
                                                       @PathVariable UUID caregiverId) {
        return certificateService.list(actorId, caregiverId);
    }

    @GetMapping("/caregivers/{caregiverId}/verified")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<CertificateResponse>> listVerified(@PathVariable UUID caregiverId) {
        return certificateService.listVerified(caregiverId);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    @PostMapping("/caregivers/{caregiverId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<CertificateResponse> create(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID caregiverId,
                                                   @Valid @RequestBody CertificateRequest request) {
        return certificateService.create(actorId, caregiverId, request);
    }

    // ─── PUT OPERATIONS ───────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<CertificateResponse> update(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody CertificateRequest request) {
        return certificateService.update(actorId, id, request);
    }

    // ─── PATCH OPERATIONS ─────────────────────────────────────────────────────

    @PatchMapping("/{id}/verification")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CertificateResponse> verify(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody VerificationRequest request) {
        return certificateService.verify(actorId, id, request);
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID id,
                                    @RequestParam Long version) {
        return certificateService.delete(actorId, id, version);
    }
}
