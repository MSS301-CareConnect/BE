package mss301.identityservice.mapper;

import mss301.identityservice.dto.request.ProvisionRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.UpdateProfileRequest;
import mss301.identityservice.dto.response.AuthenticatedUser;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.entity.Permission;
import mss301.identityservice.entity.Role;
import mss301.identityservice.entity.RolePermission;
import mss301.identityservice.entity.User;
import mss301.identityservice.entity.UserRole;
import mss301.identityservice.entity.UserRoleId;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, imports = UserRoleId.class)
public interface UserMapper {
    @Mapping(target = "roles", expression = "java(roles(user))")
    @Mapping(target = "permissions", expression = "java(permissions(user))")
    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);

    @Mapping(target = "user", source = "user")
    AuthenticatedUser toAuthenticatedUser(User user);

    // Builder của User không set được field kế thừa từ BaseEntity → dùng setter.
    @IgnoreServerManagedUserFields
    @BeanMapping(builder = @Builder(disableBuilder = true))
    User toUser(RegisterRequest request);

    @IgnoreServerManagedUserFields
    @BeanMapping(builder = @Builder(disableBuilder = true))
    User toUser(ProvisionRequest request);

    // Chỉ cho sửa field hồ sơ cơ bản; null = giữ nguyên (không có nghĩa "xóa").
    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "phoneNumber", source = "phoneNumber")
    void update(UpdateProfileRequest request, @MappingTarget User user);

    @Mapping(target = "id", expression = "java(new UserRoleId(user.getId(), role.getId()))")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "assignedBy", source = "assignedBy")
    @Mapping(target = "assignedAt", ignore = true)
    UserRole toUserRole(User user, Role role, UUID assignedBy);

    // Quyền hiệu lực chỉ tính role còn active + chưa xóa mềm.
    default Set<String> roles(User user) {
        return user.getUserRoles().stream()
                .map(UserRole::getRole)
                .filter(role -> role.isActive() && role.getDeletedAt() == null)
                .map(Role::getRoleCode)
                .collect(Collectors.toSet());
    }

    default Set<String> permissions(User user) {
        return user.getUserRoles().stream()
                .map(UserRole::getRole)
                .filter(role -> role.isActive() && role.getDeletedAt() == null)
                .flatMap(role -> role.getRolePermissions().stream())
                .map(RolePermission::getPermission)
                .filter(permission -> permission.getDeletedAt() == null)
                .map(Permission::getPermissionCode)
                .collect(Collectors.toSet());
    }
}
