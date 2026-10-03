package mss301.carerecipientservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.request.*;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.enums.RecipientStatus;
import mss301.carerecipientservice.security.CurrentUserId;
import mss301.carerecipientservice.service.CareRecipientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/care-recipients")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Care recipient", description = "Hồ sơ người bệnh của khách hàng. Chỉ thao tác trên hồ sơ mình quản lý; không có quyền → 404.")
public class CareRecipientController {
    CareRecipientService careRecipientService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientDetailResponse> create(@CurrentUserId UUID actorId,
                                                           @Valid @RequestBody CareRecipientCreateRequest request) {
        return careRecipientService.create(actorId, request);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> list(@CurrentUserId UUID actorId,
                                                                        @RequestParam(required = false) RecipientStatus status,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size) {
        return careRecipientService.list(actorId, status, keyword, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientDetailResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return careRecipientService.get(actorId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientResponse> update(@CurrentUserId UUID actorId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody CareRecipientUpdateRequest request) {
        return careRecipientService.update(actorId, id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientResponse> updateStatus(@CurrentUserId UUID actorId,
                                                           @PathVariable UUID id,
                                                           @RequestParam RecipientStatus status) {
        return careRecipientService.updateStatus(actorId, id, status);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return careRecipientService.delete(actorId, id);
    }
}
