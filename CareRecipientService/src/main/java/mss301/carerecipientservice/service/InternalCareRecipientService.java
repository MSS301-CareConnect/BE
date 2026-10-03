package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.enums.RecipientAccessLevel;

import java.util.List;
import java.util.UUID;

// API cho service khác (Booking, Caregiver…) qua /internal/** — không có người dùng cuối.
public interface InternalCareRecipientService {
    APIResponse<RecipientSnapshotResponse> getSnapshot(UUID id);

    APIResponse<AccessCheckResponse> checkAccess(UUID id, UUID userId, RecipientAccessLevel minLevel);

    APIResponse<List<CareRecipientSummaryResponse>> list(RecipientBatchRequest request);
}
