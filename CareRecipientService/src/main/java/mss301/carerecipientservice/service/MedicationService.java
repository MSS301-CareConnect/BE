package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.MedicationRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.MedicationResponse;

import java.util.List;
import java.util.UUID;

public interface MedicationService {
    APIResponse<MedicationResponse> create(UUID actorId, UUID recipientId, MedicationRequest request);

    APIResponse<List<MedicationResponse>> list(UUID actorId, UUID recipientId, Boolean active);

    APIResponse<MedicationResponse> get(UUID actorId, UUID recipientId, UUID id);

    APIResponse<MedicationResponse> update(UUID actorId, UUID recipientId, UUID id, MedicationRequest request);

    APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id);

    APIResponse<MedicationResponse> updateActive(UUID actorId, UUID recipientId, UUID id, boolean active);

}
