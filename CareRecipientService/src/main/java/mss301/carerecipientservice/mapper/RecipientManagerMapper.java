package mss301.carerecipientservice.mapper;

import mss301.carerecipientservice.client.dto.IdentityUserResponse;
import mss301.carerecipientservice.dto.request.ManagerUpdateRequest;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.RecipientManagerResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.client.dto.NotificationCreateRequest;
import mss301.carerecipientservice.dto.response.ManagerInvitationResponse;
import mss301.carerecipientservice.dto.response.MyInvitationResponse;
import mss301.carerecipientservice.entity.ManagerInvitation;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.InvitationStatus;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RelationshipType;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class, imports = List.class)
public interface RecipientManagerMapper {
    // profile null = IdentityService không phản hồi (fallback) hoặc tài khoản không còn → chỉ trả dữ liệu của service này.
    // id có ở cả hai nguồn → lấy của manager; fullName/email/phoneNumber chỉ profile có → tự map.
    @Mapping(target = "id", source = "manager.id")
    @Mapping(target = "userStatus", source = "profile.status")
    @Mapping(target = "profileAvailable", expression = "java(profile != null)")
    RecipientManagerResponse toResponse(RecipientManager manager, IdentityUserResponse profile);

    // Create. Quyền (OWNER/EDITOR/VIEWER) và cờ quản lý chính là quyết định nghiệp vụ của service → truyền vào.
    // RecipientManager không kế thừa BaseEntity (bảng liên kết, không deletedAt).
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RecipientManager toRecipientManager(CareRecipient recipient, UUID userId, RelationshipType relationshipType,
                                        RecipientAccessLevel accessLevel, boolean primary, UUID addedBy);

    // PATCH: chỉ field được phép sửa (whitelist), null = giữ nguyên.
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "accessLevel")
    @Mapping(target = "relationshipType")
    void update(ManagerUpdateRequest request, @MappingTarget RecipientManager manager);

    // Lời mời (C-04): service quyết invitee / trạng thái / hạn; mapper chỉ gán.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "recipient", source = "recipient")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "respondedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ManagerInvitation toInvitation(CareRecipient recipient, String email, UUID inviteeUserId,
                                   RelationshipType relationshipType, RecipientAccessLevel accessLevel,
                                   InvitationStatus status, UUID invitedBy, Instant expiresAt);

    @Mapping(target = "recipientId", source = "recipient.id")
    ManagerInvitationResponse toInvitationResponse(ManagerInvitation invitation);

    List<ManagerInvitationResponse> toInvitationResponseList(List<ManagerInvitation> invitations);

    @Mapping(target = "recipientId", source = "recipient.id")
    @Mapping(target = "recipientFullName", source = "recipient.fullName")
    MyInvitationResponse toMyInvitationResponse(ManagerInvitation invitation);

    List<MyInvitationResponse> toMyInvitationResponseList(List<ManagerInvitation> invitations);

    // Thông báo cho người được mời — không chứa thông tin y tế, chỉ dẫn tới màn lời mời.
    @Mapping(target = "recipientUserIds", expression = "java(List.of(invitation.getInviteeUserId()))")
    @Mapping(target = "type", constant = "RECIPIENT_INVITATION")
    @Mapping(target = "title", constant = "Lời mời cùng quản lý hồ sơ người thân")
    @Mapping(target = "body", constant = "Bạn được mời cùng quản lý một hồ sơ người thân. Mở mục Lời mời để chấp nhận hoặc từ chối.")
    @Mapping(target = "refType", constant = "MANAGER_INVITATION")
    @Mapping(target = "refId", source = "id")
    NotificationCreateRequest toInvitationNotification(ManagerInvitation invitation);

    // GET /care-recipients/{id}/access.
    AccessCheckResponse toAccessCheckResponse(UUID recipientId, UUID userId, boolean allowed,
                                              RecipientAccessLevel accessLevel);
}
