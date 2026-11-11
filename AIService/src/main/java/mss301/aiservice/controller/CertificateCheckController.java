package mss301.aiservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.dto.request.CertificateCheckRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.CertificateCheckResponse;
import mss301.aiservice.service.CertificateCheckService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/certificate-checks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "AI Certificate Check", description = "Đọc ảnh chứng chỉ và đánh giá mức rủi ro (service-to-service)")
public class CertificateCheckController {
    CertificateCheckService certificateCheckService;

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    // API cho ProviderService: AI đối chiếu ảnh chứng chỉ với thông tin đã khai báo.
    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<CertificateCheckResponse> check(@Valid @RequestBody CertificateCheckRequest request) {
        return certificateCheckService.check(request);
    }
}
