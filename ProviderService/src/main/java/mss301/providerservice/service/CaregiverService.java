package mss301.providerservice.service;

import mss301.providerservice.dto.request.AvailabilityCheckRequest;
import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.CaregiverSearchRequest;
import mss301.providerservice.dto.request.CaregiverSkillRequest;
import mss301.providerservice.dto.request.CaregiverUpdateRequest;
import mss301.providerservice.dto.request.CaregiverStatusRequest;
import mss301.providerservice.dto.request.CaregiverRatingRequest;
import mss301.providerservice.dto.request.TimeOffRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.AvailabilityCheckResponse;
import mss301.providerservice.dto.response.AvailabilityWindowResponse;
import mss301.providerservice.dto.response.CaregiverCardResponse;
import mss301.providerservice.dto.response.CaregiverPublicResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.CaregiverSkillResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.TimeOffResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CaregiverService {

    // Tạo hồ sơ caregiver.
    APIResponse<CaregiverResponse> create(UUID actorId, UUID centerId, CaregiverCreateRequest request);

    // Danh sách caregiver của trung tâm.
    APIResponse<PageResponse<CaregiverResponse>> listByCenter(UUID actorId, UUID centerId,
                                                            VerificationStatus verification,
                                                            CaregiverStatus status, int page, int size);

    // Chi tiết hồ sơ caregiver.
    APIResponse<CaregiverResponse> get(UUID actorId, UUID caregiverId);

    // Hồ sơ caregiver theo userId.
    APIResponse<CaregiverResponse> getByUser(UUID userId);

    // Hồ sơ caregiver của người đang đăng nhập.
    APIResponse<CaregiverResponse> getMe(UUID actorId);

    // Danh sách thẻ caregiver công khai.
    APIResponse<PageResponse<CaregiverCardResponse>> list(UUID centerId, String keyword, int page, int size);

    // Tìm caregiver theo kỹ năng, kinh nghiệm, giới tính, giá, đánh giá, lịch rảnh.
    APIResponse<PageResponse<CaregiverCardResponse>> search(CaregiverSearchRequest request);

    // Hồ sơ công khai của caregiver.
    APIResponse<CaregiverPublicResponse> getPublic(UUID caregiverId);

    // Cập nhật hồ sơ caregiver.
    APIResponse<CaregiverResponse> update(UUID actorId, UUID caregiverId, CaregiverUpdateRequest request);

    // Cập nhật điểm đánh giá caregiver.
    APIResponse<Void> updateRating(UUID caregiverId, CaregiverRatingRequest request);

    // Đổi trạng thái hoạt động caregiver.
    APIResponse<CaregiverResponse> updateStatus(UUID actorId, UUID caregiverId, CaregiverStatusRequest request);

    // Xác minh hồ sơ caregiver.
    APIResponse<CaregiverResponse> verify(UUID actorId, UUID caregiverId, VerificationRequest request);

    // Lưu trữ hồ sơ caregiver.
    APIResponse<Void> archive(UUID actorId, UUID caregiverId, Long version);

    // Danh sách dịch vụ caregiver làm được.
    APIResponse<List<CaregiverSkillResponse>> listServices(UUID actorId, UUID caregiverId);

    // Thay danh sách dịch vụ caregiver làm được.
    APIResponse<List<CaregiverSkillResponse>> replaceServices(UUID actorId, UUID caregiverId,
                                                               CaregiverSkillRequest request);

    // Danh sách ngày nghỉ của caregiver.
    APIResponse<List<TimeOffResponse>> listTimeOffs(UUID actorId, UUID caregiverId, Instant from, Instant to);

    // Ngày nghỉ của cả trung tâm (hoặc một caregiver của trung tâm) giao [from, to), sắp theo giờ bắt đầu.
    APIResponse<PageResponse<TimeOffResponse>> listCenterTimeOffs(UUID actorId, UUID centerId, UUID caregiverId,
                                                                  Instant from, Instant to, int page, int size);

    // Thêm ngày nghỉ.
    APIResponse<TimeOffResponse> createTimeOff(UUID actorId, UUID caregiverId, TimeOffRequest request);

    // Xóa ngày nghỉ.
    APIResponse<Void> deleteTimeOff(UUID actorId, UUID caregiverId, UUID timeOffId);

    // Khung giờ rảnh của caregiver.
    APIResponse<List<AvailabilityWindowResponse>> getAvailability(UUID caregiverId, Instant from, Instant to);

    // Kiểm tra caregiver có rảnh các khung giờ booking yêu cầu không.
    APIResponse<AvailabilityCheckResponse> checkAvailability(AvailabilityCheckRequest request);

    // Tìm caregiver chưa xóa theo userId.
    CaregiverProfile findActiveCaregiverByUser(UUID userId);

    // Tìm caregiver chưa xóa theo id.
    CaregiverProfile findActiveCaregiver(UUID id);

    // Caregiver khách đặt được (spec booking §5.1 / §6): VERIFIED + ACTIVE, trung tâm ACTIVE; ngược lại 404 như không tồn
    // tại (không lộ trạng thái nội bộ).
    CaregiverProfile findBookableCaregiver(UUID id);
    // Danh sách caregiver chờ xác minh.
    APIResponse<PageResponse<CaregiverResponse>> listPendingVerification(UUID centerId, int page, int size);

    // Danh bạ caregiver cho admin có lọc và phân trang.
    APIResponse<PageResponse<CaregiverResponse>> listDirectory(UUID centerId, VerificationStatus verification,
                                                               CaregiverStatus status, String keyword,
                                                               int page, int size);
}
