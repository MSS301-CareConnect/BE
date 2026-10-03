package mss301.carerecipientservice.mapper;

import mss301.carerecipientservice.client.dto.IdentityUserResponse;
import mss301.carerecipientservice.dto.request.ManagerUpdateRequest;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.RecipientManagerResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RelationshipType;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.UUID;

@Mapper(config = MapStructConfig.class)
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

    // API nội bộ /internal/care-recipients/{id}/access.
    AccessCheckResponse toAccessCheckResponse(UUID recipientId, UUID userId, boolean allowed,
                                              RecipientAccessLevel accessLevel);
}
