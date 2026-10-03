package mss301.identityservice.mapper;

import mss301.identityservice.dto.request.ProvisionRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.RoleRequest;
import mss301.identityservice.dto.request.UpdateProfileRequest;
import mss301.identityservice.dto.response.AuthenticatedUser;
import mss301.identityservice.dto.response.AuthenticationResponse;
import mss301.identityservice.dto.response.InternalUserResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.entity.Permission;
import mss301.identityservice.entity.Role;
import mss301.identityservice.entity.RolePermission;
import mss301.identityservice.entity.User;
import mss301.identityservice.entity.UserRole;
import mss301.identityservice.entity.UserRoleId;
import mss301.identityservice.enums.RoleCode;
import mss301.identityservice.enums.UserStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test cho các mapping không hiển nhiên: trường nhạy cảm / server quản lý bị bỏ qua, update một phần, quyền hiệu
 * lực, khóa ghép. Dùng thẳng code MapStruct sinh ra (không cần Spring).
 */
class IdentityMapperTests {

    private final UserMapper userMapper = new UserMapperImpl();
    private final RoleMapper roleMapper = new RoleMapperImpl();
    private final AuthenticationMapper authenticationMapper = new AuthenticationMapperImpl();

    @Test
    void toUserNeverCopiesPasswordStatusOrServerFields() {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email("a@example.test")
                .password("$2a$10$hash-from-redis")
                .fullName("A")
                .phoneNumber("0901234567")
                .build();
        User user = userMapper.toUser(registerRequest);
        assertThat(user.getEmail()).isEqualTo("a@example.test");
        assertThat(user.getFullName()).isEqualTo("A");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getStatus()).isNull();
        assertThat(user.getId()).isNull();
        assertThat(user.getUserRoles()).isEmpty();
    }

    @Test
    void provisionRequestCannotSetCenterOrRole() {
        ProvisionRequest provisionRequest = ProvisionRequest.builder()
                .email("m@example.test")
                .fullName("M")
                .roleCode(RoleCode.CENTER_MANAGER)
                .centerId(UUID.randomUUID())
                .build();
        User user = userMapper.toUser(provisionRequest);
        assertThat(user.getCenterId()).isNull();
        assertThat(user.getUserRoles()).isEmpty();
        assertThat(user.getAuthzVersion()).isZero();
    }

    @Test
    void profileUpdateIgnoresNullFields() {
        User user = new User();
        user.setFullName("Old name");
        user.setPhoneNumber("0900000000");
        user.setEmail("keep@example.test");
        userMapper.update(UpdateProfileRequest.builder().phoneNumber("0911111111").build(), user);
        assertThat(user.getFullName()).isEqualTo("Old name");
        assertThat(user.getPhoneNumber()).isEqualTo("0911111111");
        assertThat(user.getEmail()).isEqualTo("keep@example.test");
    }

    @Test
    void responseUsesEffectiveRolesAndPermissionsOnly() {
        User user = userWithRoles();
        UserResponse userResponse = userMapper.toResponse(user);
        assertThat(userResponse.getRoles()).containsExactly("CUSTOMER");
        assertThat(userResponse.getPermissions()).containsExactly("user:read");
        assertThat(userResponse.getStatus()).isEqualTo(UserStatus.ACTIVE);
        InternalUserResponse internalUserResponse = userMapper.toInternalResponse(user);
        assertThat(internalUserResponse.getRoles()).containsExactly("CUSTOMER");

        AuthenticatedUser authenticatedUser = userMapper.toAuthenticatedUser(user);
        assertThat(authenticatedUser.getUser().getId()).isEqualTo(user.getId());
        assertThat(authenticatedUser.getAuthzVersion()).isEqualTo(7);
    }

    @Test
    void responseDtosHaveNoSensitiveFields() {
        for (Class<?> type : new Class<?>[]{UserResponse.class, InternalUserResponse.class, AuthenticatedUser.class}) {
            boolean hasSensitiveField = Arrays.stream(type.getDeclaredFields())
                    .map(Field::getName)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .anyMatch(name -> name.contains("password") || name.contains("secret") || name.contains("token"));
            assertThat(hasSensitiveField).as(type.getSimpleName()).isFalse();
        }
    }

    @Test
    void webAuthenticationResponseNeverCarriesTokens() {
        AuthenticationResponse webResponse = authenticationMapper.toWebResponse("csrf", 900);
        assertThat(webResponse.getAccessToken()).isNull();
        assertThat(webResponse.getRefreshToken()).isNull();
        assertThat(webResponse.isAuthenticated()).isTrue();
        AuthenticationResponse mobileResponse = authenticationMapper.toMobileResponse("access", "refresh", 900);
        assertThat(mobileResponse.getCsrfToken()).isNull();
    }

    @Test
    void newRoleIsActiveByEntityDefaultAndHasNoPermissions() {
        Role role = roleMapper.toRole(RoleRequest.builder().roleCode("AUDITOR").roleName("Auditor").build());
        assertThat(role.isActive()).isTrue();
        assertThat(role.getRolePermissions()).isEmpty();
        assertThat(role.getId()).isNull();
    }

    @Test
    void linkEntitiesGetCompositeIds() {
        User user = userWithRoles();
        Role role = user.getUserRoles().iterator().next().getRole();
        UUID assignedBy = UUID.randomUUID();
        UserRole userRole = userMapper.toUserRole(user, role, assignedBy);
        assertThat(userRole.getId()).isEqualTo(new UserRoleId(user.getId(), role.getId()));
        assertThat(userRole.getAssignedBy()).isEqualTo(assignedBy);
        Permission permission = role.getRolePermissions().iterator().next().getPermission();
        RolePermission rolePermission = roleMapper.toRolePermission(role, permission);
        assertThat(rolePermission.getId().getRoleId()).isEqualTo(role.getId());
        assertThat(rolePermission.getId().getPermissionId()).isEqualTo(permission.getId());
    }

    // CUSTOMER (active) có user:read + một permission đã xóa mềm; ADMIN bị tắt → không được tính.
    private User userWithRoles() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u@example.test");
        user.setPassword("$2a$10$secret-hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setAuthzVersion(7);
        Role customer = role("CUSTOMER", true);
        customer.getRolePermissions().add(rolePermission(customer, permission("user:read", null)));
        customer.getRolePermissions().add(rolePermission(customer, permission("user:manage", Instant.now())));
        Role admin = role("ADMIN", false);
        admin.getRolePermissions().add(rolePermission(admin, permission("role:manage", null)));
        user.getUserRoles().add(userRole(user, customer));
        user.getUserRoles().add(userRole(user, admin));
        return user;
    }

    private Role role(String code, boolean active) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setRoleCode(code);
        role.setActive(active);
        return role;
    }

    private Permission permission(String code, Instant deletedAt) {
        Permission permission = new Permission();
        permission.setId(UUID.randomUUID());
        permission.setPermissionCode(code);
        permission.setDeletedAt(deletedAt);
        return permission;
    }

    private RolePermission rolePermission(Role role, Permission permission) {
        RolePermission rolePermission = new RolePermission();
        rolePermission.setRole(role);
        rolePermission.setPermission(permission);
        return rolePermission;
    }

    private UserRole userRole(User user, Role role) {
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        return userRole;
    }
}
