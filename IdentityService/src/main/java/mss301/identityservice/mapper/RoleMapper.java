package mss301.identityservice.mapper;

import mss301.identityservice.dto.request.RoleRequest;
import mss301.identityservice.dto.response.PermissionResponse;
import mss301.identityservice.dto.response.RoleResponse;
import mss301.identityservice.entity.Permission;
import mss301.identityservice.entity.Role;
import mss301.identityservice.entity.RolePermission;
import mss301.identityservice.entity.RolePermissionId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class, imports = RolePermissionId.class)
public interface RoleMapper {
    @Mapping(target = "permissions", expression = "java(role.activePermissionCodes())")
    RoleResponse toResponse(Role role);

    List<RoleResponse> toResponseList(List<Role> roles);

    List<PermissionResponse> toPermissionResponseList(List<Permission> permissions);

    // active = true là giá trị mặc định của entity Role (không đặt ở mapper); permission gán qua toRolePermission.
    @IgnoreServerManagedFields
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "rolePermissions", ignore = true)
    Role toRole(RoleRequest request);

    @Mapping(target = "id", expression = "java(new RolePermissionId(role.getId(), permission.getId()))")
    @Mapping(target = "grantedAt", ignore = true)
    RolePermission toRolePermission(Role role, Permission permission);
}
