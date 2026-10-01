package mss301.identityservice.mapper;

import mss301.identityservice.dto.request.RoleRequest;
import mss301.identityservice.dto.response.PermissionResponse;
import mss301.identityservice.dto.response.RoleResponse;
import mss301.identityservice.entity.Permission;
import mss301.identityservice.entity.Role;
import mss301.identityservice.entity.RolePermission;
import mss301.identityservice.entity.RolePermissionId;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, imports = RolePermissionId.class)
public interface RoleMapper {
    @Mapping(target = "permissions", expression = "java(permissions(role))")
    RoleResponse toResponse(Role role);

    List<RoleResponse> toResponseList(List<Role> roles);

    PermissionResponse toPermissionResponse(Permission permission);

    List<PermissionResponse> toPermissionResponseList(List<Permission> permissions);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "rolePermissions", ignore = true)
    @BeanMapping(builder = @Builder(disableBuilder = true))
    Role toRole(RoleRequest request);

    @Mapping(target = "id", expression = "java(new RolePermissionId(role.getId(), permission.getId()))")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "permission", source = "permission")
    @Mapping(target = "grantedAt", ignore = true)
    RolePermission toRolePermission(Role role, Permission permission);

    default Set<String> permissions(Role role) {
        return role.getRolePermissions().stream()
                .map(RolePermission::getPermission)
                .filter(permission -> permission.getDeletedAt() == null)
                .map(Permission::getPermissionCode)
                .collect(Collectors.toSet());
    }
}
