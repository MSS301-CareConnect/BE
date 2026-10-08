package mss301.providerservice.service;

import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.enums.CenterStatus;

import java.util.UUID;

public interface CareCenterService {

    APIResponse<CenterResponse> create(UUID actorId, CenterCreateRequest request);

    APIResponse<PageResponse<CenterResponse>> list(CenterStatus status, String keyword, boolean includeDeleted,
                                                   int page, int size);

    APIResponse<CenterResponse> get(UUID id);

    APIResponse<CenterResponse> update(UUID actorId, UUID id, CenterUpdateRequest request);

    APIResponse<CenterResponse> updateStatus(UUID actorId, UUID id, CenterStatusUpdateRequest request);

    APIResponse<Void> delete(UUID actorId, UUID id, Long version);
}
