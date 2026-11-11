package mss301.providerservice.service;

import mss301.providerservice.dto.request.RegistrationBulkReviewRequest;
import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.enums.RegistrationStatus;

import java.util.List;
import java.util.UUID;

public interface ShiftRegistrationService {

    // Đăng ký nhận ca.
    APIResponse<ShiftRegistrationResponse> register(UUID actorId, UUID shiftId, ShiftRegistrationRequest request);

    // Danh sách đăng ký của ca.
    APIResponse<PageResponse<ShiftRegistrationResponse>> listByShift(UUID actorId, UUID shiftId,
                                                                     RegistrationStatus status, int page, int size);

    // Danh sách đăng ký của caregiver.
    APIResponse<PageResponse<ShiftRegistrationResponse>> listMine(UUID actorId, RegistrationStatus status,
                                                                  int page, int size);

    // Duyệt / từ chối đăng ký.
    APIResponse<ShiftRegistrationResponse> review(UUID actorId, UUID registrationId, RegistrationReviewRequest request);

    // Duyệt / từ chối nhiều đăng ký.
    APIResponse<List<ShiftRegistrationResponse>> bulkReview(UUID actorId, UUID shiftId,
                                                            RegistrationBulkReviewRequest request);

    // Hủy đăng ký.
    APIResponse<Void> cancel(UUID actorId, UUID registrationId, Long version);
}
