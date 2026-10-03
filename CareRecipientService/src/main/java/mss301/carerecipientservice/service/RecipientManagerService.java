package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.ManagerCreateRequest;
import mss301.carerecipientservice.dto.request.ManagerUpdateRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.RecipientManagerResponse;

import java.util.List;
import java.util.UUID;

public interface RecipientManagerService {
    APIResponse<List<RecipientManagerResponse>> list(UUID actorId, UUID recipientId);

    APIResponse<RecipientManagerResponse> create(UUID actorId, UUID recipientId, ManagerCreateRequest request);

    APIResponse<RecipientManagerResponse> update(UUID actorId, UUID recipientId, UUID id, ManagerUpdateRequest request);

    APIResponse<RecipientManagerResponse> assignPrimary(UUID actorId, UUID recipientId, UUID id);

    APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id);
}
