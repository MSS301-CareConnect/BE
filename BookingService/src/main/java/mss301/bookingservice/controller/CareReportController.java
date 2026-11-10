package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.CareReportRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CareReportResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.service.CareReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-reports")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Care reports", description = "Báo cáo chăm sóc và ảnh theo từng ca")
public class CareReportController {
    CareReportService careReportService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<CareReportResponse> get(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID centerId, @PathVariable UUID id) {
        return careReportService.get(actorId, roles, centerId, id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<PageResponse<CareReportResponse>> list(@CurrentUserId UUID actorId, @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID centerId, @RequestParam(required = false) UUID bookingId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return careReportService.list(actorId, roles, centerId, bookingId, page, size);
    }

    @PostMapping
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CareReportResponse> create(@CurrentUserId UUID actorId, @Valid @RequestBody CareReportRequest request) {
        return careReportService.create(actorId, request);
    }

    @PostMapping(value = "/{id}/photos", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CareReportResponse> addPhotos(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                                    @RequestPart("files") List<MultipartFile> files) {
        return careReportService.addPhotos(actorId, id, files);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CareReportResponse> submit(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return careReportService.submit(actorId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CareReportResponse> update(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                                 @Valid @RequestBody CareReportRequest request) {
        return careReportService.update(actorId, id, request);
    }

    @DeleteMapping("/{id}/photos/{mediaFileId}")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<Void> deletePhoto(@CurrentUserId UUID actorId, @PathVariable UUID id, @PathVariable UUID mediaFileId) {
        return careReportService.deletePhoto(actorId, id, mediaFileId);
    }
}
