package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.CatalogItemRequest;
import mss301.carecatalogservice.dto.request.CatalogItemSearchRequest;
import mss301.carecatalogservice.dto.request.ServiceStatusRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CatalogImageResponse;
import mss301.carecatalogservice.dto.response.CatalogItemResponse;
import mss301.carecatalogservice.dto.response.PageResponse;
import mss301.carecatalogservice.enums.RoleCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

// Danh mục dịch vụ chuẩn: ADMIN quản lý; ADMIN / CENTER_MANAGER thấy mọi trạng thái, người khác chỉ thấy ACTIVE.
public interface CatalogItemService {
    APIResponse<PageResponse<CatalogItemResponse>> list(Set<RoleCode> roles, CatalogItemSearchRequest request);

    APIResponse<CatalogItemResponse> get(Set<RoleCode> roles, UUID id);

    // Ảnh minh họa: 302 tới URL tải có hạn của MediaService (ngoại lệ rule #3 — thẻ <img> không đọc được APIResponse).
    ResponseEntity<Void> redirectToImage(UUID mediaFileId);

    APIResponse<CatalogItemResponse> create(CatalogItemRequest request);

    // ADMIN tải ảnh minh họa lên MediaService; trả đường dẫn cố định để lưu vào imageUrl của dịch vụ chuẩn.
    APIResponse<CatalogImageResponse> uploadImage(UUID actorId, MultipartFile file);

    APIResponse<CatalogItemResponse> update(UUID id, CatalogItemRequest request);

    APIResponse<CatalogItemResponse> updateStatus(UUID id, ServiceStatusRequest request);

    // Chỉ xóa mềm khi không trung tâm nào còn dùng (offering ACTIVE hoặc gói giá active) — ngược lại 409, dùng updateStatus.
    APIResponse<Void> delete(UUID id);
}
