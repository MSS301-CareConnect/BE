package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.ManagerCreateRequest;
import mss301.carerecipientservice.dto.request.ManagerUpdateRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.ManagerInvitationResponse;
import mss301.carerecipientservice.dto.response.MyInvitationResponse;
import mss301.carerecipientservice.dto.response.RecipientManagerResponse;

import java.util.List;
import java.util.UUID;

public interface RecipientManagerService {
    // Danh sách người cùng quản lý.
    APIResponse<List<RecipientManagerResponse>> list(UUID actorId, UUID recipientId);

    // Danh sách lời mời của hồ sơ.
    APIResponse<List<ManagerInvitationResponse>> listInvitations(UUID actorId, UUID recipientId);

    // Lời mời đang chờ người dùng hiện tại.
    APIResponse<List<MyInvitationResponse>> listMyInvitations(UUID actorId);

    // C-04 / D19: thay việc thêm thẳng — người được mời phải chấp nhận; không lộ email có tài khoản hay không.
    APIResponse<ManagerInvitationResponse> invite(UUID actorId, UUID recipientId, ManagerCreateRequest request);

    // Chấp nhận lời mời.
    APIResponse<RecipientManagerResponse> acceptInvitation(UUID actorId, UUID invitationId);

    // Từ chối lời mời.
    APIResponse<Void> declineInvitation(UUID actorId, UUID invitationId);

    // Thu hồi lời mời.
    APIResponse<Void> revokeInvitation(UUID actorId, UUID recipientId, UUID invitationId);

    // Đổi quyền / quan hệ người cùng quản lý.
    APIResponse<RecipientManagerResponse> update(UUID actorId, UUID recipientId, UUID id,
                                                        ManagerUpdateRequest request);

    // Chuyển người quản lý chính.
    APIResponse<RecipientManagerResponse> assignPrimary(UUID actorId, UUID recipientId, UUID id);

    // Gỡ người cùng quản lý.
    APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id);

}
