package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.PricePlanBatchUpdateRequest;
import mss301.carecatalogservice.dto.request.PricePlanDraftRequest;
import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.entity.PricePlan;

import java.util.List;
import java.util.UUID;

public interface PricePlanService {
    APIResponse<PricePlanResponse> create(UUID centerId, PricePlanRequest request);

    APIResponse<PricePlanResponse> update(UUID centerId, UUID id, PricePlanRequest request);

    APIResponse<List<PricePlanResponse>> updateBatch(UUID centerId, PricePlanBatchUpdateRequest request);

    // Gói gửi kèm khi tạo dịch vụ trung tâm — chạy trong transaction của CenterOfferingService.create.
    List<PricePlan> createForOffering(CenterOffering centerOffering, List<PricePlanDraftRequest> drafts);

    APIResponse<List<PricePlanResponse>> listByCenterOffering(UUID centerOfferingId);

    APIResponse<PricePlanResponse> get(UUID id);

    APIResponse<Void> delete(UUID centerId, UUID id);
}
