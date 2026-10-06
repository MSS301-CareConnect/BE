package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.CenterServiceRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterServiceResponse;

import java.util.List;
import java.util.UUID;

public interface CenterServiceService {
    APIResponse<CenterServiceResponse> createCenterService(UUID centerId, CenterServiceRequest request);

    APIResponse<CenterServiceResponse> updateCenterService(UUID centerId, UUID id, CenterServiceRequest request);

    APIResponse<List<CenterServiceResponse>> getCenterServicesByCenterId(UUID centerId);

    APIResponse<CenterServiceResponse> getCenterServiceById(UUID id);

    APIResponse<Void> deleteCenterService(UUID centerId, UUID id);
}
