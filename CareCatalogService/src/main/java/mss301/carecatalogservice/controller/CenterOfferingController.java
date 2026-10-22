package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.*;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;
import mss301.carecatalogservice.dto.response.PageResponse;
import mss301.carecatalogservice.dto.response.PriceSummaryResponse;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;
import mss301.carecatalogservice.enums.RoleCode;
import mss301.carecatalogservice.security.CurrentCenterId;
import mss301.carecatalogservice.security.CurrentRoles;
import mss301.carecatalogservice.service.CenterOfferingService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/center-services")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Center offering", description = "Dịch vụ mà trung tâm cung cấp (dựa trên danh mục chuẩn).")
public class CenterOfferingController {
    CenterOfferingService centerOfferingService;

    @GetMapping("/center/{centerId}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<PageResponse<CenterOfferingResponse>> listByCenter(@CurrentRoles Set<RoleCode> roles,
                                                                          @CurrentCenterId(required = false) UUID scopeCenterId,
                                                                          @PathVariable UUID centerId,
                                                                          @ParameterObject CenterOfferingSearchRequest request) {
        return centerOfferingService.listByCenter(roles, scopeCenterId, centerId, request);
    }

    @GetMapping("/price-summary")
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<List<PriceSummaryResponse>> listPriceSummaries(@Valid @ParameterObject PriceSummaryRequest request) {
        return centerOfferingService.listPriceSummaries(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CenterOfferingResponse> get(@CurrentRoles Set<RoleCode> roles,
                                                   @CurrentCenterId(required = false) UUID scopeCenterId,
                                                   @PathVariable UUID id) {
        return centerOfferingService.get(roles, scopeCenterId, id);
    }

    @PostMapping
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CenterOfferingResponse> create(@CurrentCenterId UUID centerId,
                                                      @Valid @RequestBody CenterOfferingRequest request) {
        return centerOfferingService.create(centerId, request);
    }

    @PostMapping("/quote")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<ServiceQuoteResponse>> quote(@Valid @RequestBody ServiceQuoteRequest request) {
        return centerOfferingService.quote(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CenterOfferingResponse> update(@CurrentCenterId UUID centerId,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody CenterOfferingRequest request) {
        return centerOfferingService.update(centerId, id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CenterOfferingResponse> updateStatus(@CurrentCenterId UUID centerId,
                                                            @PathVariable UUID id,
                                                            @Valid @RequestBody ServiceStatusRequest request) {
        return centerOfferingService.updateStatus(centerId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<Void> delete(@CurrentCenterId UUID centerId, @PathVariable UUID id) {
        return centerOfferingService.delete(centerId, id);
    }
}
