package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.CenterServiceRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterServiceResponse;
import mss301.carecatalogservice.service.CenterServiceService;
import mss301.carecatalogservice.security.CurrentCenterId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/center-services")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Center service", description = "Dịch vụ mà trung tâm cung cấp (dựa trên danh mục chuẩn).")
public class CenterServiceController {
    CenterServiceService centerServiceService;

    @PostMapping
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CenterServiceResponse> createCenterService(@CurrentCenterId UUID centerId,
                                                                  @Valid @RequestBody CenterServiceRequest request) {
        return centerServiceService.createCenterService(centerId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CenterServiceResponse> updateCenterService(@CurrentCenterId UUID centerId,
                                                                  @PathVariable UUID id,
                                                                  @Valid @RequestBody CenterServiceRequest request) {
        return centerServiceService.updateCenterService(centerId, id, request);
    }

    @GetMapping("/center/{centerId}")
    public APIResponse<List<CenterServiceResponse>> getCenterServicesByCenterId(@PathVariable UUID centerId) {
        return centerServiceService.getCenterServicesByCenterId(centerId);
    }

    @GetMapping("/{id}")
    public APIResponse<CenterServiceResponse> getCenterServiceById(@PathVariable UUID id) {
        return centerServiceService.getCenterServiceById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<Void> deleteCenterService(@CurrentCenterId UUID centerId,
                                                 @PathVariable UUID id) {
        return centerServiceService.deleteCenterService(centerId, id);
    }
}
