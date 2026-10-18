package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;
import mss301.carecatalogservice.security.CurrentCenterId;
import mss301.carecatalogservice.service.CenterOfferingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/center-services")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Center offering", description = "Dịch vụ mà trung tâm cung cấp (dựa trên danh mục chuẩn).")
public class CenterOfferingController {
    CenterOfferingService centerOfferingService;

    @PostMapping
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CenterOfferingResponse> create(@CurrentCenterId UUID centerId,
                                                      @Valid @RequestBody CenterOfferingRequest request) {
        return centerOfferingService.create(centerId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CenterOfferingResponse> update(@CurrentCenterId UUID centerId,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody CenterOfferingRequest request) {
        return centerOfferingService.update(centerId, id, request);
    }

    @GetMapping("/center/{centerId}")
    public APIResponse<List<CenterOfferingResponse>> listByCenter(@PathVariable UUID centerId) {
        return centerOfferingService.listByCenter(centerId);
    }

    @GetMapping("/{id}")
    public APIResponse<CenterOfferingResponse> getById(@PathVariable UUID id) {
        return centerOfferingService.getById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<Void> delete(@CurrentCenterId UUID centerId, @PathVariable UUID id) {
        return centerOfferingService.delete(centerId, id);
    }
}
