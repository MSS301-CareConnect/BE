package mss301.carerecipientservice.service.Impl;

import mss301.carerecipientservice.client.BookingClient;
import mss301.carerecipientservice.dto.response.ManagerInvitationResponse;
import mss301.carerecipientservice.dto.response.MyInvitationResponse;
import mss301.carerecipientservice.entity.ManagerInvitation;
import mss301.carerecipientservice.enums.InvitationStatus;
import mss301.carerecipientservice.repository.ManagerInvitationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.client.dto.IdentityUserResponse;
import mss301.carerecipientservice.dto.request.ManagerCreateRequest;
import mss301.carerecipientservice.dto.request.ManagerUpdateRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.RecipientManagerResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.mapper.RecipientManagerMapper;
import mss301.carerecipientservice.repository.CareRecipientRepository;
import mss301.carerecipientservice.repository.RecipientManagerRepository;
import mss301.carerecipientservice.service.IdentityUserService;
import mss301.carerecipientservice.service.RecipientAccessService;
import org.springframework.stereotype.Service;
import mss301.carerecipientservice.service.RecipientManagerService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RecipientManagerServiceImpl implements RecipientManagerService {

    RecipientManagerRepository recipientManagerRepository;
    CareRecipientRepository careRecipientRepository;
    RecipientAccessService recipientAccessService;
    IdentityUserService identityUserService;
    RecipientManagerMapper recipientManagerMapper;
    TransactionTemplate transactionTemplate;
    ManagerInvitationRepository managerInvitationRepository;
    BookingClient bookingClient;
    Clock clock;

    // D19: lời mời hết hạn sau 7 ngày.
    static Duration INVITATION_TTL = Duration.ofDays(7);

    // Không @Transactional: đọc DB (transaction ngắn của repository) rồi mới gọi Identity.
    @Override
    public APIResponse<List<RecipientManagerResponse>> list(UUID actorId, UUID recipientId) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.VIEWER);
        List<RecipientManager> managers = recipientManagerRepository.findByRecipientIdOrderByCreatedAtAsc(recipientId);
        // Một lời gọi batch cho cả danh sách; Identity lỗi → vẫn trả danh sách, chỉ thiếu profile.
        Map<UUID, IdentityUserResponse> profiles = identityUserService.getProfiles(
                managers.stream().map(RecipientManager::getUserId).toList());
        List<RecipientManagerResponse> managerResponses = managers.stream()
                .map(manager -> recipientManagerMapper.toResponse(manager, profiles.get(manager.getUserId())))
                .toList();
        return APIResponse.success("Lấy danh sách người quản lý thành công", managerResponses);
    }

    // Danh sách lời mời của hồ sơ (chỉ quản lý chính), cập nhật lời mời quá hạn.
    @Override
    @Transactional
    public APIResponse<List<ManagerInvitationResponse>> listInvitations(UUID actorId, UUID recipientId) {
        checkPrimaryOwner(recipientId, actorId);
        List<ManagerInvitation> invitations = managerInvitationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        expireOverdue(invitations);
        List<ManagerInvitationResponse> invitationResponses = recipientManagerMapper.toInvitationResponseList(invitations);
        return APIResponse.success("Lấy danh sách lời mời thành công", invitationResponses);
    }

    // Danh sách lời mời còn hiệu lực gửi tới người dùng.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<MyInvitationResponse>> listMyInvitations(UUID actorId) {
        List<ManagerInvitation> invitations = managerInvitationRepository.findOpenForInvitee(actorId,
                InvitationStatus.PENDING, clock.instant());
        List<MyInvitationResponse> invitationResponses = recipientManagerMapper.toMyInvitationResponseList(invitations);
        return APIResponse.success("Lấy lời mời của bạn thành công", invitationResponses);
    }

    // Không @Transactional: tra Identity (mạng) trước, ghi bằng TransactionTemplate, báo Booking sau commit.
    @Override
    public APIResponse<ManagerInvitationResponse> invite(UUID actorId, UUID recipientId, ManagerCreateRequest request) {
        // Kiểm quyền trước khi gọi Identity: người ngoài không dùng được API này để dò email.
        checkPrimaryOwner(recipientId, actorId);
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);
        UUID inviteeUserId = findInviteeUserId(email);
        ManagerInvitation invitation = transactionTemplate.execute(status -> {
            CareRecipient recipient = lockRecipient(recipientId);
            // Kiểm lại sau khi khóa: quyền của actor có thể vừa bị đổi trong lúc gọi Identity.
            checkPrimaryOwner(recipientId, actorId);
            recipientAccessService.checkActiveForWrite(recipientId);
            if (inviteeUserId != null && recipientManagerRepository.existsByRecipientIdAndUserId(recipientId, inviteeUserId)) {
                throw new AppException(ErrorCode.MANAGER_EXISTED);
            }
            Instant now = clock.instant();
            expireOverdue(managerInvitationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId));
            if (managerInvitationRepository.existsByRecipientIdAndEmailAndStatusAndExpiresAtAfter(recipientId, email, InvitationStatus.PENDING, now)) {
                throw new AppException(ErrorCode.INVITATION_EXISTED);
            }
            return managerInvitationRepository.saveAndFlush(recipientManagerMapper.toInvitation(recipient, email,
                    inviteeUserId, request.getRelationshipType(), request.getAccessLevel(), InvitationStatus.PENDING,
                    actorId, now.plus(INVITATION_TTL)));
        });
        log.info("Invitation {} to manage care recipient {} created by user {}", invitation.getId(), recipientId, actorId);
        notifyInvitee(invitation);
        ManagerInvitationResponse invitationResponse = recipientManagerMapper.toInvitationResponse(invitation);
        return APIResponse.success("Đã gửi lời mời", invitationResponse);
    }

    // Chấp nhận lời mời: thêm người dùng làm người cùng quản lý hồ sơ.
    @Override
    public APIResponse<RecipientManagerResponse> acceptInvitation(UUID actorId, UUID invitationId) {
        RecipientManager manager = transactionTemplate.execute(status -> {
            UUID recipientId = managerInvitationRepository.findById(invitationId)
                    .filter(found -> actorId.equals(found.getInviteeUserId()))
                    .map(found -> found.getRecipient().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));
            // Khóa hồ sơ trước rồi lời mời — cùng thứ tự với invite / revoke để không deadlock.
            CareRecipient recipient = lockRecipient(recipientId);
            ManagerInvitation invitation = lockOpenInvitation(invitationId, actorId);
            if (recipientManagerRepository.existsByRecipientIdAndUserId(recipientId, actorId)) {
                throw new AppException(ErrorCode.MANAGER_EXISTED);
            }
            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setRespondedAt(clock.instant());
            return recipientManagerRepository.saveAndFlush(recipientManagerMapper.toRecipientManager(recipient, actorId,
                    invitation.getRelationshipType(), invitation.getAccessLevel(), false, invitation.getInvitedBy()));
        });
        log.info("User {} accepted invitation {} and now manages care recipient {}", actorId, invitationId,
                manager.getRecipient().getId());
        RecipientManagerResponse managerResponse = toResponseWithProfile(manager);
        return APIResponse.success("Đã chấp nhận lời mời", managerResponse);
    }

    // Từ chối lời mời đang chờ.
    @Override
    @Transactional
    public APIResponse<Void> declineInvitation(UUID actorId, UUID invitationId) {
        ManagerInvitation invitation = lockOpenInvitation(invitationId, actorId);
        invitation.setStatus(InvitationStatus.DECLINED);
        invitation.setRespondedAt(clock.instant());
        log.info("User {} declined invitation {}", actorId, invitationId);
        return APIResponse.success("Đã từ chối lời mời");
    }

    // Quản lý chính thu hồi lời mời đang chờ.
    @Override
    @Transactional
    public APIResponse<Void> revokeInvitation(UUID actorId, UUID recipientId, UUID invitationId) {
        checkPrimaryOwner(recipientId, actorId);
        lockRecipient(recipientId);
        ManagerInvitation invitation = managerInvitationRepository.findForUpdate(invitationId)
                .filter(found -> found.getRecipient().getId().equals(recipientId))
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_NOT_PENDING);
        }
        invitation.setStatus(InvitationStatus.REVOKED);
        invitation.setRespondedAt(clock.instant());
        log.info("Invitation {} of care recipient {} revoked by user {}", invitationId, recipientId, actorId);
        return APIResponse.success("Đã thu hồi lời mời");
    }

    // Đổi quyền / quan hệ của người cùng quản lý (chỉ OWNER).
    @Override
    public APIResponse<RecipientManagerResponse> update(UUID actorId, UUID recipientId, UUID id,
                                                        ManagerUpdateRequest request) {
        if (request.getAccessLevel() == null && request.getRelationshipType() == null) {
            throw new AppException(ErrorCode.MANAGER_UPDATE_EMPTY);
        }
        RecipientManager updatedManager = transactionTemplate.execute(status -> {
            recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.OWNER);
            lockRecipient(recipientId);
            RecipientManager manager = findManager(recipientId, id);
            RecipientAccessLevel newLevel = request.getAccessLevel();
            if (newLevel != null && newLevel != RecipientAccessLevel.OWNER) {
                checkCanLoseOwnership(recipientId, manager);
            }
            recipientManagerMapper.update(request, manager);
            return recipientManagerRepository.saveAndFlush(manager);
        });
        log.info("Manager {} of care recipient {} updated by user {}", id, recipientId, actorId);
        RecipientManagerResponse managerResponse = toResponseWithProfile(updatedManager);
        return APIResponse.success("Cập nhật người quản lý thành công", managerResponse);
    }

    // Chuyển vai trò quản lý chính sang người quản lý khác (chỉ OWNER).
    @Override
    public APIResponse<RecipientManagerResponse> assignPrimary(UUID actorId, UUID recipientId, UUID id) {
        RecipientManager newPrimary = transactionTemplate.execute(status -> {
            recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.OWNER);
            lockRecipient(recipientId);
            RecipientManager target = findManager(recipientId, id);
            if (target.getAccessLevel() != RecipientAccessLevel.OWNER) {
                throw new AppException(ErrorCode.PRIMARY_MUST_BE_OWNER);
            }
            if (target.isPrimary()) {
                return target;
            }
            // Bỏ cờ cũ và flush TRƯỚC khi đặt cờ mới (partial unique index uk_recipient_managers_primary);
            // cả hai bước trong cùng transaction nên hồ sơ không bao giờ ở trạng thái "không có quản lý chính".
            recipientManagerRepository.findByRecipientIdAndPrimaryTrue(recipientId).ifPresent(currentPrimary -> {
                currentPrimary.setPrimary(false);
                recipientManagerRepository.flush();
            });
            target.setPrimary(true);
            return recipientManagerRepository.saveAndFlush(target);
        });
        log.info("Primary manager of care recipient {} transferred to manager {} by user {}", recipientId, id, actorId);
        RecipientManagerResponse managerResponse = toResponseWithProfile(newPrimary);
        return APIResponse.success("Đã chuyển người quản lý chính", managerResponse);
    }

    // Gỡ người cùng quản lý: quản lý chính gỡ người khác, ai cũng tự rời hồ sơ được.
    @Transactional
    @Override
    public APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id) {
        RecipientManager actorMembership = recipientAccessService.checkAccess(recipientId, actorId,
                RecipientAccessLevel.VIEWER);
        lockRecipient(recipientId);
        RecipientManager target = findManager(recipientId, id);
        boolean leavingSelf = target.getUserId().equals(actorId);
        // D19: chỉ người quản lý chính gỡ người khác; ai cũng tự rời được.
        if (!leavingSelf && !actorMembership.isPrimary()) {
            throw new AppException(ErrorCode.RECIPIENT_ACCESS_DENIED);
        }
        checkCanLoseOwnership(recipientId, target);
        recipientManagerRepository.delete(target);
        log.info("Manager {} removed from care recipient {} by user {}", id, recipientId, actorId);
        String message = leavingSelf ? "Bạn đã rời khỏi hồ sơ" : "Đã gỡ người thân khỏi hồ sơ";
        return APIResponse.success(message);
    }

    // D19: chỉ người quản lý chính (luôn là OWNER) được mời / gỡ người thân.
    private void checkPrimaryOwner(UUID recipientId, UUID actorId) {
        RecipientManager membership = recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.OWNER);
        if (!membership.isPrimary()) {
            throw new AppException(ErrorCode.PRIMARY_OWNER_REQUIRED);
        }
    }

    // Email không thuộc CUSTOMER đang hoạt động → lời mời vẫn tạo bình thường nhưng không ai nhận được (không dò email).
    private UUID findInviteeUserId(String email) {
        try {
            return identityUserService.getActiveCustomer(email).getId();
        } catch (AppException e) {
            if (e.getErrorCode() == ErrorCode.MANAGER_USER_INVALID) {
                return null;
            }
            throw e;
        }
    }

    // Khóa lời mời đang chờ của người được mời, không hợp lệ thì báo lỗi.
    private ManagerInvitation lockOpenInvitation(UUID invitationId, UUID actorId) {
        ManagerInvitation invitation = managerInvitationRepository.findForUpdate(invitationId)
                .filter(found -> actorId.equals(found.getInviteeUserId()))
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_NOT_PENDING);
        }
        if (!invitation.getExpiresAt().isAfter(clock.instant())) {
            throw new AppException(ErrorCode.INVITATION_EXPIRED);
        }
        return invitation;
    }

    // Hết hạn tính lười khi đọc / mời lại (không cần job) — partial unique index chỉ chặn lời mời PENDING.
    private void expireOverdue(List<ManagerInvitation> invitations) {
        Instant now = clock.instant();
        for (ManagerInvitation invitation : invitations) {
            if (invitation.getStatus() == InvitationStatus.PENDING && !invitation.getExpiresAt().isAfter(now)) {
                invitation.setStatus(InvitationStatus.EXPIRED);
            }
        }
        managerInvitationRepository.flush();
    }

    // Best-effort sau commit: Booking lỗi chỉ mất thông báo, lời mời vẫn hiện trong "Lời mời của tôi".
    private void notifyInvitee(ManagerInvitation invitation) {
        if (invitation.getInviteeUserId() == null) {
            return;
        }
        try {
            bookingClient.createNotification(recipientManagerMapper.toInvitationNotification(invitation));
        } catch (RuntimeException e) {
            log.warn("Invitation {} notification failed ({})", invitation.getId(), e.getClass().getSimpleName());
        }
    }

    // Quản lý chính phải chuyển quyền trước; OWNER cuối cùng không được mất quyền.
    private void checkCanLoseOwnership(UUID recipientId, RecipientManager manager) {
        if (manager.isPrimary()) {
            throw new AppException(ErrorCode.PRIMARY_TRANSFER_REQUIRED);
        }
        if (manager.getAccessLevel() == RecipientAccessLevel.OWNER
                && recipientManagerRepository.countByRecipientIdAndAccessLevel(recipientId, RecipientAccessLevel.OWNER) <= 1) {
            throw new AppException(ErrorCode.LAST_OWNER);
        }
    }

    // Gọi Identity sau khi transaction đã commit.
    private RecipientManagerResponse toResponseWithProfile(RecipientManager manager) {
        Map<UUID, IdentityUserResponse> profiles = identityUserService.getProfiles(List.of(manager.getUserId()));
        return recipientManagerMapper.toResponse(manager, profiles.get(manager.getUserId()));
    }

    // Tìm người quản lý thuộc hồ sơ, không có thì báo lỗi.
    private RecipientManager findManager(UUID recipientId, UUID id) {
        return recipientManagerRepository.findByIdAndRecipientId(id, recipientId)
                .orElseThrow(() -> new AppException(ErrorCode.MANAGER_NOT_FOUND));
    }

    // Khóa dòng hồ sơ để cập nhật, không có thì báo lỗi.
    private CareRecipient lockRecipient(UUID recipientId) {
        return careRecipientRepository.findForUpdate(recipientId)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
    }
}
