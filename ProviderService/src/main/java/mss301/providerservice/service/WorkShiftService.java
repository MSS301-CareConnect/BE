package mss301.providerservice.service;

import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.ShiftStatus;

import java.time.LocalDate;
import java.util.UUID;

public interface WorkShiftService {

    APIResponse<WorkShiftResponse> create(UUID actorId, UUID centerId, WorkShiftRequest request);

    APIResponse<PageResponse<WorkShiftResponse>> list(UUID centerId, ShiftStatus status,
                                                     LocalDate fromDate, LocalDate toDate,
                                                     int page, int size);

    APIResponse<WorkShiftResponse> get(UUID id);

    APIResponse<WorkShiftResponse> update(UUID actorId, UUID id, WorkShiftRequest request);

    APIResponse<Void> delete(UUID actorId, UUID id, Long version);

    WorkShift find(UUID id);

    long approvedCount(UUID shiftId);
}
