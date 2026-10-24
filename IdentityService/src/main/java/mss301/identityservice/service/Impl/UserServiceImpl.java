package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.dto.request.ChangePasswordRequest;
import mss301.identityservice.dto.request.ProvisionRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.UpdateProfileRequest;
import mss301.identityservice.dto.request.UserBatchRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.UserStatisticsResponse;
import mss301.identityservice.dto.response.AuthenticatedUser;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.dto.response.PageResponse;
import mss301.identityservice.dto.response.AffectedUsersResponse;
import mss301.identityservice.dto.request.UserStatusRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Locale;
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
import mss301.identityservice.security.CurrentRequestIdentity;
import mss301.identityservice.service.UserService;
import mss301.identityservice.service.SessionStore;
import mss301.identityservice.util.EmailNormalizer;
import mss301.identityservice.validation.StrongPasswordValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mss301.identityservice.repository.StatisticsCountView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserServiceImpl implements UserService {

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
    SessionStore sessionStore;
    CurrentRequestIdentity currentRequestIdentity;

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

    // ===== Tra cứu tài khoản (người dùng & service-to-service qua ApiGateway) =====

    @Override
    @Transactional(readOnly = true)
    public APIResponse<UserResponse> get(UUID id) {
        User user = findUser(id);
        checkLookupScope(user);
        UserResponse userResponse = mapLookupResponse(user);
        return APIResponse.success("Lấy thông tin tài khoản thành công", userResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<UserResponse> getByEmail(String email) {
        if (!canReadAllUsers()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        String normalizedEmail = EmailNormalizer.normalize(email);
        if (normalizedEmail == null || normalizedEmail.isEmpty()) {
            throw new AppException(ErrorCode.EMAIL_REQUIRED);
        }
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        UserResponse userResponse = userMapper.toResponse(user);
        return APIResponse.success("Lấy thông tin tài khoản thành công", userResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<UserResponse>> listBatch(UserBatchRequest request) {
        Set<UUID> userIds = new LinkedHashSet<>(request.getUserIds());
        List<User> users = userRepository.findAllByIdInAndDeletedAtIsNull(userIds);
        users.forEach(this::checkLookupScope);
        List<UserResponse> userResponses = users.stream().map(this::mapLookupResponse).toList();
        return APIResponse.success("Lấy danh sách tài khoản thành công", userResponses);
    }

    // ===== Quản trị tài khoản =====

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<UserResponse>> list(UUID actorId, RoleCode role, UserStatus status, UUID centerId,
                                                         String keyword, int page, int size, String sort) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        User actor = findActiveActor(actorId);
        boolean manager = !hasRole(actor, RoleCode.ADMIN);
        if (manager && (!hasRole(actor, RoleCode.CENTER_MANAGER) || actor.getCenterId() == null)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        RoleCode effectiveRole = manager ? RoleCode.CAREGIVER : role;
        UUID effectiveCenter = manager ? actor.getCenterId() : centerId;
        String[] sortParts = sort.split(",");
        if (sortParts.length > 2 || !Set.of("createdAt", "fullName", "email", "status").contains(sortParts[0])) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        Sort.Direction direction;
        try {
            direction = sortParts.length == 2 ? Sort.Direction.fromString(sortParts[1]) : Sort.Direction.DESC;
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        Specification<User> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (effectiveCenter != null) {
                predicates.add(builder.equal(root.get("centerId"), effectiveCenter));
            }
            if (effectiveRole != null) {
                Join<User, UserRole> assignment = root.join("userRoles");
                predicates.add(builder.equal(assignment.get("role").get("roleCode"), effectiveRole.name()));
                predicates.add(builder.isTrue(assignment.get("role").get("active")));
                predicates.add(builder.isNull(assignment.get("role").get("deletedAt")));
                query.distinct(true);
            }
            if (manager) {
                Subquery<UUID> privileged = query.subquery(UUID.class);
                Join<User, UserRole> assignment = privileged.from(User.class).join("userRoles");
                privileged.select(assignment.get("user").get("id"))
                        .where(assignment.get("role").get("roleCode").in(PRIVILEGED_ROLE_CODES),
                                builder.isTrue(assignment.get("role").get("active")),
                                builder.isNull(assignment.get("role").get("deletedAt")));
                predicates.add(builder.not(root.get("id").in(privileged)));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(builder.like(builder.lower(root.get("email")), pattern),
                        builder.like(builder.lower(root.get("fullName")), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<User> users = userRepository.findAll(specification, PageRequest.of(page, size, Sort.by(direction, sortParts[0])));
        List<UserResponse> items = userMapper.toResponseList(users.getContent());
        if (manager) {
            items.forEach(item -> item.setPermissions(null));
        }
        PageResponse<UserResponse> response = PageResponse.of(users, items);
        return APIResponse.success("Lấy danh sách tài khoản thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<AffectedUsersResponse> updateCenterStatus(UUID actorId, UUID centerId, UserStatusRequest request) {
        if (!canReadAllUsers()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        checkLockReason(request.getStatus(), request.getReason());
        long affected = 0;
        for (UUID userId : userRepository.findIdsByCenter(centerId)) {
            User user = lockUser(userId);
            if (hasRole(user, RoleCode.ADMIN)) {
                continue;
            }
            boolean shouldLock = request.getStatus() == UserStatus.LOCKED && user.getStatus() == UserStatus.ACTIVE;
            boolean shouldUnlock = request.getStatus() == UserStatus.ACTIVE && "CENTER".equals(user.getLockSource());
            if (!shouldLock && !shouldUnlock) {
                continue;
            }
            user.setStatus(request.getStatus());
            user.setLockSource(shouldLock ? "CENTER" : null);
            user.setStatusReason(shouldLock ? request.getReason().trim() : null);
            revokeSessions(user);
            writeAudit(actorId, user, AuditAction.STATUS_CHANGED, request.getReason() == null ? request.getStatus().name() : request.getReason());
            affected++;
        }
        AffectedUsersResponse response = userMapper.toAffectedUsersResponse(affected);
        return APIResponse.success("Cập nhật tài khoản theo trung tâm thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<UserResponse> provision(UUID actorId, ProvisionRequest request) {
        User actor = findActiveActor(actorId);
        RoleCode roleCode = request.getRoleCode();
        User user = userMapper.toUser(request);
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
    public APIResponse<UserResponse> updateStatus(UUID actorId, UUID id, UserStatus status, String reason) {
        User target = lockTargetUser(id);
        User actor = findActiveActor(actorId);
        checkManageScope(actor, target, Permissions.USER_MANAGE);
        if (actorId.equals(id) && status == UserStatus.LOCKED) {
            throw new AppException(ErrorCode.DELETE_SELF_INVALID);
        }
        checkLockReason(status, reason);
        if (target.getStatus() != status || (status == UserStatus.LOCKED && !"ADMIN".equals(target.getLockSource()))) {
            if (status == UserStatus.LOCKED) {
                checkNotLastAdmin(target);
            }
            target.setStatus(status);
            target.setLockSource(status == UserStatus.LOCKED ? "ADMIN" : null);
            target.setStatusReason(reason == null ? null : reason.trim());
            revokeSessions(target);
            writeAudit(actorId, target, AuditAction.STATUS_CHANGED, reason == null ? status.name() : reason);
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
            revokeSessions(target);
            writeAudit(actorId, target, AuditAction.ROLE_REVOKED, roleCode);
        }
        UserResponse userResponse = userMapper.toResponse(target);
        return APIResponse.success("Thu hồi role thành công", userResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<UserStatisticsResponse> statistics(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= 366) {
            throw new AppException(ErrorCode.INVALID_DATE_FILTER);
        }
        UserStatisticsResponse response = userMapper.toStatisticsResponse(toStatisticsCounts(userRepository.countUsersByRole()),
                toStatisticsCounts(userRepository.countUsersByStatus()), userRepository.countNewCustomers(
                        start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant(), RoleCode.CUSTOMER.name()));
        return APIResponse.success("Lấy thống kê tài khoản thành công", response);
    }

    private Map<String, Long> toStatisticsCounts(List<StatisticsCountView> rows) {
        Map<String, Long> counts = new LinkedHashMap<>();
        rows.forEach(row -> counts.put(row.getCategory(), row.getCount()));
        return counts;
    }

    private void checkLockReason(UserStatus status, String reason) {
        if (status == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (status == UserStatus.LOCKED && (reason == null || reason.isBlank())) {
            throw new AppException(ErrorCode.LOCK_REASON_REQUIRED);
        }
        if (reason != null && reason.length() > 500) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }

    private boolean canReadAllUsers() {
        return currentRequestIdentity.hasRole(RoleCode.SERVICE.name())
                || currentRequestIdentity.hasRole(RoleCode.ADMIN.name());
    }

    private void checkLookupScope(User target) {
        if (canReadAllUsers()) {
            return;
        }
        UUID actorId = currentRequestIdentity.userId();
        if (actorId.equals(target.getId())) {
            return;
        }
        User actor = findActiveActor(actorId);
        if (hasRole(actor, RoleCode.CENTER_MANAGER) && actor.getCenterId() != null
                && actor.getCenterId().equals(target.getCenterId())) {
            return;
        }
        throw new AppException(ErrorCode.USER_NOT_EXISTED);
    }

    private UserResponse mapLookupResponse(User user) {
        UserResponse userResponse = userMapper.toResponse(user);
        if (!canReadAllUsers()) {
            userResponse.setPermissions(null);
        }
        return userResponse;
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
        // Redis lỗi làm DB rollback; phiên đã xóa không được hồi sinh khi retry.
        sessionStore.removeAllUserSessions(user.getId());
    }

    private void writeAudit(UUID actorId, User target, AuditAction action, String detail) {
        identityAuditRepository.save(
                identityAuditMapper.toAudit(actorId, target.getId(), target.getCenterId(), action, detail));
    }

    private boolean hasRole(User user, RoleCode roleCode) {
        return user.effectiveRoleCodes().contains(roleCode.name());
    }

    private boolean hasPermission(User user, String permission) {
        return user.effectivePermissionCodes().contains(permission);
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
