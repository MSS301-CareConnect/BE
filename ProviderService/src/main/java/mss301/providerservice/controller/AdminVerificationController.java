package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.*;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Verification", description = "Admin xác minh caregiver và chứng chỉ")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVerificationController {
    private final CaregiverService caregiverService;
    private final CertificateService certificateService;

    @PatchMapping("/caregivers/{id}/verification")
    @Operation(summary = "Duyệt hoặc từ chối hồ sơ caregiver")
    public APIResponse<CaregiverResponse> verifyCaregiver(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                                          @Valid @RequestBody VerificationRequest request) {
        return caregiverService.verify(actorId, id, request);
    }

    @PatchMapping("/certificates/{id}/verification")
    @Operation(summary = "Duyệt hoặc từ chối chứng chỉ")
    public APIResponse<CertificateResponse> verifyCertificate(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                                              @Valid @RequestBody VerificationRequest request) {
        return certificateService.verify(actorId, id, request);
    }
}
