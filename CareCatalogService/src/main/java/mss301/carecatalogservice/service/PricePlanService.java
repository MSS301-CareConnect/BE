package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.PricePlanBatchUpdateRequest;
import mss301.carecatalogservice.dto.request.PricePlanDraftRequest;
import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.PageResponse;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.entity.PricePlan;

import java.util.List;
import java.util.UUID;

public interface PricePlanService {
    // Tạo gói giá.
    APIResponse<PricePlanResponse> create(UUID centerId, PricePlanRequest request);

    // Cập nhật một gói giá.
    APIResponse<PricePlanResponse> update(UUID centerId, UUID id, PricePlanRequest request);

    // Cập nhật nhiều gói giá trong một lần.
    APIResponse<List<PricePlanResponse>> updateBatch(UUID centerId, PricePlanBatchUpdateRequest request);

    // Gói gửi kèm khi tạo dịch vụ trung tâm — chạy trong transaction của CenterOfferingService.create.
    List<PricePlan> createForOffering(CenterOffering centerOffering, List<PricePlanDraftRequest> drafts);

    // Danh sách gói giá của một dịch vụ trung tâm.
    APIResponse<List<PricePlanResponse>> listByCenterOffering(UUID centerOfferingId);

    // Danh sách gói giá có phân trang.
    APIResponse<PageResponse<PricePlanResponse>> list(UUID centerOfferingId, int page, int size);

    // Chi tiết một gói giá.
    APIResponse<PricePlanResponse> get(UUID id);

    // Xóa gói giá.
    APIResponse<Void> delete(UUID centerId, UUID id);
}
