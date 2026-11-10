package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.RecipientAddressRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.RecipientAddressResponse;

import java.util.List;
import java.util.UUID;

public interface RecipientAddressService {
    APIResponse<List<RecipientAddressResponse>> list(UUID actorId, UUID recipientId);

    APIResponse<RecipientAddressResponse> create(UUID actorId, UUID recipientId, RecipientAddressRequest request);

    APIResponse<RecipientAddressResponse> update(UUID actorId, UUID recipientId, UUID id, RecipientAddressRequest request);

    APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id);
}
