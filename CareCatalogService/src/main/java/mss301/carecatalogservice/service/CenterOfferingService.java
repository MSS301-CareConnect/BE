package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;

import java.util.List;
import java.util.UUID;

// Dịch vụ mà trung tâm đăng ký cung cấp, dựa trên danh mục chuẩn (CatalogItem).
public interface CenterOfferingService {
    APIResponse<CenterOfferingResponse> create(UUID centerId, CenterOfferingRequest request);

    APIResponse<CenterOfferingResponse> update(UUID centerId, UUID id, CenterOfferingRequest request);

    APIResponse<List<CenterOfferingResponse>> listByCenter(UUID centerId);

    APIResponse<CenterOfferingResponse> getById(UUID id);

    APIResponse<Void> delete(UUID centerId, UUID id);
}
