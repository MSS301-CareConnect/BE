package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.CareServiceRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CareServiceResponse;

import java.util.List;
import java.util.UUID;

public interface CareServiceService {
    APIResponse<CareServiceResponse> createCareService(CareServiceRequest request);

    APIResponse<CareServiceResponse> updateCareService(UUID id, CareServiceRequest request);

    APIResponse<List<CareServiceResponse>> getAllCareServices();

    APIResponse<CareServiceResponse> getCareServiceById(UUID id);

    APIResponse<Void> deleteCareService(UUID id);
}
