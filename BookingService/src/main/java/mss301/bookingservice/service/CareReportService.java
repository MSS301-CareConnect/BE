package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.CareReportRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CareReportResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.RoleCode;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CareReportService {
    // Chi tiết báo cáo chăm sóc.
    APIResponse<CareReportResponse> get(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id);
    // Danh sách báo cáo chăm sóc.
    APIResponse<PageResponse<CareReportResponse>> list(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID bookingId, int page, int size);
    // Tạo báo cáo chăm sóc.
    APIResponse<CareReportResponse> create(UUID actorId, CareReportRequest request);
    // Sửa báo cáo chưa gửi.
    APIResponse<CareReportResponse> update(UUID actorId, UUID id, CareReportRequest request);
    // Gửi báo cáo.
    APIResponse<CareReportResponse> submit(UUID actorId, UUID id);
    // Thêm ảnh vào báo cáo.
    APIResponse<CareReportResponse> addPhotos(UUID actorId, UUID id, List<MultipartFile> files);
    // Xóa ảnh khỏi báo cáo.
    APIResponse<Void> deletePhoto(UUID actorId, UUID id, UUID mediaFileId);
}
