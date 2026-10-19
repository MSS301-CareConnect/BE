package mss301.providerservice.service;

import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;

import java.util.UUID;

public interface ShiftRegistrationService {

    APIResponse<ShiftRegistrationResponse> register(UUID actorId, UUID shiftId, ShiftRegistrationRequest request);

    APIResponse<PageResponse<ShiftRegistrationResponse>> listForShift(UUID actorId, UUID shiftId, int page, int size);

    APIResponse<PageResponse<ShiftRegistrationResponse>> myRegistrations(UUID actorId, int page, int size);

    APIResponse<ShiftRegistrationResponse> review(UUID actorId, UUID registrationId, RegistrationReviewRequest request);

    APIResponse<Void> cancelOwn(UUID actorId, UUID registrationId, Long version);
}
