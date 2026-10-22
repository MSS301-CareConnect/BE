package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.security.CurrentCenterId;
import mss301.carecatalogservice.service.PricePlanService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/price-plans")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Price plan", description = "Bảng giá theo giờ / ngày / ca của dịch vụ trung tâm.")
public class PricePlanController {
    PricePlanService pricePlanService;

    @GetMapping({"/center-offering/{centerOfferingId}", "/center-service/{centerOfferingId}"})
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<PricePlanResponse>> listByCenterOffering(@PathVariable UUID centerOfferingId) {
        return pricePlanService.listByCenterOffering(centerOfferingId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<PricePlanResponse> get(@PathVariable UUID id) {
        return pricePlanService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PricePlanResponse> create(@CurrentCenterId UUID centerId,
                                                 @Valid @RequestBody PricePlanRequest request) {
        return pricePlanService.create(centerId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PricePlanResponse> update(@CurrentCenterId UUID centerId,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody PricePlanRequest request) {
        return pricePlanService.update(centerId, id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<Void> delete(@CurrentCenterId UUID centerId,
                                    @PathVariable UUID id) {
        return pricePlanService.delete(centerId, id);
    }
}
