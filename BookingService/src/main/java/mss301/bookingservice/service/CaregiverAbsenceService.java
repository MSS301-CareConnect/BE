package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.CaregiverAbsenceConclusionRequest;
import mss301.bookingservice.dto.request.CaregiverAbsenceExplanationRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverAbsenceResponse;
import mss301.bookingservice.dto.response.CaregiverAbsenceStatisticsResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.CaregiverAbsenceSource;
import mss301.bookingservice.enums.CaregiverAbsenceStatus;
import mss301.bookingservice.enums.RoleCode;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public interface CaregiverAbsenceService {

    // from / to: ngày của buổi bị vắng theo múi giờ booking (bỏ trống = không giới hạn). centerId chỉ có nghĩa với ADMIN;
    // caregiverId (Provider id) lọc một caregiver cho manager / admin.
    APIResponse<PageResponse<CaregiverAbsenceResponse>> list(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
                                                             CaregiverAbsenceStatus status, UUID centerId,
                                                             UUID caregiverId, UUID bookingId, LocalDate from,
                                                             LocalDate to, int page, int size);

    // Chi tiết một lần vắng mặt.
    APIResponse<CaregiverAbsenceResponse> get(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id);

    // Thống kê vắng mặt.
    APIResponse<CaregiverAbsenceStatisticsResponse> getStatistics(UUID actorId, Set<RoleCode> roles,
                                                                  UUID scopeCenterId, UUID centerId, UUID caregiverId,
                                                                  LocalDate from, LocalDate to);

    // CAREGIVER đã vắng gửi / sửa giải trình khi trung tâm chưa kết luận.
    APIResponse<CaregiverAbsenceResponse> explain(UUID actorId, UUID id, CaregiverAbsenceExplanationRequest request);

    // CENTER_MANAGER cùng trung tâm kết luận một lần: EXCUSED (cần ghi chú) hoặc CONFIRMED.
    APIResponse<CaregiverAbsenceResponse> conclude(UUID actorId, UUID centerId, UUID id,
                                                   CaregiverAbsenceConclusionRequest request);

    // Gọi trong transaction vừa đánh buổi CAREGIVER_MISSED (booking + buổi đã khóa): ghi hồ sơ một lần cho buổi và báo
    // caregiver, trung tâm, khách. Buổi không có caregiver thì chỉ báo khách + trung tâm.
    void record(BookingOccurrence occurrence, CaregiverAbsenceSource source);
}
