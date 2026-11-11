package mss301.aiservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.dto.request.CertificateVerificationRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.CertificateVerificationResponse;
import mss301.aiservice.service.CertificateVerificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/certificate-verifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Certificate Verification", description = "AI kiểm tra sơ bộ thông tin chứng chỉ")
public class CertificateVerificationController {
    CertificateVerificationService certificateVerificationService;

    // Chỉ ProviderService gọi (token SERVICE): đọc ảnh bằng OCR, kiểm QR rồi so với thông tin chứng chỉ đã khai báo.
    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<CertificateVerificationResponse> verify(@Valid @RequestBody CertificateVerificationRequest request) {
        return certificateVerificationService.verify(request);
    }
}
