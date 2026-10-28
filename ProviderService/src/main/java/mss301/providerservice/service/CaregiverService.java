package mss301.providerservice.service;

import mss301.providerservice.dto.request.AvailabilityCheckRequest;
import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.CaregiverSearchRequest;
import mss301.providerservice.dto.request.CaregiverSkillRequest;
import mss301.providerservice.dto.request.CaregiverUpdateRequest;
import mss301.providerservice.dto.request.CaregiverStatusRequest;
import mss301.providerservice.dto.request.CaregiverRatingRequest;
import mss301.providerservice.dto.request.TimeOffRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.AvailabilityCheckResponse;
import mss301.providerservice.dto.response.AvailabilityWindowResponse;
import mss301.providerservice.dto.response.CaregiverCardResponse;
import mss301.providerservice.dto.response.CaregiverPublicResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.CaregiverSkillResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.TimeOffResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CaregiverService {

    APIResponse<CaregiverResponse> create(UUID actorId, UUID centerId, CaregiverCreateRequest request);

    APIResponse<PageResponse<CaregiverResponse>> listByCenter(UUID actorId, UUID centerId,
                                                            VerificationStatus verification,
                                                            CaregiverStatus status, int page, int size);

    APIResponse<CaregiverResponse> get(UUID actorId, UUID caregiverId);

    APIResponse<CaregiverResponse> getByUser(UUID userId);

    APIResponse<CaregiverResponse> getMe(UUID actorId);

    APIResponse<PageResponse<CaregiverCardResponse>> list(UUID centerId, String keyword, int page, int size);

    APIResponse<PageResponse<CaregiverCardResponse>> search(CaregiverSearchRequest request);

    APIResponse<CaregiverPublicResponse> getPublic(UUID caregiverId);

    APIResponse<CaregiverResponse> update(UUID actorId, UUID caregiverId, CaregiverUpdateRequest request);

    APIResponse<Void> updateRating(UUID caregiverId, CaregiverRatingRequest request);

    APIResponse<CaregiverResponse> updateStatus(UUID actorId, UUID caregiverId, CaregiverStatusRequest request);

    APIResponse<CaregiverResponse> verify(UUID actorId, UUID caregiverId, VerificationRequest request);

    APIResponse<Void> archive(UUID actorId, UUID caregiverId, Long version);

    APIResponse<List<CaregiverSkillResponse>> listServices(UUID actorId, UUID caregiverId);

    APIResponse<List<CaregiverSkillResponse>> replaceServices(UUID actorId, UUID caregiverId,
                                                               CaregiverSkillRequest request);

    APIResponse<List<TimeOffResponse>> listTimeOffs(UUID actorId, UUID caregiverId, Instant from, Instant to);

    APIResponse<TimeOffResponse> createTimeOff(UUID actorId, UUID caregiverId, TimeOffRequest request);

    APIResponse<Void> deleteTimeOff(UUID actorId, UUID caregiverId, UUID timeOffId);

    APIResponse<List<AvailabilityWindowResponse>> getAvailability(UUID caregiverId, Instant from, Instant to);

    APIResponse<AvailabilityCheckResponse> checkAvailability(AvailabilityCheckRequest request);

    CaregiverProfile findActiveCaregiverByUser(UUID userId);

    CaregiverProfile findActiveCaregiver(UUID id);
    APIResponse<PageResponse<CaregiverResponse>> listPendingVerification(UUID centerId, int page, int size);
}
