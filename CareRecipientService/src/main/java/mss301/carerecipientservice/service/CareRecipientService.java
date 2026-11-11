package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.request.EmergencyContactRequest;
import mss301.carerecipientservice.dto.request.ManagerCreateRequest;
import mss301.carerecipientservice.dto.request.ManagerUpdateRequest;
import mss301.carerecipientservice.dto.request.MedicationRequest;
import mss301.carerecipientservice.dto.request.RecipientAddressRequest;
import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.EmergencyContactResponse;
import mss301.carerecipientservice.dto.response.ManagerInvitationResponse;
import mss301.carerecipientservice.dto.response.MedicationResponse;
import mss301.carerecipientservice.dto.response.MyInvitationResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.dto.response.RecipientAddressResponse;
import mss301.carerecipientservice.dto.response.RecipientManagerResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;
import mss301.carerecipientservice.enums.RoleCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CareRecipientService {

    // Hồ sơ — list/get chọn phạm vi dữ liệu theo role của người gọi (roles lấy từ token)
    APIResponse<CareRecipientDetailResponse> create(UUID actorId, CareRecipientCreateRequest request);

    // Danh sách hồ sơ: admin xem toàn hệ thống, người dùng xem hồ sơ mình quản lý.
    APIResponse<PageResponse<CareRecipientSummaryResponse>> list(UUID actorId, Set<RoleCode> roles, RecipientStatus status,
                                                                 String keyword, UUID createdBy, boolean includeDeleted,
                                                                 int page, int size);

    // Chi tiết hồ sơ người bệnh.
    APIResponse<CareRecipientDetailResponse> get(UUID actorId, Set<RoleCode> roles, UUID id);

    // Cập nhật hồ sơ người bệnh.
    APIResponse<CareRecipientResponse> update(UUID actorId, UUID id, CareRecipientUpdateRequest request);

    // Đổi trạng thái hồ sơ.
    APIResponse<CareRecipientResponse> updateStatus(UUID actorId, UUID id, RecipientStatus status);

    // Lưu trữ (xóa mềm) hồ sơ.
    APIResponse<Void> delete(UUID actorId, UUID id);

    // Inter-Service
    APIResponse<RecipientSnapshotResponse> getSnapshot(UUID id);

    // Kiểm tra một người dùng có quyền tối thiểu trên hồ sơ không (cho service khác).
    APIResponse<AccessCheckResponse> checkAccess(UUID id, UUID userId, RecipientAccessLevel minLevel);

    // Tóm tắt nhiều hồ sơ một lần.
    APIResponse<List<CareRecipientSummaryResponse>> listBatch(RecipientBatchRequest request);

    // Thêm thuốc.
    APIResponse<MedicationResponse> createMedication(UUID actorId, UUID recipientId, MedicationRequest request);

    // Danh sách thuốc.
    APIResponse<List<MedicationResponse>> listMedications(UUID actorId, UUID recipientId, Boolean active);

    // Chi tiết một thuốc.
    APIResponse<MedicationResponse> getMedication(UUID actorId, UUID recipientId, UUID medicationId);

    // Cập nhật thuốc.
    APIResponse<MedicationResponse> updateMedication(UUID actorId, UUID recipientId, UUID medicationId, MedicationRequest request);

    // Bật / tắt thuốc đang dùng.
    APIResponse<MedicationResponse> updateMedicationActive(UUID actorId, UUID recipientId, UUID medicationId, boolean active);

    // Xóa thuốc.
    APIResponse<Void> deleteMedication(UUID actorId, UUID recipientId, UUID medicationId);

    // Thêm liên hệ khẩn cấp.
    APIResponse<EmergencyContactResponse> createEmergencyContact(UUID actorId, UUID recipientId, EmergencyContactRequest request);

    // Danh sách liên hệ khẩn cấp.
    APIResponse<List<EmergencyContactResponse>> listEmergencyContacts(UUID actorId, UUID recipientId);

    // Chi tiết một liên hệ khẩn cấp.
    APIResponse<EmergencyContactResponse> getEmergencyContact(UUID actorId, UUID recipientId, UUID contactId);

    // Cập nhật liên hệ khẩn cấp.
    APIResponse<EmergencyContactResponse> updateEmergencyContact(UUID actorId, UUID recipientId, UUID contactId, EmergencyContactRequest request);

    // Đặt liên hệ khẩn cấp chính.
    APIResponse<EmergencyContactResponse> assignPrimaryEmergencyContact(UUID actorId, UUID recipientId, UUID contactId);

    // Xóa liên hệ khẩn cấp.
    APIResponse<Void> deleteEmergencyContact(UUID actorId, UUID recipientId, UUID contactId);

    // Sổ địa chỉ (2026-11-05)
    APIResponse<List<RecipientAddressResponse>> listAddresses(UUID actorId, UUID recipientId);

    // Thêm địa chỉ.
    APIResponse<RecipientAddressResponse> createAddress(UUID actorId, UUID recipientId, RecipientAddressRequest request);

    // Cập nhật địa chỉ.
    APIResponse<RecipientAddressResponse> updateAddress(UUID actorId, UUID recipientId, UUID addressId,
                                                        RecipientAddressRequest request);

    // Xóa địa chỉ.
    APIResponse<Void> deleteAddress(UUID actorId, UUID recipientId, UUID addressId);

    // Danh sách người cùng quản lý hồ sơ.
    APIResponse<List<RecipientManagerResponse>> listManagers(UUID actorId, UUID recipientId);

    // Danh sách lời mời cùng quản lý của hồ sơ.
    APIResponse<List<ManagerInvitationResponse>> listInvitations(UUID actorId, UUID recipientId);

    // Danh sách lời mời đang chờ người dùng hiện tại.
    APIResponse<List<MyInvitationResponse>> listMyInvitations(UUID actorId);

    // Mời người khác cùng quản lý hồ sơ.
    APIResponse<ManagerInvitationResponse> inviteManager(UUID actorId, UUID recipientId, ManagerCreateRequest request);

    // Chấp nhận lời mời cùng quản lý.
    APIResponse<RecipientManagerResponse> acceptInvitation(UUID actorId, UUID invitationId);

    // Từ chối lời mời cùng quản lý.
    APIResponse<Void> declineInvitation(UUID actorId, UUID invitationId);

    // Thu hồi lời mời cùng quản lý.
    APIResponse<Void> revokeInvitation(UUID actorId, UUID recipientId, UUID invitationId);

    // Đổi quyền / quan hệ của người cùng quản lý.
    APIResponse<RecipientManagerResponse> updateManager(UUID actorId, UUID recipientId, UUID managerId, ManagerUpdateRequest request);

    // Chuyển người quản lý chính.
    APIResponse<RecipientManagerResponse> assignPrimaryManager(UUID actorId, UUID recipientId, UUID managerId);

    // Gỡ người cùng quản lý.
    APIResponse<Void> deleteManager(UUID actorId, UUID recipientId, UUID managerId);
}
