package mss301.identityservice.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.entity.Permission;
import mss301.identityservice.entity.Role;
import mss301.identityservice.enums.RoleCode;
import mss301.identityservice.mapper.RoleMapper;
import mss301.identityservice.repository.PermissionRepository;
import mss301.identityservice.repository.RolePermissionRepository;
import mss301.identityservice.repository.RoleRepository;
import mss301.identityservice.security.Permissions;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seed idempotent permission catalog + 4 role nền tảng với grants cố định. Không seed tài khoản
 * (ADMIN đầu tiên do BootstrapAdmin tạo theo cấu hình). Không chạy DDL.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DataInitializer implements ApplicationRunner {

    RoleRepository roleRepository;
    PermissionRepository permissionRepository;
    RolePermissionRepository rolePermissionRepository;
    RoleMapper roleMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Permission> catalog = seedPermissions();
        for (RoleCode roleCode : RoleCode.values()) {
            // SERVICE chỉ tồn tại trong token client-credentials, không phải role gán được cho user.
            if (roleCode == RoleCode.SERVICE) {
                continue;
            }
            if (roleRepository.findByRoleCodeAndDeletedAtIsNull(roleCode.name()).isPresent()) {
                continue;
            }
            Role role = roleRepository.saveAndFlush(Role.builder()
                    .roleCode(roleCode.name())
                    .roleName(roleCode.name())
                    .build());
            for (String permissionCode : defaultPermissions(roleCode)) {
                rolePermissionRepository.save(roleMapper.toRolePermission(role, catalog.get(permissionCode)));
            }
            log.info("Seeded role {}", roleCode);
        }
    }

    private Map<String, Permission> seedPermissions() {
        Map<String, Permission> catalog = new HashMap<>();
        for (Permission permission : permissionRepository.findAll()) {
            catalog.put(permission.getPermissionCode(), permission);
        }
        for (String permissionCode : Permissions.ALL) {
            if (!catalog.containsKey(permissionCode)) {
                Permission permission = permissionRepository.save(Permission.builder()
                        .permissionCode(permissionCode)
                        .permissionName(permissionCode)
                        .resource(permissionCode.split(":")[0])
                        .build());
                catalog.put(permissionCode, permission);
            }
        }
        return catalog;
    }

    private List<String> defaultPermissions(RoleCode roleCode) {
        return switch (roleCode) {
            case ADMIN -> List.of(Permissions.USER_READ, Permissions.USER_MANAGE, Permissions.MANAGER_CREATE, Permissions.ROLE_MANAGE);
            case CENTER_MANAGER -> List.of(Permissions.USER_READ, Permissions.USER_MANAGE, Permissions.CAREGIVER_CREATE);
            default -> List.of();
        };
    }
}
