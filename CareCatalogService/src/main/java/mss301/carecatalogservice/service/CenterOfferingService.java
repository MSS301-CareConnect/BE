package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.request.CenterOfferingSearchRequest;
import mss301.carecatalogservice.dto.request.PriceSummaryRequest;
import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.request.ServiceStatusRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;
import mss301.carecatalogservice.dto.response.PageResponse;
import mss301.carecatalogservice.dto.response.PriceSummaryResponse;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;
import mss301.carecatalogservice.enums.RoleCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

// Dịch vụ mà trung tâm đăng ký cung cấp, dựa trên danh mục chuẩn (CatalogItem). ADMIN và manager của chính trung tâm
// thấy mọi trạng thái; người khác chỉ thấy dịch vụ đang bán được (offering + danh mục ACTIVE, có gói giá active).
public interface CenterOfferingService {
    APIResponse<PageResponse<CenterOfferingResponse>> listByCenter(Set<RoleCode> roles, UUID scopeCenterId, UUID centerId,
                                                                   CenterOfferingSearchRequest request);

    APIResponse<CenterOfferingResponse> get(Set<RoleCode> roles, UUID scopeCenterId, UUID id);

    // SERVICE (ProviderService, SRCH-02): giá thấp nhất trong khoảng lọc của từng trung tâm.
    APIResponse<List<PriceSummaryResponse>> listPriceSummaries(PriceSummaryRequest request);

    APIResponse<CenterOfferingResponse> create(UUID centerId, CenterOfferingRequest request);

    APIResponse<List<ServiceQuoteResponse>> quote(ServiceQuoteRequest request);

    APIResponse<CenterOfferingResponse> update(UUID centerId, UUID id, CenterOfferingRequest request);

    APIResponse<CenterOfferingResponse> updateStatus(UUID centerId, UUID id, ServiceStatusRequest request);

    // Chỉ khi không còn gói giá active; xóa mềm luôn các gói giá con trong cùng transaction.
    APIResponse<Void> delete(UUID centerId, UUID id);
}
