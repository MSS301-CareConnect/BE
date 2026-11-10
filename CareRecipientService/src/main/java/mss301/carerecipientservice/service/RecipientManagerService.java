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
    APIResponse<List<RecipientManagerResponse>> list(UUID actorId, UUID recipientId);

    APIResponse<List<ManagerInvitationResponse>> listInvitations(UUID actorId, UUID recipientId);

    APIResponse<List<MyInvitationResponse>> listMyInvitations(UUID actorId);

    // C-04 / D19: thay việc thêm thẳng — người được mời phải chấp nhận; không lộ email có tài khoản hay không.
    APIResponse<ManagerInvitationResponse> invite(UUID actorId, UUID recipientId, ManagerCreateRequest request);

    APIResponse<RecipientManagerResponse> acceptInvitation(UUID actorId, UUID invitationId);

    APIResponse<Void> declineInvitation(UUID actorId, UUID invitationId);

    APIResponse<Void> revokeInvitation(UUID actorId, UUID recipientId, UUID invitationId);

    APIResponse<RecipientManagerResponse> update(UUID actorId, UUID recipientId, UUID id,
                                                        ManagerUpdateRequest request);

    APIResponse<RecipientManagerResponse> assignPrimary(UUID actorId, UUID recipientId, UUID id);

    APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id);

}
