package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.dto.request.ChangePasswordRequest;
import mss301.identityservice.dto.request.ProvisionRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.UpdateProfileRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.AuthenticatedUser;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.entity.Role;
import mss301.identityservice.entity.User;
import mss301.identityservice.entity.UserRole;
import mss301.identityservice.entity.UserRoleId;
import mss301.identityservice.enums.AuditAction;
import mss301.identityservice.enums.RoleCode;
import mss301.identityservice.enums.UserStatus;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.mapper.IdentityAuditMapper;
import mss301.identityservice.mapper.UserMapper;
import mss301.identityservice.repository.IdentityAuditRepository;
import mss301.identityservice.repository.RoleRepository;
import mss301.identityservice.repository.UserRepository;
import mss301.identityservice.repository.UserRoleRepository;
import mss301.identityservice.security.Permissions;
import mss301.identityservice.service.AccountService;
import mss301.identityservice.util.EmailNormalizer;
import mss301.identityservice.validation.StrongPasswordValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AccountServiceImpl implements AccountService {

    static int MAX_PAGE_SIZE = 100;

    // Manager chỉ được thấy caregiver "thuần" — loại tài khoản đồng thời có role quản trị.
    static List<String> PRIVILEGED_ROLE_CODES = List.of(RoleCode.ADMIN.name(), RoleCode.CENTER_MANAGER.name());

    UserRepository userRepository;
    RoleRepository roleRepository;
    UserRoleRepository userRoleRepository;
    IdentityAuditRepository identityAuditRepository;
    UserMapper userMapper;
    IdentityAuditMapper identityAuditMapper;
    PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public AuthenticatedUser verifyCredentials(String email, String password) {
        // Email không tồn tại và sai mật khẩu trả CÙNG lỗi để không lộ tài khoản nào có trong hệ thống.
        if (StrongPasswordValidator.exceedsBcryptLimit(password)) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EmailNormalizer.normalize(email))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));
        // password null = tài khoản provision chưa tự đặt mật khẩu qua OTP.
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
        return userMapper.toAuthenticatedUser(user);
    }

    @Override
    @Transactional
    public UserResponse createVerified(RegisterRequest request) {
        // request.password đã là BCrypt hash (băm trước khi gửi OTP, lưu tạm trên Redis).
        User user = userMapper.toUser(request);
        user.setPassword(request.getPassword());
        User savedUser = createAccount(user, RoleCode.CUSTOMER, null);
        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public void resetPassword(String email, String passwordHash) {
        User found = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_OTP_OR_EMAIL));
        User user = lockUser(found.getId());
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
        user.setPassword(passwordHash);
        revokeSessions(user);
        writeAudit(user.getId(), user, AuditAction.PASSWORD_RESET, "All previous sessions invalidated");
        log.info("Password reset for user {}", user.getId());
    }

    @Override
    @Transactional
    public void invalidateSessions(UUID userId) {
        User user = lockUser(userId);
        revokeSessions(user);
        writeAudit(userId, user, AuditAction.SESSIONS_REVOKED, "Logout all");
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<UserResponse> me(UUID actorId) {
        User user = findActiveActor(actorId);
        UserResponse userResponse = userMapper.toResponse(user);
        return APIResponse.success("Lấy thông tin tài khoản thành công", userResponse);
    }

    @Override
    @Transactional
    public APIResponse<UserResponse> update(UUID actorId, UpdateProfileRequest request) {
        // Khóa dòng để ghi tuần tự với khóa tài khoản / đổi mật khẩu — hồ sơ cũ không được khôi phục trạng thái cũ.
        User user = lockUser(actorId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        userMapper.update(request, user);
        UserResponse userResponse = userMapper.toResponse(user);
        return APIResponse.success("Cập nhật thông tin thành công", userResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> changePassword(UUID actorId, ChangePasswordRequest request) {
        User user = lockUser(actorId);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        String currentPassword = request.getCurrentPassword();
        if (user.getPassword() == null
                || StrongPasswordValidator.exceedsBcryptLimit(currentPassword)
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new AppException(ErrorCode.PASSWORD_INCORRECT);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        revokeSessions(user);
        writeAudit(actorId, user, AuditAction.PASSWORD_CHANGED, "All previous sessions invalidated");
        return APIResponse.success("Đổi mật khẩu thành công, vui lòng đăng nhập lại");
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<UserResponse>> list(UUID actorId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        User actor = findActiveActor(actorId);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<User> users;
        if (hasRole(actor, RoleCode.ADMIN)) {
            checkAdminPermission(actor, Permissions.USER_READ);
            users = userRepository.findAllByDeletedAtIsNull(pageRequest);
        } else if (hasRole(actor, RoleCode.CENTER_MANAGER)
                && actor.getCenterId() != null
                && hasPermission(actor, Permissions.USER_READ)) {
            users = userRepository.findByCenterAndRole(actor.getCenterId(), RoleCode.CAREGIVER.name(),
                    PRIVILEGED_ROLE_CODES, pageRequest);
        } else {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        List<UserResponse> userResponses = userMapper.toResponseList(users.getContent());
        return APIResponse.success("Lấy danh sách tài khoản thành công", userResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<UserResponse> get(UUID actorId, UUID id) {
        User actor = findActiveActor(actorId);
        User target = findUser(id);
        if (!actorId.equals(id)) {
            checkManageScope(actor, target, Permissions.USER_READ);
        }
        UserResponse userResponse = userMapper.toResponse(target);
        return APIResponse.success("Lấy thông tin tài khoản thành công", userResponse);
    }

    @Override
    @Transactional
    public APIResponse<UserResponse> provision(UUID actorId, ProvisionRequest request) {
        User actor = findActiveActor(actorId);
        User user = userMapper.toUser(request);
        RoleCode roleCode = request.getRoleCode();
        switch (roleCode) {
            case CENTER_MANAGER -> {
                checkAdminPermission(actor, Permissions.MANAGER_CREATE);
                if (request.getCenterId() == null) {
                    throw new AppException(ErrorCode.CENTER_REQUIRED);
                }
                user.setCenterId(request.getCenterId());
            }
            case CAREGIVER -> {
                checkCaregiverCreator(actor, request.getCenterId());
                // Trung tâm luôn lấy từ manager, không tin centerId do client gửi.
                user.setCenterId(actor.getCenterId());
            }
            case CUSTOMER -> {
                checkAdminPermission(actor, Permissions.USER_MANAGE);
                if (request.getCenterId() != null) {
                    throw new AppException(ErrorCode.INVALID_REQUEST);
                }
            }
            default -> throw new AppException(ErrorCode.INVALID_ROLE);
        }
        // Không có mật khẩu tạm dùng chung: người được tạo tự đặt mật khẩu qua luồng quên mật khẩu (OTP).
        User savedUser = createAccount(user, roleCode, actorId);
        UserResponse userResponse = userMapper.toResponse(savedUser);
        return APIResponse.success("Tạo tài khoản thành công; người dùng cần đặt mật khẩu bằng OTP", userResponse);
    }

    @Override
    @Transactional
    public APIResponse<UserResponse> update(UUID actorId, UUID id, UpdateProfileRequest request) {
        User target = lockTargetUser(id);
        User actor = findActiveActor(actorId);
        checkManageScope(actor, target, Permissions.USER_MANAGE);
        userMapper.update(request, target);
        writeAudit(actorId, target, AuditAction.PROFILE_UPDATED, "Basic profile fields");
        UserResponse userResponse = userMapper.toResponse(target);
        return APIResponse.success("Cập nhật thông tin thành công", userResponse);
    }

    @Override
    @Transactional
    public APIResponse<UserResponse> updateStatus(UUID actorId, UUID id, UserStatus status) {
        User target = lockTargetUser(id);
        User actor = findActiveActor(actorId);
        checkManageScope(actor, target, Permissions.USER_MANAGE);
        if (actorId.equals(id) && status == UserStatus.LOCKED) {
            throw new AppException(ErrorCode.DELETE_SELF_INVALID);
        }
        if (target.getStatus() != status) {
            if (status == UserStatus.LOCKED) {
                checkNotLastAdmin(target);
            }
            target.setStatus(status);
            revokeSessions(target);
            writeAudit(actorId, target, AuditAction.STATUS_CHANGED, status.name());
            log.info("User {} status changed to {} by {}", id, status, actorId);
        }
        UserResponse userResponse = userMapper.toResponse(target);
        return APIResponse.success("Cập nhật trạng thái thành công", userResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id) {
        User target = lockTargetUser(id);
        User actor = findActiveActor(actorId);
        checkManageScope(actor, target, Permissions.USER_MANAGE);
        if (actorId.equals(id)) {
            throw new AppException(ErrorCode.DELETE_SELF_INVALID);
        }
        checkNotLastAdmin(target);
        // Xóa mềm: giữ tài khoản + lịch sử audit; email không được tái sử dụng.
        target.setDeletedAt(Instant.now());
        revokeSessions(target);
        writeAudit(actorId, target, AuditAction.ACCOUNT_SOFT_DELETED, "Retain account and audit history");
        log.info("User {} soft deleted by {}", id, actorId);
        return APIResponse.success("Vô hiệu hóa tài khoản thành công");
    }

    @Override
    @Transactional
    public APIResponse<UserResponse> assign(UUID actorId, UUID id, UUID roleId) {
        User target = lockTargetUser(id);
        User actor = findActiveActor(actorId);
        checkAdminPermission(actor, Permissions.ROLE_MANAGE);
        Role role = roleRepository.findByIdAndDeletedAtIsNull(roleId)
                .filter(Role::isActive)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        if (isCenterScopedRole(role) && target.getCenterId() == null) {
            throw new AppException(ErrorCode.CENTER_REQUIRED);
        }
        if (!userRoleRepository.existsById(new UserRoleId(id, roleId))) {
            assignRole(target, role, actorId);
            writeAudit(actorId, target, AuditAction.ROLE_ASSIGNED, role.getRoleCode());
        }
        UserResponse userResponse = userMapper.toResponse(target);
        return APIResponse.success("Gán role thành công", userResponse);
    }

    @Override
    @Transactional
    public APIResponse<UserResponse> revoke(UUID actorId, UUID id, UUID roleId) {
        User target = lockTargetUser(id);
        User actor = findActiveActor(actorId);
        checkAdminPermission(actor, Permissions.ROLE_MANAGE);
        Optional<UserRole> assignment = userRoleRepository.findById(new UserRoleId(id, roleId));
        if (assignment.isPresent()) {
            UserRole userRole = assignment.get();
            String roleCode = userRole.getRole().getRoleCode();
            if (RoleCode.ADMIN.name().equals(roleCode)) {
                checkNotLastAdmin(target);
            }
            userRoleRepository.delete(userRole);
            target.getUserRoles().remove(userRole);
            writeAudit(actorId, target, AuditAction.ROLE_REVOKED, roleCode);
        }
        UserResponse userResponse = userMapper.toResponse(target);
        return APIResponse.success("Thu hồi role thành công", userResponse);
    }

    private User createAccount(User user, RoleCode roleCode, UUID actorId) {
        user.setEmail(EmailNormalizer.normalize(user.getEmail()));
        // Kiểm trước để báo lỗi thân thiện; unique constraint uk_users_email vẫn chặn khi hai request chạy song song.
        if (userRepository.existsByEmailIgnoreCase(user.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }
        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.saveAndFlush(user);
        assignRole(savedUser, findAssignableRole(roleCode), actorId);
        writeAudit(actorId, savedUser, AuditAction.ACCOUNT_CREATED, roleCode.name());
        log.info("Account {} created with role {}", savedUser.getId(), roleCode);
        return savedUser;
    }

    private User findUser(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private User findActiveActor(UUID actorId) {
        User actor = findUser(actorId);
        if (actor.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return actor;
    }

    private User lockUser(UUID id) {
        return userRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    // Khóa role ADMIN trước (mutex chung) rồi mới khóa user: các thao tác có thể làm mất ADMIN cuối cùng chạy tuần tự.
    private User lockTargetUser(UUID id) {
        roleRepository.findForUpdate(RoleCode.ADMIN.name())
                .orElseThrow(() -> new AppException(ErrorCode.DEFAULT_ROLE_NOT_FOUND));
        return lockUser(id);
    }

    private Role findAssignableRole(RoleCode roleCode) {
        Role role = roleRepository.findByRoleCodeAndDeletedAtIsNull(roleCode.name())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        if (!role.isActive()) {
            throw new AppException(ErrorCode.INVALID_ROLE);
        }
        return role;
    }

    private void assignRole(User user, Role role, UUID assignedBy) {
        UserRole userRole = userMapper.toUserRole(user, role, assignedBy);
        userRoleRepository.save(userRole);
        user.getUserRoles().add(userRole);
    }

    // Tăng authzVersion → mọi access/refresh token cũ mất hiệu lực tại IdentityService.
    private void revokeSessions(User user) {
        user.setAuthzVersion(user.getAuthzVersion() + 1);
    }

    private void writeAudit(UUID actorId, User target, AuditAction action, String detail) {
        identityAuditRepository.save(
                identityAuditMapper.toAudit(actorId, target.getId(), target.getCenterId(), action, detail));
    }

    private boolean hasRole(User user, RoleCode roleCode) {
        return userMapper.roles(user).contains(roleCode.name());
    }

    private boolean hasPermission(User user, String permission) {
        return userMapper.permissions(user).contains(permission);
    }

    private boolean isCenterScopedRole(Role role) {
        return RoleCode.CENTER_MANAGER.name().equals(role.getRoleCode())
                || RoleCode.CAREGIVER.name().equals(role.getRoleCode());
    }

    private void checkAdminPermission(User actor, String permission) {
        if (!hasRole(actor, RoleCode.ADMIN) || !hasPermission(actor, permission)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void checkCaregiverCreator(User actor, UUID requestedCenterId) {
        if (!hasRole(actor, RoleCode.CENTER_MANAGER)
                || !hasPermission(actor, Permissions.CAREGIVER_CREATE)
                || actor.getCenterId() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (requestedCenterId != null && !requestedCenterId.equals(actor.getCenterId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    // ADMIN: cần permission. CENTER_MANAGER: chỉ caregiver cùng trung tâm, không bao giờ đụng ADMIN/manager khác.
    private void checkManageScope(User actor, User target, String permission) {
        if (hasRole(actor, RoleCode.ADMIN)) {
            checkAdminPermission(actor, permission);
            return;
        }
        boolean managesTarget = hasRole(actor, RoleCode.CENTER_MANAGER)
                && hasPermission(actor, permission)
                && actor.getCenterId() != null
                && actor.getCenterId().equals(target.getCenterId())
                && hasRole(target, RoleCode.CAREGIVER)
                && !hasRole(target, RoleCode.ADMIN)
                && !hasRole(target, RoleCode.CENTER_MANAGER);
        if (!managesTarget) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private void checkNotLastAdmin(User user) {
        boolean isActiveAdmin = hasRole(user, RoleCode.ADMIN) && user.getStatus() == UserStatus.ACTIVE;
        if (isActiveAdmin && userRepository.countActiveByRoleCode(RoleCode.ADMIN.name(), UserStatus.ACTIVE) <= 1) {
            throw new AppException(ErrorCode.LAST_ADMIN);
        }
    }
}
