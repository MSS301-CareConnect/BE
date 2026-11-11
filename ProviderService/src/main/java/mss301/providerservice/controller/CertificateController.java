package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.CertificateVerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificatePendingAuditStatisticsResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.enums.CertificateAiCheckStatus;
import mss301.providerservice.enums.CertificateRiskLevel;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CertificateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import mss301.providerservice.dto.response.PageResponse;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/certificates")
@Tag(name = "Certificates", description = "Quản lý chứng chỉ caregiver — khai báo, trung tâm xác nhận, AI đọc ảnh, admin duyệt")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CertificateController {

    CertificateService certificateService;

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

    // Hàng chờ ADMIN duyệt (bước 5): chứng chỉ trung tâm đã xác nhận, kèm kết quả AI; rủi ro cao lên đầu.
    @GetMapping("/pending-audit")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<PageResponse<CertificateResponse>> listPendingAudit(@RequestParam(required = false) UUID centerId,
                                                                           @RequestParam(required = false) CertificateRiskLevel riskLevel,
                                                                           @RequestParam(required = false) CertificateAiCheckStatus aiCheckStatus,
                                                                           @RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        return certificateService.listPendingAudit(centerId, riskLevel, aiCheckStatus, page, size);
    }

    // API thống kê chứng chỉ chờ admin duyệt theo mức rủi ro.
    @GetMapping("/pending-audit/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CertificatePendingAuditStatisticsResponse> getPendingAuditStatistics(
            @RequestParam(required = false) UUID centerId) {
        return certificateService.getPendingAuditStatistics(centerId);
    }

    // API danh sách chứng chỉ của caregiver.
    @GetMapping("/caregivers/{caregiverId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<List<CertificateResponse>> list(@CurrentUserId UUID actorId,
                                                       @PathVariable UUID caregiverId) {
        return certificateService.list(actorId, caregiverId);
    }

    // API danh sách chứng chỉ đã xác minh (công khai).
    @GetMapping("/caregivers/{caregiverId}/verified")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<CertificateResponse>> listVerified(@PathVariable UUID caregiverId) {
        return certificateService.listVerified(caregiverId);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    // API thêm chứng chỉ cho caregiver.
    @PostMapping("/caregivers/{caregiverId}")
    @PreAuthorize("hasAnyRole('CAREGIVER')")
    public APIResponse<CertificateResponse> create(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID caregiverId,
                                                   @Valid @RequestBody CertificateRequest request) {
        return certificateService.create(actorId, caregiverId, request);
    }

    // Bước 4: ADMIN chạy lại kiểm tra AI (AI lỗi, hoặc muốn đọc lại ảnh). Kết quả mới thay kết quả cũ.
    @PostMapping("/{id}/ai-check")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CertificateResponse> startAiCheck(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return certificateService.startAiCheck(actorId, id);
    }

    // ─── PUT OPERATIONS ───────────────────────────────────────────────────────

    // API cập nhật chứng chỉ.
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CAREGIVER')")
    public APIResponse<CertificateResponse> update(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody CertificateRequest request) {
        return certificateService.update(actorId, id, request);
    }

    // ─── PATCH OPERATIONS ─────────────────────────────────────────────────────

    // API manager xác nhận chứng chỉ.
    @PatchMapping("/{id}/center-verification")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CertificateResponse> verifyCenter(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                                         @Valid @RequestBody CertificateVerificationRequest request) {
        return certificateService.verifyCenter(actorId, id, request);
    }

    // API admin duyệt chứng chỉ.
    @PatchMapping("/{id}/verification")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CertificateResponse> verify(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody CertificateVerificationRequest request) {
        return certificateService.verify(actorId, id, request);
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    // API xóa chứng chỉ.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CAREGIVER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID id,
                                    @RequestParam Long version) {
        return certificateService.delete(actorId, id, version);
    }

}
