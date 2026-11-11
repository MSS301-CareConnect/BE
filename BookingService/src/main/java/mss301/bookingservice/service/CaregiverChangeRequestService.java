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

    // Khách tạo yêu cầu đổi người chăm sóc.
    APIResponse<CaregiverChangeRequestResponse> create(UUID actorId, UUID bookingId, CaregiverChangeCreateRequest request);

    // Danh sách yêu cầu đổi người của booking.
    APIResponse<List<CaregiverChangeRequestResponse>> listByBooking(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID bookingId);

    // Danh sách yêu cầu đổi người của trung tâm.
    APIResponse<PageResponse<CaregiverChangeRequestResponse>> list(UUID actorId, UUID centerId, CaregiverChangeRequestStatus status, int page, int size);

    // Trung tâm duyệt yêu cầu đổi người.
    APIResponse<CaregiverChangeRequestResponse> approve(UUID actorId, UUID centerId, UUID requestId, CaregiverChangeApproveRequest request);

    // Trung tâm từ chối yêu cầu đổi người.
    APIResponse<CaregiverChangeRequestResponse> reject(UUID actorId, UUID centerId, UUID requestId, CaregiverChangeRejectRequest request);

    // Khách hủy yêu cầu đổi người.
    APIResponse<CaregiverChangeRequestResponse> cancel(UUID actorId, UUID requestId);
}
