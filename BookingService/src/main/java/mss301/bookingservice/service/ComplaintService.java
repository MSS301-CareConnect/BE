package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.ComplaintRequest;
import mss301.bookingservice.dto.request.ComplaintAdminResolutionRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.ComplaintResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.ComplaintAction;
import mss301.bookingservice.enums.ComplaintStatus;
import mss301.bookingservice.enums.ComplaintLevel;
import mss301.bookingservice.enums.RoleCode;
import java.util.UUID;
import java.util.Set;

public interface ComplaintService {
    APIResponse<PageResponse<ComplaintResponse>> list(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
            ComplaintStatus status, ComplaintLevel level, UUID centerId, int page, int size);
    APIResponse<ComplaintResponse> get(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id);
    APIResponse<ComplaintResponse> create(UUID actorId, ComplaintRequest request);
    APIResponse<ComplaintResponse> transition(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id, ComplaintAction action, String note);
    APIResponse<ComplaintResponse> adminResolve(UUID actorId, UUID id, ComplaintAdminResolutionRequest request);
}
