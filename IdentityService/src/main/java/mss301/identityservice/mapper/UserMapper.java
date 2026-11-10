package mss301.identityservice.mapper;

import mss301.identityservice.dto.request.ProvisionRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.UpdateProfileRequest;
import mss301.identityservice.dto.response.AuthenticatedUser;
import mss301.identityservice.dto.response.InternalUserResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.entity.Role;
import mss301.identityservice.entity.User;
import mss301.identityservice.entity.UserRole;
import mss301.identityservice.entity.UserRoleId;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import mss301.identityservice.dto.response.UserStatisticsResponse;
import java.util.Map;
import mss301.identityservice.dto.response.AffectedUsersResponse;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;
import java.util.UUID;

// Không có password trong bất kỳ response nào; password hash chỉ do service gán (IgnoreServerManagedUserFields).
@Mapper(config = MapStructConfig.class, imports = UserRoleId.class)
public interface UserMapper {
    UserStatisticsResponse toStatisticsResponse(Map<String, Long> usersByRole, Map<String, Long> usersByStatus, long newCustomers);

    AffectedUsersResponse toAffectedUsersResponse(Long affectedUsers);

    @Mapping(target = "roles", expression = "java(user.effectiveRoleCodes())")
    @Mapping(target = "permissions", expression = "java(user.effectivePermissionCodes())")
    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);

    // API nội bộ /internal/users/** (service khác gọi).
    @Mapping(target = "roles", expression = "java(user.effectiveRoleCodes())")
    InternalUserResponse toInternalResponse(User user);

    // API nội bộ /internal/users/batch.
    List<InternalUserResponse> toInternalResponseList(List<User> users);

    // Một tham số nguồn: MapStruct không tự map chính tham số vào thuộc tính "user" → phải khai báo.
    @Mapping(target = "user", source = "user")
    AuthenticatedUser toAuthenticatedUser(User user);

    @IgnoreServerManagedFields
    @IgnoreServerManagedUserFields
    User toUser(RegisterRequest request);

    @IgnoreServerManagedFields
    @IgnoreServerManagedUserFields
    User toUser(ProvisionRequest request);

    // PUT /users/me, PUT /users/{id}: contract đã công bố là cập nhật MỘT PHẦN (null = giữ nguyên, không có nghĩa
    // "xóa"). Whitelist field được sửa — field mới của User không bao giờ tự động sửa được qua API này.
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "fullName")
    @Mapping(target = "phoneNumber")
    void update(UpdateProfileRequest request, @MappingTarget User user);

    @Mapping(target = "id", expression = "java(new UserRoleId(user.getId(), role.getId()))")
    @Mapping(target = "assignedAt", ignore = true)
    UserRole toUserRole(User user, Role role, UUID assignedBy);
}
