package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.PricePlanResponse;

import java.util.List;
import java.util.UUID;

public interface PricePlanService {
    APIResponse<PricePlanResponse> createPricePlan(UUID centerId, PricePlanRequest request);

    APIResponse<PricePlanResponse> updatePricePlan(UUID centerId, UUID id, PricePlanRequest request);

    APIResponse<List<PricePlanResponse>> getPricePlansByCenterServiceId(UUID centerServiceId);

    APIResponse<PricePlanResponse> getPricePlanById(UUID id);

    APIResponse<Void> deletePricePlan(UUID centerId, UUID id);
}
