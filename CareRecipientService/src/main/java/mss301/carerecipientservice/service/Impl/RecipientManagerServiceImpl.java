package mss301.carerecipientservice.service.Impl;

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

/**
 * Người thân quản lý hồ sơ. Quy tắc: chỉ OWNER thêm/sửa/gỡ người khác và chuyển quản lý chính; ai cũng tự rời được;
 * quản lý chính luôn là OWNER và phải chuyển cho người khác trước khi rời/hạ quyền; không gỡ/hạ OWNER cuối cùng.
 * Mọi thao tác ghi khóa dòng hồ sơ (findForUpdate) để các kiểm tra trên không bị request song song vượt qua.
 *
 * Gọi IdentityService (mạng) luôn NGOÀI transaction DB (rule #24): các method cần Identity không có @Transactional
 * mà ghi bằng TransactionTemplate.
 *
 * TODO(Identity events): khi user bị khóa / xóa mềm ở IdentityService, recipient_managers chưa được đồng bộ (hồ sơ có
 *  thể còn OWNER đã bị khóa). Khi có message broker: Identity phát event user.locked / user.deleted (outbox), service
 *  này gỡ/đánh dấu manager và xử lý hồ sơ mất OWNER hoạt động. Xem docs/2026-10-03-PLAN_CARE_RECIPIENT_GATEWAY.md §6.
 */
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

    // Không @Transactional: đọc DB (transaction ngắn của repository) rồi mới gọi Identity.
    @Override
    public APIResponse<List<RecipientManagerResponse>> list(UUID actorId, UUID recipientId) {
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.VIEWER);
        List<RecipientManager> managers = recipientManagerRepository.findAllByRecipient(recipientId);
        // Một lời gọi batch cho cả danh sách; Identity lỗi → vẫn trả danh sách, chỉ thiếu profile.
        Map<UUID, IdentityUserResponse> profiles = identityUserService.getProfiles(
                managers.stream().map(RecipientManager::getUserId).toList());
        List<RecipientManagerResponse> managerResponses = managers.stream()
                .map(manager -> recipientManagerMapper.toResponse(manager, profiles.get(manager.getUserId())))
                .toList();
        return APIResponse.success("Lấy danh sách người quản lý thành công", managerResponses);
    }

    @Override
    public APIResponse<RecipientManagerResponse> create(UUID actorId, UUID recipientId, ManagerCreateRequest request) {
        // Kiểm quyền trước khi gọi Identity: người ngoài không dùng được API này để dò email.
        recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.OWNER);
        IdentityUserResponse user = identityUserService.getActiveCustomer(request.getEmail());
        RecipientManager savedManager = transactionTemplate.execute(status -> {
            CareRecipient recipient = lockRecipient(recipientId);
            // Kiểm lại sau khi khóa: quyền của actor có thể vừa bị đổi trong lúc gọi Identity.
            recipientAccessService.checkAccess(recipientId, actorId, RecipientAccessLevel.OWNER);
            if (recipientManagerRepository.existsByRecipientAndUser(recipientId, user.getId())) {
                throw new AppException(ErrorCode.MANAGER_EXISTED);
            }
            RecipientManager manager = recipientManagerMapper.toRecipientManager(recipient, user.getId(),
                    request.getRelationshipType(), request.getAccessLevel(), false, actorId);
            return recipientManagerRepository.saveAndFlush(manager);
        });
        log.info("User {} added as {} of care recipient {} by user {}", user.getId(), request.getAccessLevel(),
                recipientId, actorId);
        RecipientManagerResponse managerResponse = recipientManagerMapper.toResponse(savedManager, user);
        return APIResponse.success("Thêm người thân quản lý hồ sơ thành công", managerResponse);
    }

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
            recipientManagerRepository.findPrimary(recipientId).ifPresent(currentPrimary -> {
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

    @Transactional
    @Override
    public APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id) {
        RecipientManager actorMembership = recipientAccessService.checkAccess(recipientId, actorId,
                RecipientAccessLevel.VIEWER);
        lockRecipient(recipientId);
        RecipientManager target = findManager(recipientId, id);
        boolean leavingSelf = target.getUserId().equals(actorId);
        // Gỡ người khác cần OWNER; tự rời thì không.
        if (!leavingSelf && actorMembership.getAccessLevel() != RecipientAccessLevel.OWNER) {
            throw new AppException(ErrorCode.RECIPIENT_ACCESS_DENIED);
        }
        checkCanLoseOwnership(recipientId, target);
        recipientManagerRepository.delete(target);
        log.info("Manager {} removed from care recipient {} by user {}", id, recipientId, actorId);
        String message = leavingSelf ? "Bạn đã rời khỏi hồ sơ" : "Đã gỡ người thân khỏi hồ sơ";
        return APIResponse.success(message);
    }

    // Quản lý chính phải chuyển quyền trước; OWNER cuối cùng không được mất quyền.
    private void checkCanLoseOwnership(UUID recipientId, RecipientManager manager) {
        if (manager.isPrimary()) {
            throw new AppException(ErrorCode.PRIMARY_TRANSFER_REQUIRED);
        }
        if (manager.getAccessLevel() == RecipientAccessLevel.OWNER
                && recipientManagerRepository.countByRecipientAndAccessLevel(recipientId, RecipientAccessLevel.OWNER) <= 1) {
            throw new AppException(ErrorCode.LAST_OWNER);
        }
    }

    // Gọi Identity sau khi transaction đã commit.
    private RecipientManagerResponse toResponseWithProfile(RecipientManager manager) {
        Map<UUID, IdentityUserResponse> profiles = identityUserService.getProfiles(List.of(manager.getUserId()));
        return recipientManagerMapper.toResponse(manager, profiles.get(manager.getUserId()));
    }

    private RecipientManager findManager(UUID recipientId, UUID id) {
        return recipientManagerRepository.findByIdAndRecipient(id, recipientId)
                .orElseThrow(() -> new AppException(ErrorCode.MANAGER_NOT_FOUND));
    }

    private CareRecipient lockRecipient(UUID recipientId) {
        return careRecipientRepository.findForUpdate(recipientId)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
    }
}
