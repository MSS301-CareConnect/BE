package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.CaregiverWithdrawalRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverWithdrawalResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.RoleCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

// Caregiver xin hủy buổi được giao (2026-11-09) — Center Manager của trung tâm phân người khác qua API đổi caregiver sẵn có.
public interface CaregiverWithdrawalService {

    // CENTER_MANAGER: hàng đợi của trung tâm (pendingOnly = chưa phân người khác); CAREGIVER: yêu cầu của chính mình.
    APIResponse<PageResponse<CaregiverWithdrawalResponse>> list(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
                                                                UUID bookingId, boolean pendingOnly, int page, int size);

    // CAREGIVER: một hoặc nhiều buổi của cùng booking; buổi nào không hợp lệ thì từ chối cả lệnh.
    APIResponse<List<CaregiverWithdrawalResponse>> create(UUID actorId, CaregiverWithdrawalRequest request);
}
