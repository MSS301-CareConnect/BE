package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.CenterBookingPolicyRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CenterBookingPolicyResponse;
import mss301.bookingservice.entity.CenterBookingPolicy;
import mss301.bookingservice.enums.RoleCode;

import java.util.Set;
import java.util.UUID;

// Center booking policy (spec §8): manager sửa trung tâm mình, admin sửa mọi trung tâm; khách xem để biết cọc / mốc hủy.
public interface CenterBookingPolicyService {
    // Chính sách booking của trung tâm.
    APIResponse<CenterBookingPolicyResponse> get(Set<RoleCode> roles, UUID scopeCenterId, UUID centerId);

    // Cập nhật chính sách booking của trung tâm.
    APIResponse<CenterBookingPolicyResponse> update(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID centerId,
                                                    CenterBookingPolicyRequest request);

    // Policy đang áp cho trung tâm (bản lưu hoặc mặc định chưa lưu) — Booking đọc tại thời điểm xử lý, không sửa.
    CenterBookingPolicy findEffective(UUID centerId);
}
