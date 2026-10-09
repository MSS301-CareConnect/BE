package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;

import java.util.UUID;

// Caregiver xem hồ sơ người bệnh mình được phân công (BookingService xác nhận). Không được phân công → 404.
public interface CaregiverRecipientService {
    APIResponse<RecipientSnapshotResponse> getSnapshot(UUID actorId, UUID recipientId);
}
