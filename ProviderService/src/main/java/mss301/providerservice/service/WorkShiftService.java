package mss301.providerservice.service;

import mss301.providerservice.dto.request.WorkShiftBatchRequest;
import mss301.providerservice.dto.request.WorkShiftCancelRequest;
import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.request.WorkShiftStatusRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.dto.response.WorkShiftStatisticsResponse;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.ShiftStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkShiftService {

    // Tạo ca.
    APIResponse<WorkShiftResponse> create(UUID actorId, UUID centerId, WorkShiftRequest request);

    // Tạo ca cho nhiều ngày.
    APIResponse<List<WorkShiftResponse>> createBatch(UUID actorId, UUID centerId, WorkShiftBatchRequest request);

    // Danh sách ca.
    APIResponse<PageResponse<WorkShiftResponse>> list(UUID actorId, UUID centerId, ShiftStatus status,
                                                     LocalDate fromDate, LocalDate toDate, String shiftType,
                                                     boolean hasVacancy, boolean hasPending, int page, int size);

    // Thống kê ca.
    APIResponse<WorkShiftStatisticsResponse> statistics(UUID actorId, UUID centerId, LocalDate fromDate,
                                                       LocalDate toDate, String shiftType);

    // Chi tiết ca.
    APIResponse<WorkShiftResponse> get(UUID actorId, UUID id);

    // Cập nhật ca.
    APIResponse<WorkShiftResponse> update(UUID actorId, UUID id, WorkShiftRequest request);

    // Đổi trạng thái ca.
    APIResponse<WorkShiftResponse> changeStatus(UUID actorId, UUID id, WorkShiftStatusRequest request);

    // Hủy ca.
    APIResponse<WorkShiftResponse> cancel(UUID actorId, UUID id, WorkShiftCancelRequest request);

    // Xóa ca.
    APIResponse<Void> delete(UUID actorId, UUID id, Long version);

    // Tìm ca chưa xóa.
    WorkShift findActiveShift(UUID id);

    // Đếm đăng ký đã duyệt của ca.
    long countApproved(UUID shiftId);
}
