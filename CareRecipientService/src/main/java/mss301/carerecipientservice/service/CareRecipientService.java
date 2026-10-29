package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.*;
import mss301.carerecipientservice.dto.response.*;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;
import mss301.carerecipientservice.enums.RoleCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CareRecipientService {

    // Hồ sơ — list/get chọn phạm vi dữ liệu theo role của người gọi (roles lấy từ token)
    APIResponse<CareRecipientDetailResponse> create(UUID actorId, CareRecipientCreateRequest request);

    APIResponse<PageResponse<CareRecipientSummaryResponse>> list(UUID actorId, Set<RoleCode> roles, RecipientStatus status,
                                                                 String keyword, UUID createdBy, boolean includeDeleted,
                                                                 int page, int size);

    APIResponse<CareRecipientDetailResponse> get(UUID actorId, Set<RoleCode> roles, UUID id);

    APIResponse<CareRecipientResponse> update(UUID actorId, UUID id, CareRecipientUpdateRequest request);

    APIResponse<CareRecipientResponse> updateStatus(UUID actorId, UUID id, RecipientStatus status);

    APIResponse<Void> delete(UUID actorId, UUID id);

    // Inter-Service
    APIResponse<RecipientSnapshotResponse> getSnapshot(UUID id);

    APIResponse<AccessCheckResponse> checkAccess(UUID id, UUID userId, RecipientAccessLevel minLevel);

    APIResponse<List<CareRecipientSummaryResponse>> listBatch(RecipientBatchRequest request);

    // ===== Medication =====
    APIResponse<MedicationResponse> createMedication(UUID actorId, UUID recipientId, MedicationRequest request);

    APIResponse<List<MedicationResponse>> listMedications(UUID actorId, UUID recipientId, Boolean active);

    APIResponse<MedicationResponse> getMedication(UUID actorId, UUID recipientId, UUID medicationId);

    APIResponse<MedicationResponse> updateMedication(UUID actorId, UUID recipientId, UUID medicationId, MedicationRequest request);

    APIResponse<MedicationResponse> updateMedicationActive(UUID actorId, UUID recipientId, UUID medicationId, boolean active);

    APIResponse<Void> deleteMedication(UUID actorId, UUID recipientId, UUID medicationId);

    // ===== Emergency Contact =====
    APIResponse<EmergencyContactResponse> createEmergencyContact(UUID actorId, UUID recipientId, EmergencyContactRequest request);

    APIResponse<List<EmergencyContactResponse>> listEmergencyContacts(UUID actorId, UUID recipientId);

    APIResponse<EmergencyContactResponse> getEmergencyContact(UUID actorId, UUID recipientId, UUID contactId);

    APIResponse<EmergencyContactResponse> updateEmergencyContact(UUID actorId, UUID recipientId, UUID contactId, EmergencyContactRequest request);

    APIResponse<EmergencyContactResponse> assignPrimaryEmergencyContact(UUID actorId, UUID recipientId, UUID contactId);

    APIResponse<Void> deleteEmergencyContact(UUID actorId, UUID recipientId, UUID contactId);

    // ===== Recipient Manager =====
    APIResponse<List<RecipientManagerResponse>> listManagers(UUID actorId, UUID recipientId);

    APIResponse<List<ManagerInvitationResponse>> listInvitations(UUID actorId, UUID recipientId);

    APIResponse<List<MyInvitationResponse>> listMyInvitations(UUID actorId);

    APIResponse<ManagerInvitationResponse> inviteManager(UUID actorId, UUID recipientId, ManagerCreateRequest request);

    APIResponse<RecipientManagerResponse> acceptInvitation(UUID actorId, UUID invitationId);

    APIResponse<Void> declineInvitation(UUID actorId, UUID invitationId);

    APIResponse<Void> revokeInvitation(UUID actorId, UUID recipientId, UUID invitationId);

    APIResponse<RecipientManagerResponse> updateManager(UUID actorId, UUID recipientId, UUID managerId, ManagerUpdateRequest request);

    APIResponse<RecipientManagerResponse> assignPrimaryManager(UUID actorId, UUID recipientId, UUID managerId);

    APIResponse<Void> deleteManager(UUID actorId, UUID recipientId, UUID managerId);
}
