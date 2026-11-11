package mss301.providerservice.service;

import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterRestoreRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.dto.request.CenterSelfUpdateRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.ProviderStatisticsResponse;
import mss301.providerservice.dto.response.CenterCardResponse;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.enums.CenterStatus;

import java.math.BigDecimal;
import java.util.UUID;

public interface CareCenterService {

    // Tạo trung tâm.
    APIResponse<CenterResponse> create(UUID actorId, CenterCreateRequest request);

    // Danh sách trung tâm cho admin.
    APIResponse<PageResponse<CenterResponse>> list(CenterStatus status, String keyword, boolean includeDeleted,
                                                   int page, int size);

    // Chi tiết trung tâm.
    APIResponse<CenterResponse> get(UUID id);

    // Danh sách trung tâm công khai có lọc dịch vụ và giá.
    APIResponse<PageResponse<CenterCardResponse>> listPublic(String keyword, UUID careServiceId, BigDecimal minPrice,
                                                             BigDecimal maxPrice, int page, int size);

    // Thẻ thông tin công khai của trung tâm.
    APIResponse<CenterCardResponse> getPublic(UUID centerId);

    // Admin cập nhật trung tâm.
    APIResponse<CenterResponse> update(UUID actorId, UUID id, CenterUpdateRequest request);

    // Manager cập nhật trung tâm của mình.
    APIResponse<CenterResponse> updateMine(UUID actorId, CenterSelfUpdateRequest request);

    // Đổi trạng thái trung tâm.
    APIResponse<CenterResponse> updateStatus(UUID actorId, UUID id, CenterStatusUpdateRequest request);

    // Lưu trữ trung tâm.
    APIResponse<Void> delete(UUID actorId, UUID id, Long version);

    // Khôi phục trung tâm đã lưu trữ.
    APIResponse<CenterResponse> restore(UUID actorId, UUID id, CenterRestoreRequest request);

    // Thống kê trung tâm và caregiver.
    APIResponse<ProviderStatisticsResponse> statistics();
}
