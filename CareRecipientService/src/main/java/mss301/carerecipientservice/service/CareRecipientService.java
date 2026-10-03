package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.enums.RecipientStatus;

import java.util.UUID;

public interface CareRecipientService {
    // Customer (chỉ hồ sơ mình quản lý)
    APIResponse<CareRecipientDetailResponse> create(UUID actorId, CareRecipientCreateRequest request);

    APIResponse<PageResponse<CareRecipientSummaryResponse>> list(UUID actorId, RecipientStatus status, String keyword,
                                                                 int page, int size);

    APIResponse<CareRecipientDetailResponse> get(UUID actorId, UUID id);

    APIResponse<CareRecipientResponse> update(UUID actorId, UUID id, CareRecipientUpdateRequest request);

    APIResponse<CareRecipientResponse> updateStatus(UUID actorId, UUID id, RecipientStatus status);

    APIResponse<Void> delete(UUID actorId, UUID id);

    // Admin (toàn hệ thống, chỉ đọc)
    APIResponse<PageResponse<CareRecipientSummaryResponse>> listForAdmin(RecipientStatus status, String keyword,
                                                                         UUID createdBy, boolean includeDeleted,
                                                                         int page, int size);

    APIResponse<CareRecipientDetailResponse> getForAdmin(UUID id);
}
