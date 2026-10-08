package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mss301.providerservice.dto.response.*;
import mss301.providerservice.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/catalog/caregivers")
@RequiredArgsConstructor
@Tag(name = "Provider Catalog", description = "Danh mục caregiver đã được xác minh")
@PreAuthorize("isAuthenticated()")
public class ProviderCatalogController {
    private final CaregiverService caregiverService;
    private final CertificateService certificateService;

    @GetMapping
    @Operation(summary = "Tìm caregiver đang hoạt động")
    public APIResponse<PageResponse<CaregiverResponse>> list(
            @RequestParam(required = false) UUID centerId, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return caregiverService.catalog(centerId, keyword, page, size);
    }

    @GetMapping("/{caregiverId}/certificates")
    @Operation(summary = "Xem chứng chỉ đã xác minh của caregiver")
    public APIResponse<List<CertificateResponse>> certificates(@PathVariable UUID caregiverId) {
        return certificateService.verifiedList(caregiverId);
    }
}
