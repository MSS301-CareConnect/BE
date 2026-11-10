package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.EmergencyContactRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.EmergencyContactResponse;

import java.util.List;
import java.util.UUID;

public interface EmergencyContactService {
    APIResponse<EmergencyContactResponse> create(UUID actorId, UUID recipientId, EmergencyContactRequest request);

    APIResponse<List<EmergencyContactResponse>> list(UUID actorId, UUID recipientId);

    APIResponse<EmergencyContactResponse> get(UUID actorId, UUID recipientId, UUID id);

    APIResponse<EmergencyContactResponse> update(UUID actorId, UUID recipientId, UUID id,
                                                        EmergencyContactRequest request);

    APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id);

    APIResponse<EmergencyContactResponse> assignPrimary(UUID actorId, UUID recipientId, UUID id);

}
