package mss301.providerservice.service;

import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.CaregiverUpdateRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;

import java.util.UUID;

public interface CaregiverService {

    APIResponse<CaregiverResponse> create(UUID actorId, UUID centerId, CaregiverCreateRequest request);

    APIResponse<PageResponse<CaregiverResponse>> listManaged(UUID actorId, UUID centerId,
                                                            VerificationStatus verification,
                                                            CaregiverStatus status, int page, int size);

    APIResponse<CaregiverResponse> get(UUID actorId, UUID caregiverId);

    APIResponse<CaregiverResponse> get(UUID caregiverId);

    APIResponse<CaregiverResponse> getByUser(UUID userId);

    APIResponse<CaregiverResponse> myProfile(UUID actorId);

    APIResponse<PageResponse<CaregiverResponse>> catalog(UUID centerId, String keyword, int page, int size);

    APIResponse<CaregiverResponse> update(UUID actorId, UUID caregiverId, CaregiverUpdateRequest request);

    APIResponse<CaregiverResponse> verify(UUID actorId, UUID caregiverId, VerificationRequest request);

    APIResponse<Void> archive(UUID actorId, UUID caregiverId, Long version);

    CaregiverProfile findByUser(UUID userId);

    CaregiverProfile find(UUID id);
}
