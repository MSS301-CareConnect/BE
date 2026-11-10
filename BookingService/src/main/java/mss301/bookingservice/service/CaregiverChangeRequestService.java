package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.CaregiverChangeApproveRequest;
import mss301.bookingservice.dto.request.CaregiverChangeCreateRequest;
import mss301.bookingservice.dto.request.CaregiverChangeRejectRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverChangeRequestResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.CaregiverChangeRequestStatus;
import mss301.bookingservice.enums.RoleCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CaregiverChangeRequestService {

    APIResponse<CaregiverChangeRequestResponse> createRequest(UUID actorId, UUID bookingId, CaregiverChangeCreateRequest request);

    APIResponse<List<CaregiverChangeRequestResponse>> listRequestsByBooking(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID bookingId);

    APIResponse<PageResponse<CaregiverChangeRequestResponse>> listCenterRequests(UUID actorId, UUID centerId, CaregiverChangeRequestStatus status, int page, int size);

    APIResponse<CaregiverChangeRequestResponse> approveRequest(UUID actorId, UUID centerId, UUID requestId, CaregiverChangeApproveRequest request);

    APIResponse<CaregiverChangeRequestResponse> rejectRequest(UUID actorId, UUID centerId, UUID requestId, CaregiverChangeRejectRequest request);

    APIResponse<CaregiverChangeRequestResponse> cancelRequest(UUID actorId, UUID requestId);
}
