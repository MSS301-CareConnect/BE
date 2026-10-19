package mss301.providerservice.service;

import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;

import java.util.UUID;

public interface CaregiverScheduleService {

    APIResponse<PageResponse<ScheduleResponse>> mySchedules(UUID actorId, int page, int size);

    APIResponse<PageResponse<ScheduleResponse>> centerSchedules(UUID actorId, UUID centerId, int page, int size);

    APIResponse<ScheduleResponse> updateStatus(UUID actorId, UUID id, ScheduleStatusRequest request);
}
