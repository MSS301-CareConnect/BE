package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.service.PricePlanService;
import mss301.carecatalogservice.security.CurrentCenterId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/price-plans")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Price plan", description = "Bảng giá theo giờ / ngày / ca của dịch vụ trung tâm.")
public class PricePlanController {
    PricePlanService pricePlanService;

    @PostMapping
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PricePlanResponse> createPricePlan(@CurrentCenterId UUID centerId,
                                                          @Valid @RequestBody PricePlanRequest request) {
        return pricePlanService.createPricePlan(centerId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PricePlanResponse> updatePricePlan(@CurrentCenterId UUID centerId,
                                                          @PathVariable UUID id,
                                                          @Valid @RequestBody PricePlanRequest request) {
        return pricePlanService.updatePricePlan(centerId, id, request);
    }

    @GetMapping("/center-service/{centerServiceId}")
    public APIResponse<List<PricePlanResponse>> getPricePlansByCenterServiceId(@PathVariable UUID centerServiceId) {
        return pricePlanService.getPricePlansByCenterServiceId(centerServiceId);
    }

    @GetMapping("/{id}")
    public APIResponse<PricePlanResponse> getPricePlanById(@PathVariable UUID id) {
        return pricePlanService.getPricePlanById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<Void> deletePricePlan(@CurrentCenterId UUID centerId,
                                             @PathVariable UUID id) {
        return pricePlanService.deletePricePlan(centerId, id);
    }
}
