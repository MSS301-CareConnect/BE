package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;

import java.util.List;
import java.util.UUID;

public interface CareRecipientService {
    // Customer
    APIResponse<CareRecipientDetailResponse> create(UUID actorId, CareRecipientCreateRequest request);

    APIResponse<PageResponse<CareRecipientSummaryResponse>> list(UUID actorId, RecipientStatus status, String keyword,
                                                                 int page, int size);

    APIResponse<CareRecipientDetailResponse> get(UUID actorId, UUID id);

    APIResponse<CareRecipientResponse> update(UUID actorId, UUID id, CareRecipientUpdateRequest request);

    APIResponse<CareRecipientResponse> updateStatus(UUID actorId, UUID id, RecipientStatus status);

    APIResponse<Void> delete(UUID actorId, UUID id);

    // Admin
    APIResponse<PageResponse<CareRecipientSummaryResponse>> listForAdmin(RecipientStatus status, String keyword,
                                                                         UUID createdBy, boolean includeDeleted,
                                                                         int page, int size);

    APIResponse<CareRecipientDetailResponse> getForAdmin(UUID id);

    // Caregiver (hồ sơ người bệnh caregiver được phân công — Booking xác nhận)
    APIResponse<RecipientSnapshotResponse> getSnapshotForCaregiver(UUID actorId, UUID id);

    // Inter-Service / Public contract (thay thế /internal/**)
    APIResponse<RecipientSnapshotResponse> getSnapshot(UUID id);

    APIResponse<AccessCheckResponse> checkAccess(UUID id, UUID userId, RecipientAccessLevel minLevel);

    APIResponse<List<CareRecipientSummaryResponse>> listBatch(RecipientBatchRequest request);
}
