package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.PricePlanResponse;

import java.util.List;
import java.util.UUID;

public interface PricePlanService {
    APIResponse<PricePlanResponse> create(UUID centerId, PricePlanRequest request);

    APIResponse<PricePlanResponse> update(UUID centerId, UUID id, PricePlanRequest request);

    APIResponse<List<PricePlanResponse>> listByCenterOffering(UUID centerOfferingId);

    APIResponse<PricePlanResponse> get(UUID id);

    APIResponse<Void> delete(UUID centerId, UUID id);
}
