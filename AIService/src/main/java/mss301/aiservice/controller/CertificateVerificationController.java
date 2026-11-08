package mss301.aiservice.controller;

import io.swagger.v3.oas.annotations.Operation;
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

    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "Kiểm tra chứng chỉ",
            description = """
                    API nội bộ dành cho ProviderService.
                    Đọc nội dung ảnh bằng OCR, kiểm tra QR và so sánh
                    với thông tin chứng chỉ caregiver đã khai báo.
                    """)
    public APIResponse<CertificateVerificationResponse> verify(@Valid @RequestBody CertificateVerificationRequest request) {
        return certificateVerificationService.verify(request);
    }
}
