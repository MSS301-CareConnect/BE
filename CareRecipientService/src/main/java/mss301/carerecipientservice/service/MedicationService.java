package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.MedicationRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.MedicationResponse;

import java.util.List;
import java.util.UUID;

public interface MedicationService {
    // Thêm thuốc.
    APIResponse<MedicationResponse> create(UUID actorId, UUID recipientId, MedicationRequest request);

    // Danh sách thuốc.
    APIResponse<List<MedicationResponse>> list(UUID actorId, UUID recipientId, Boolean active);

    // Chi tiết một thuốc.
    APIResponse<MedicationResponse> get(UUID actorId, UUID recipientId, UUID id);

    // Cập nhật thuốc.
    APIResponse<MedicationResponse> update(UUID actorId, UUID recipientId, UUID id, MedicationRequest request);

    // Xóa thuốc.
    APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id);

    // Bật / tắt thuốc đang dùng.
    APIResponse<MedicationResponse> updateActive(UUID actorId, UUID recipientId, UUID id, boolean active);

}
