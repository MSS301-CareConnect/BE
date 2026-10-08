package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.response.*;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CertificateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Certificates", description = "Quản lý chứng chỉ caregiver; không chứa mediaFileId")
@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
public class CertificateController {
    private final CertificateService certificateService;

    @PostMapping("/caregivers/{caregiverId}/certificates")
    @Operation(summary = "Thêm chứng chỉ")
    public APIResponse<CertificateResponse> create(@CurrentUserId UUID actorId, @PathVariable UUID caregiverId,
                                                   @Valid @RequestBody CertificateRequest request) {
        return certificateService.create(actorId, caregiverId, request);
    }

    @GetMapping("/caregivers/{caregiverId}/certificates")
    @Operation(summary = "Danh sách chứng chỉ")
    public APIResponse<List<CertificateResponse>> list(@CurrentUserId UUID actorId,
                                                       @PathVariable UUID caregiverId) {
        return certificateService.list(actorId, caregiverId);
    }

    @PutMapping("/certificates/{id}")
    @Operation(summary = "Cập nhật chứng chỉ", description = "Sau khi sửa, trạng thái xác minh trở lại PENDING.")
    public APIResponse<CertificateResponse> update(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                                   @Valid @RequestBody CertificateRequest request) {
        return certificateService.update(actorId, id, request);
    }

    @DeleteMapping("/certificates/{id}")
    @Operation(summary = "Xóa mềm chứng chỉ")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                    @RequestParam Long version) {
        return certificateService.delete(actorId, id, version);
    }
}
