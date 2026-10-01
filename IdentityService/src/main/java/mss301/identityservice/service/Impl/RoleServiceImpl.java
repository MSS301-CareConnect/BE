package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.dto.request.RolePermissionsRequest;
import mss301.identityservice.dto.request.RoleRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.PermissionResponse;
import mss301.identityservice.dto.response.RoleResponse;
import mss301.identityservice.entity.Permission;
import mss301.identityservice.entity.Role;
import mss301.identityservice.entity.RolePermission;
import mss301.identityservice.entity.RolePermissionId;
import mss301.identityservice.entity.User;
import mss301.identityservice.enums.AuditAction;
import mss301.identityservice.enums.RoleCode;
import mss301.identityservice.enums.UserStatus;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.mapper.IdentityAuditMapper;
import mss301.identityservice.mapper.RoleMapper;
import mss301.identityservice.mapper.UserMapper;
import mss301.identityservice.repository.IdentityAuditRepository;
import mss301.identityservice.repository.PermissionRepository;
import mss301.identityservice.repository.RolePermissionRepository;
import mss301.identityservice.repository.RoleRepository;
import mss301.identityservice.repository.UserRepository;
import mss301.identityservice.security.Permissions;
import mss301.identityservice.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RoleServiceImpl implements RoleService {

    RoleRepository roleRepository;
    PermissionRepository permissionRepository;
    RolePermissionRepository rolePermissionRepository;
    UserRepository userRepository;
    IdentityAuditRepository identityAuditRepository;
    RoleMapper roleMapper;
    UserMapper userMapper;
    IdentityAuditMapper identityAuditMapper;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<RoleResponse>> list(UUID actorId) {
        checkRoleManager(actorId);
        List<RoleResponse> roleResponses = roleMapper.toResponseList(roleRepository.findAllByDeletedAtIsNull());
        return APIResponse.success("Lấy danh sách role thành công", roleResponses);
    }

    @Override
    @Transactional
    public APIResponse<RoleResponse> create(UUID actorId, RoleRequest request) {
        checkRoleManager(actorId);
        if (roleRepository.findByRoleCodeAndDeletedAtIsNull(request.getRoleCode()).isPresent()) {
            throw new AppException(ErrorCode.ROLE_EXISTED);
        }
        Role role = roleRepository.saveAndFlush(roleMapper.toRole(request));
        writeAudit(actorId, role.getId(), AuditAction.ROLE_CREATED, role.getRoleCode());
        log.info("Role {} created by {}", role.getRoleCode(), actorId);
        RoleResponse roleResponse = roleMapper.toResponse(role);
        return APIResponse.success("Tạo role thành công", roleResponse);
    }

    @Override
    @Transactional
    public APIResponse<RoleResponse> updatePermissions(UUID actorId, UUID id, RolePermissionsRequest request) {
        // Cùng mutex với các thao tác tài khoản: đổi quyền chạy tuần tự với gán/thu hồi role.
        roleRepository.findForUpdate(RoleCode.ADMIN.name())
                .orElseThrow(() -> new AppException(ErrorCode.DEFAULT_ROLE_NOT_FOUND));
        checkRoleManager(actorId);
        Role role = roleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        // 4 role nền tảng có quyền cố định; chỉ role bổ sung mới được cấu hình permission.
        if (isBuiltInRole(role)) {
            throw new AppException(ErrorCode.INVALID_ROLE);
        }
        Set<Permission> selectedPermissions = findPermissions(request.getPermissionIds());
        removeUnselectedGrants(role, selectedPermissions);
        addMissingGrants(role, selectedPermissions);
        writeAudit(actorId, id, AuditAction.ROLE_PERMISSIONS_CHANGED, request.getPermissionIds().toString());
        RoleResponse roleResponse = roleMapper.toResponse(role);
        return APIResponse.success("Cập nhật permissions thành công", roleResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<PermissionResponse>> listPermissions(UUID actorId) {
        checkRoleManager(actorId);
        List<PermissionResponse> permissionResponses =
                roleMapper.toPermissionResponseList(permissionRepository.findAllByDeletedAtIsNull());
        return APIResponse.success("Lấy danh sách permission thành công", permissionResponses);
    }

    // Kiểm lại quyền hiện hành từ DB (ngoài @PreAuthorize) trước thao tác quản trị toàn cục.
    private void checkRoleManager(UUID actorId) {
        User actor = userRepository.findByIdAndDeletedAtIsNull(actorId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        boolean isRoleManager = actor.getStatus() == UserStatus.ACTIVE
                && userMapper.roles(actor).contains(RoleCode.ADMIN.name())
                && userMapper.permissions(actor).contains(Permissions.ROLE_MANAGE);
        if (!isRoleManager) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private boolean isBuiltInRole(Role role) {
        return Arrays.stream(RoleCode.values())
                .anyMatch(roleCode -> roleCode.name().equals(role.getRoleCode()));
    }

    private Set<Permission> findPermissions(Set<UUID> permissionIds) {
        Set<Permission> permissions = new HashSet<>();
        for (UUID permissionId : permissionIds) {
            Permission permission = permissionRepository.findByIdAndDeletedAtIsNull(permissionId)
                    .orElseThrow(() -> new AppException(ErrorCode.PERMISSION_NOT_FOUND));
            permissions.add(permission);
        }
        return permissions;
    }

    private void removeUnselectedGrants(Role role, Set<Permission> selectedPermissions) {
        for (RolePermission rolePermission : new HashSet<>(role.getRolePermissions())) {
            if (!selectedPermissions.contains(rolePermission.getPermission())) {
                rolePermissionRepository.delete(rolePermission);
                role.getRolePermissions().remove(rolePermission);
            }
        }
    }

    private void addMissingGrants(Role role, Set<Permission> selectedPermissions) {
        for (Permission permission : selectedPermissions) {
            if (!rolePermissionRepository.existsById(new RolePermissionId(role.getId(), permission.getId()))) {
                RolePermission rolePermission = roleMapper.toRolePermission(role, permission);
                rolePermissionRepository.save(rolePermission);
                role.getRolePermissions().add(rolePermission);
            }
        }
    }

    private void writeAudit(UUID actorId, UUID roleId, AuditAction action, String detail) {
        identityAuditRepository.save(identityAuditMapper.toAudit(actorId, roleId, null, action, detail));
    }
}
