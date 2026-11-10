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

    APIResponse<WorkShiftResponse> create(UUID actorId, UUID centerId, WorkShiftRequest request);

    APIResponse<List<WorkShiftResponse>> createBatch(UUID actorId, UUID centerId, WorkShiftBatchRequest request);

    APIResponse<PageResponse<WorkShiftResponse>> list(UUID actorId, UUID centerId, ShiftStatus status,
                                                     LocalDate fromDate, LocalDate toDate, String shiftType,
                                                     boolean hasVacancy, boolean hasPending, int page, int size);

    APIResponse<WorkShiftStatisticsResponse> statistics(UUID actorId, UUID centerId, LocalDate fromDate,
                                                       LocalDate toDate, String shiftType);

    APIResponse<WorkShiftResponse> get(UUID actorId, UUID id);

    APIResponse<WorkShiftResponse> update(UUID actorId, UUID id, WorkShiftRequest request);

    APIResponse<WorkShiftResponse> changeStatus(UUID actorId, UUID id, WorkShiftStatusRequest request);

    APIResponse<WorkShiftResponse> cancel(UUID actorId, UUID id, WorkShiftCancelRequest request);

    APIResponse<Void> delete(UUID actorId, UUID id, Long version);

    WorkShift findActiveShift(UUID id);

    long countApproved(UUID shiftId);
}
