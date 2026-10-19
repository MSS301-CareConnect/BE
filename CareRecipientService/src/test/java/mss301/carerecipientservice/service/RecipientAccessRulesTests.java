package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.ManagerUpdateRequest;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.mapper.RecipientManagerMapper;
import mss301.carerecipientservice.repository.CareRecipientRepository;
import mss301.carerecipientservice.repository.RecipientManagerRepository;
import mss301.carerecipientservice.service.Impl.RecipientAccessServiceImpl;
import mss301.carerecipientservice.service.Impl.RecipientManagerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test (Mockito, không Spring) cho luật phân quyền OWNER/EDITOR/VIEWER và bảo vệ quản lý chính / OWNER cuối.
 */
class RecipientAccessRulesTests {

    UUID recipientId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    CareRecipient recipient = new CareRecipient();
    RecipientManagerRepository recipientManagerRepository = mock(RecipientManagerRepository.class);
    CareRecipientRepository careRecipientRepository = mock(CareRecipientRepository.class);
    IdentityUserService identityUserService = mock(IdentityUserService.class);
    RecipientManagerMapper recipientManagerMapper = mock(RecipientManagerMapper.class);
    TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    RecipientAccessService recipientAccessService = new RecipientAccessServiceImpl(recipientManagerRepository);
    RecipientManagerServiceImpl recipientManagerService = new RecipientManagerServiceImpl(recipientManagerRepository,
            careRecipientRepository, recipientAccessService, identityUserService, recipientManagerMapper, transactionTemplate);

    @BeforeEach
    void setUp() {
        recipient.setId(recipientId);
        when(careRecipientRepository.findForUpdate(recipientId)).thenReturn(Optional.of(recipient));
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        when(identityUserService.getProfiles(anyCollection())).thenReturn(Map.of());
        when(recipientManagerRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @CsvSource({
            "VIEWER, VIEWER, true", "VIEWER, EDITOR, false", "VIEWER, OWNER, false",
            "EDITOR, VIEWER, true", "EDITOR, EDITOR, true", "EDITOR, OWNER, false",
            "OWNER, VIEWER, true", "OWNER, EDITOR, true", "OWNER, OWNER, true"
    })
    void accessLevelHierarchy(RecipientAccessLevel actual, RecipientAccessLevel required, boolean allowed) {
        assertThat(actual.covers(required)).isEqualTo(allowed);
    }

    @Test
    void nonMemberGets404AndInsufficientLevelGets403() {
        UUID stranger = UUID.randomUUID();
        when(recipientManagerRepository.findMembership(recipientId, stranger)).thenReturn(Optional.empty());
        assertErrorCode(() -> recipientAccessService.checkAccess(recipientId, stranger, RecipientAccessLevel.VIEWER),
                ErrorCode.RECIPIENT_NOT_FOUND);

        RecipientManager viewer = member(UUID.randomUUID(), RecipientAccessLevel.VIEWER, false);
        assertErrorCode(() -> recipientAccessService.checkAccess(recipientId, viewer.getUserId(), RecipientAccessLevel.EDITOR),
                ErrorCode.RECIPIENT_ACCESS_DENIED);
        assertThat(recipientAccessService.checkAccess(recipientId, viewer.getUserId(), RecipientAccessLevel.VIEWER))
                .isSameAs(viewer);
    }

    @Test
    void primaryCannotLeaveOrBeDowngraded() {
        RecipientManager primaryOwner = member(ownerId, RecipientAccessLevel.OWNER, true);
        assertErrorCode(() -> recipientManagerService.delete(ownerId, recipientId, primaryOwner.getId()),
                ErrorCode.PRIMARY_TRANSFER_REQUIRED);
        ManagerUpdateRequest downgrade = ManagerUpdateRequest.builder().accessLevel(RecipientAccessLevel.EDITOR).build();
        assertErrorCode(() -> recipientManagerService.update(ownerId, recipientId, primaryOwner.getId(), downgrade),
                ErrorCode.PRIMARY_TRANSFER_REQUIRED);
        verify(recipientManagerRepository, never()).delete(any(RecipientManager.class));
    }

    @Test
    void lastOwnerCannotBeDowngradedEvenIfNotPrimary() {
        member(ownerId, RecipientAccessLevel.OWNER, false);
        RecipientManager otherOwner = member(UUID.randomUUID(), RecipientAccessLevel.OWNER, false);
        when(recipientManagerRepository.countByRecipientAndAccessLevel(recipientId, RecipientAccessLevel.OWNER)).thenReturn(1L);
        ManagerUpdateRequest downgrade = ManagerUpdateRequest.builder().accessLevel(RecipientAccessLevel.VIEWER).build();
        assertErrorCode(() -> recipientManagerService.update(ownerId, recipientId, otherOwner.getId(), downgrade),
                ErrorCode.LAST_OWNER);
    }

    @Test
    void ownerCanRemoveEditorButEditorCannotRemoveOthers() {
        member(ownerId, RecipientAccessLevel.OWNER, true);
        RecipientManager editor = member(UUID.randomUUID(), RecipientAccessLevel.EDITOR, false);
        RecipientManager viewer = member(UUID.randomUUID(), RecipientAccessLevel.VIEWER, false);
        assertErrorCode(() -> recipientManagerService.delete(editor.getUserId(), recipientId, viewer.getId()),
                ErrorCode.RECIPIENT_ACCESS_DENIED);
        recipientManagerService.delete(ownerId, recipientId, editor.getId());
        verify(recipientManagerRepository).delete(editor);
        recipientManagerService.delete(viewer.getUserId(), recipientId, viewer.getId());
        verify(recipientManagerRepository).delete(viewer);
    }

    @Test
    void primaryOnlyTransfersToOwnerAndOldPrimaryIsCleared() {
        RecipientManager currentPrimary = member(ownerId, RecipientAccessLevel.OWNER, true);
        RecipientManager editor = member(UUID.randomUUID(), RecipientAccessLevel.EDITOR, false);
        assertErrorCode(() -> recipientManagerService.assignPrimary(ownerId, recipientId, editor.getId()),
                ErrorCode.PRIMARY_MUST_BE_OWNER);

        RecipientManager secondOwner = member(UUID.randomUUID(), RecipientAccessLevel.OWNER, false);
        when(recipientManagerRepository.findPrimary(recipientId)).thenReturn(Optional.of(currentPrimary));
        recipientManagerService.assignPrimary(ownerId, recipientId, secondOwner.getId());
        assertThat(currentPrimary.isPrimary()).isFalse();
        assertThat(secondOwner.isPrimary()).isTrue();
    }

    @Test
    void editorCannotChangeManagers() {
        RecipientManager editor = member(UUID.randomUUID(), RecipientAccessLevel.EDITOR, false);
        RecipientManager viewer = member(UUID.randomUUID(), RecipientAccessLevel.VIEWER, false);
        ManagerUpdateRequest upgrade = ManagerUpdateRequest.builder().accessLevel(RecipientAccessLevel.OWNER).build();
        assertErrorCode(() -> recipientManagerService.update(editor.getUserId(), recipientId, viewer.getId(), upgrade),
                ErrorCode.RECIPIENT_ACCESS_DENIED);
        assertErrorCode(() -> recipientManagerService.update(ownerId, recipientId, viewer.getId(), new ManagerUpdateRequest()),
                ErrorCode.MANAGER_UPDATE_EMPTY);
    }

    private RecipientManager member(UUID userId, RecipientAccessLevel accessLevel, boolean primary) {
        RecipientManager manager = RecipientManager.builder()
                .id(UUID.randomUUID())
                .recipient(recipient)
                .userId(userId)
                .accessLevel(accessLevel)
                .primary(primary)
                .build();
        when(recipientManagerRepository.findMembership(recipientId, userId)).thenReturn(Optional.of(manager));
        when(recipientManagerRepository.findByIdAndRecipient(manager.getId(), recipientId)).thenReturn(Optional.of(manager));
        return manager;
    }

    private void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }
}
