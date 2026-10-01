package mss301.identityservice.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.entity.Role;
import mss301.identityservice.entity.User;
import mss301.identityservice.enums.AuditAction;
import mss301.identityservice.enums.RoleCode;
import mss301.identityservice.enums.UserStatus;
import mss301.identityservice.mapper.IdentityAuditMapper;
import mss301.identityservice.mapper.UserMapper;
import mss301.identityservice.repository.IdentityAuditRepository;
import mss301.identityservice.repository.RoleRepository;
import mss301.identityservice.repository.UserRepository;
import mss301.identityservice.repository.UserRoleRepository;
import mss301.identityservice.util.EmailNormalizer;
import mss301.identityservice.validation.StrongPasswordValidator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo ADMIN đầu tiên trên DB mới khi bật IDENTITY_BOOTSTRAP_ENABLED (chạy một lần rồi tắt).
 * Không tự nâng quyền email đã có; bỏ qua nếu đã có ADMIN hoạt động.
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "identity.bootstrap.enabled", havingValue = "true")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BootstrapAdmin implements ApplicationRunner {

    static String ADMIN_FULL_NAME = "CareConnect Administrator";

    UserRepository userRepository;
    RoleRepository roleRepository;
    UserRoleRepository userRoleRepository;
    IdentityAuditRepository identityAuditRepository;
    UserMapper userMapper;
    IdentityAuditMapper identityAuditMapper;
    PasswordEncoder passwordEncoder;
    Environment environment;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role adminRole = roleRepository.findForUpdate(RoleCode.ADMIN.name())
                .orElseThrow(() -> new IllegalStateException("Seed ADMIN before bootstrap"));
        if (userRepository.countActiveByRoleCode(RoleCode.ADMIN.name(), UserStatus.ACTIVE) > 0) {
            return;
        }
        String email = EmailNormalizer.normalize(environment.getRequiredProperty("identity.bootstrap.email"));
        String password = environment.getRequiredProperty("identity.bootstrap.password");
        if (!new StrongPasswordValidator().isValid(password, null)) {
            throw new IllegalArgumentException("Bootstrap password does not meet policy");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException("Bootstrap must use a new account");
        }
        User admin = userRepository.saveAndFlush(User.builder()
                .email(email)
                .fullName(ADMIN_FULL_NAME)
                .password(passwordEncoder.encode(password))
                .status(UserStatus.ACTIVE)
                .build());
        userRoleRepository.save(userMapper.toUserRole(admin, adminRole, null));
        identityAuditRepository.save(identityAuditMapper.toAudit(null, admin.getId(), null,
                AuditAction.ADMIN_BOOTSTRAPPED, "Initial administrator"));
        log.info("Bootstrap administrator {} created", admin.getId());
    }
}
