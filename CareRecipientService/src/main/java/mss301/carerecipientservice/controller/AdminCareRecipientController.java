package mss301.carerecipientservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.enums.RecipientStatus;
import mss301.carerecipientservice.service.CareRecipientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/care-recipients")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Admin - Care recipient", description = "ADMIN xem hồ sơ người bệnh toàn hệ thống (chỉ đọc), dùng khi xử lý khiếu nại.")
public class AdminCareRecipientController {
    CareRecipientService careRecipientService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> list(@RequestParam(required = false) RecipientStatus status,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(required = false) UUID createdBy,
                                                                        @RequestParam(defaultValue = "false") boolean includeDeleted,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size) {
        return careRecipientService.listForAdmin(status, keyword, createdBy, includeDeleted, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CareRecipientDetailResponse> get(@PathVariable UUID id) {
        return careRecipientService.getForAdmin(id);
    }
}
