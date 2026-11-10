# Handoff Report — Explorer M1.1 (IdentityService Refactoring Strategy)

## 1. Observation

Direct examination of the `IdentityService` codebase at `d:\FA26\MSS\CareConnect\BE\IdentityService` revealed the following exact current structures, line numbers, and dependencies:

### 1.1 Controllers
1. **`AccountController.java`**
   - **Path**: `IdentityService/src/main/java/mss301/identityservice/controller/AccountController.java`
   - **Line 24**: `@RequestMapping("/users")` (missing `/api/v1` prefix).
   - **Lines 29–30**: Injects `AccountService accountService` and `RegistrationService registrationService`.
   - **Lines 34–62**: Contains 5 registration/password endpoints that belong to Authentication domain:
     - `POST /register` (`RegisterRequest` → `APIResponse<OtpResponse>`)
     - `POST /register/resend-otp` (`EmailRequest` → `APIResponse<OtpResponse>`)
     - `POST /register/verify` (`VerifyOtpRequest` → `APIResponse<UserResponse>`)
     - `POST /forgot-password` (`EmailRequest` → `APIResponse<Void>`)
     - `POST /reset-password` (`ResetPasswordRequest` → `APIResponse<Void>`)
   - **Lines 66–81**: Contains self account endpoints:
     - `GET /me` (`@CurrentUserId UUID actorId` → `accountService.me(actorId)`)
     - `PUT /me` (`@CurrentUserId UUID actorId`, `@Valid @RequestBody UpdateProfileRequest request` → `accountService.update(actorId, request)`)
     - `POST /me/change-password` (`@CurrentUserId UUID actorId`, `@Valid @RequestBody ChangePasswordRequest request` → `accountService.changePassword(actorId, request)`)
   - **Lines 85–141**: Contains administration endpoints:
     - `GET` with `@PreAuthorize("hasAuthority('user:read') and (hasRole('ADMIN') or hasRole('CENTER_MANAGER'))")` (`accountService.list(actorId, page, size)`)
     - `GET /{id}` (`accountService.get(actorId, id)`)
     - `POST` with `@PreAuthorize("hasRole('ADMIN') or hasRole('CENTER_MANAGER')")` (`accountService.provision(actorId, request)`)
     - `PUT /{id}` with `@PreAuthorize("hasAuthority('user:manage')")` (`accountService.update(actorId, id, request)`)
     - `PATCH /{id}/status` with `@PreAuthorize("hasAuthority('user:manage')")` (`accountService.updateStatus(actorId, id, status)`)
     - `DELETE /{id}` with `@PreAuthorize("hasAuthority('user:manage')")` (`accountService.delete(actorId, id)`)
     - `PUT /{id}/roles/{roleId}` with `@PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")` (`accountService.assign(actorId, id, roleId)`)
     - `DELETE /{id}/roles/{roleId}` with `@PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")` (`accountService.revoke(actorId, id, roleId)`)

2. **`AuthenticationController.java`**
   - **Path**: `IdentityService/src/main/java/mss301/identityservice/controller/AuthenticationController.java`
   - **Line 24**: `@RequestMapping("/auth")` (missing `/api/v1` prefix).
   - **Line 29**: Injects `AuthenticationService authenticationService`. Does not yet inject `RegistrationService`.
   - **Lines 33–84**: Contains web, mobile, and common auth endpoints:
     - `GET /csrf`, `POST /login`, `POST /refresh`, `POST /logout`
     - `POST /mobile/login`, `POST /mobile/refresh`, `POST /mobile/logout`
     - `POST /logout-all`

3. **`InternalUserController.java`**
   - **Path**: `IdentityService/src/main/java/mss301/identityservice/controller/InternalUserController.java`
   - **Lines 18–41**: Annotated with `@RequestMapping("/internal/users")`, injects `InternalUserService internalUserService`, exposes:
     - `GET /{userId}` (`internalUserService.get(userId)`)
     - `GET /by-email` (`internalUserService.getByEmail(email)`)
     - `POST /batch` (`internalUserService.list(request)`)
   - All protected by `InternalSecurityConfig` requiring `X-Internal-Secret`.

4. **`RoleController.java` & `PermissionController.java`**
   - **Paths**:
     - `IdentityService/src/main/java/mss301/identityservice/controller/RoleController.java` (Line 21: `@RequestMapping("/roles")`)
     - `IdentityService/src/main/java/mss301/identityservice/controller/PermissionController.java` (Line 20: `@RequestMapping("/permissions")`)

5. **`JwksController.java`**
   - **Path**: `IdentityService/src/main/java/mss301/identityservice/controller/JwksController.java`
   - **Line 22**: `@GetMapping("/.well-known/jwks.json")` — standard RFC 7517 endpoint consumed directly by Resource Server JWT decoders.

### 1.2 Services & Implementations
1. **`AccountService.java` & `AccountServiceImpl.java`**
   - **Paths**:
     - `IdentityService/src/main/java/mss301/identityservice/service/AccountService.java`
     - `IdentityService/src/main/java/mss301/identityservice/service/Impl/AccountServiceImpl.java`
   - Interface `AccountService` and class `AccountServiceImpl` implement credentials verification, self-account CRUD, and administrative account management.
   - Injected in:
     - `AuthenticationServiceImpl.java` (Line 48: `AccountService accountService;`)
     - `RegistrationServiceImpl.java` (Line 61: `AccountService accountService;`)
     - `AccountController.java` (Line 29: `AccountService accountService;`)
     - `IdentityServiceApplicationTests.java` (Line 78: `@Autowired AccountService accounts;`)

2. **`InternalUserService.java` & `InternalUserServiceImpl.java`**
   - **Paths**:
     - `IdentityService/src/main/java/mss301/identityservice/service/InternalUserService.java`
     - `IdentityService/src/main/java/mss301/identityservice/service/Impl/InternalUserServiceImpl.java`
   - Implements `get(UUID id)`, `getByEmail(String email)`, and `list(UserBatchRequest request)` querying `UserRepository` and mapping to `InternalUserResponse`.

3. **`RegistrationService.java` & `RegistrationServiceImpl.java`**
   - **Paths**:
     - `IdentityService/src/main/java/mss301/identityservice/service/RegistrationService.java`
     - `IdentityService/src/main/java/mss301/identityservice/service/Impl/RegistrationServiceImpl.java`
   - Handles Redis OTP flows for registration and password reset, calling `accountService.createVerified(...)` and `accountService.resetPassword(...)`.

### 1.3 Security & Configuration
1. **`InternalSecurityConfig.java` & `InternalSecretFilter.java`**
   - **Paths**:
     - `IdentityService/src/main/java/mss301/identityservice/security/InternalSecurityConfig.java`
     - `IdentityService/src/main/java/mss301/identityservice/security/InternalSecretFilter.java`
   - Dedicated filter chain matching `/internal/**` (order 1) checking `X-Internal-Secret`.

2. **`IdentityProperties.java` & `application.yaml`**
   - `IdentityProperties.java` (Line 65): `String internalSecret = "";`
   - `application.yaml` (Line 68): `internal-secret: ${IDENTITY_INTERNAL_SECRET:}`

3. **`OpenApiConfig.java`**
   - **Path**: `IdentityService/src/main/java/mss301/identityservice/config/swagger/OpenApiConfig.java`
   - Lines 14, 29, 30, 57–61, 97–101 reference `InternalSecretFilter`, `INTERNAL_SCHEME = "internalSecret"`, and `INTERNAL_PREFIX = "/internal/"`.

4. **`SecurityConfig.java`**
   - **Path**: `IdentityService/src/main/java/mss301/identityservice/security/SecurityConfig.java`
   - Lines 39–44: `TOKEN_FLOW_ENDPOINTS` contains unversioned `/auth/**` and `/users/register...` paths.
   - Lines 56–58: `COOKIELESS_CSRF_EXEMPT_PATHS` contains unversioned `/users/register...` and `/users/forgot-password` paths.
   - Line 60: `public static final String MOBILE_AUTH_PREFIX = "/auth/mobile/";`

### 1.4 Repositories & MapStruct Mappers
1. **`UserRepository.java`**
   - Line 22: `findByEmailIgnoreCaseAndDeletedAtIsNull(String email)`
   - Line 24: `findByIdAndDeletedAtIsNull(UUID id)`
   - Line 33: `@EntityGraph(attributePaths = {"userRoles", "userRoles.role"}) List<User> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);`
   - Fully ready to support single lookup, email lookup, and batch lookup.

2. **`UserMapper.java` & `MapStructConfig.java`**
   - `MapStructConfig.java` defines `unmappedTargetPolicy = ReportingPolicy.ERROR`.
   - `UserMapper.java`:
     - Line 27: `UserResponse toResponse(User user);`
     - Line 29: `List<UserResponse> toResponseList(List<User> users);`
     - Line 33: `InternalUserResponse toInternalResponse(User user);`
     - Line 36: `List<InternalUserResponse> toInternalResponseList(List<User> users);`
   - All mapping targets (`roles`, `permissions`, server fields) are fully covered.

3. **Current Build Verification**:
   - Running `.\mvnw.cmd compile` and `.\mvnw.cmd test-compile` in `d:\FA26\MSS\CareConnect\BE\IdentityService` exited with code 0 (`BUILD SUCCESS`).

---

## 2. Logic Chain

1. **Endpoint Relocation (R3)**:
   - *Observation*: Registration, OTP verification, and password reset endpoints (`/register`, `/register/resend-otp`, `/register/verify`, `/forgot-password`, `/reset-password`) are currently placed in `AccountController` calling `RegistrationService`.
   - *Reasoning*: Registration and password reset are authentication concerns, not account management concerns. Placing them in `AuthenticationController` unifies all anonymous credential issuance and verification under `/api/v1/auth/**`.
   - *Action*: Move the 5 methods to `AuthenticationController`, inject `RegistrationService` in `AuthenticationController`, and remove `RegistrationService` from `UserController`.

2. **Domain Aggregate Alignment (R1, R2)**:
   - *Observation*: The domain entity is `User` and the repository is `UserRepository`, but the controller is named `AccountController` and service is `AccountService`.
   - *Reasoning*: R1 specifies that every domain aggregate follows: Entity `[Domain]`, Repository `[Domain]Repository`, Service `[Domain]Service`, ServiceImpl `[Domain]ServiceImpl`, Controller `[Domain]Controller`.
   - *Action*: Rename `AccountController` → `UserController` (`/api/v1/users/**`), `AccountService` → `UserService`, `AccountServiceImpl` → `UserServiceImpl`.

3. **Elimination of `/internal/**` Artifacts & Public Lookups (R4)**:
   - *Observation*: `InternalUserController`, `InternalUserService`, `InternalUserServiceImpl`, `InternalSecurityConfig`, `InternalSecretFilter` provide user lookups for external microservices using `X-Internal-Secret`.
   - *Reasoning*: R4 abolishes `/internal/**` in favor of ApiGateway reverse-proxying with OpenFeign JWT token forwarding (`Authorization: Bearer <token>`). Therefore, inter-service lookup endpoints must be exposed on `UserController` at `/api/v1/users/{id}`, `/api/v1/users/by-email`, and `/api/v1/users/batch`.
   - *Action*:
     - Delete all 5 internal files.
     - Remove `internalSecret` from `IdentityProperties.java` and `application.yaml`.
     - Remove internal scheme and customizer from `OpenApiConfig.java`.
     - Implement `get(UUID id)`, `getByEmail(String email)`, and `listBatch(UserBatchRequest request)` in `UserService` / `UserServiceImpl` and expose them in `UserController`.

4. **Security Filter & CSRF Synchronization (R3, R4)**:
   - *Observation*: In `SecurityConfig.java`, `TOKEN_FLOW_ENDPOINTS`, `COOKIELESS_CSRF_EXEMPT_PATHS`, and `MOBILE_AUTH_PREFIX` use hardcoded paths without `/api/v1`.
   - *Reasoning*: If paths are changed in controllers without updating `SecurityConfig.java`, requests to `/api/v1/auth/register`, `/api/v1/auth/login`, etc. will either fail with 401 Unauthorized (because `requestMatchers` does not match them) or 403 Forbidden (CSRF validation failure).
   - *Action*: Update all string literals in `SecurityConfig.java` to start with `/api/v1/auth/`.

5. **Inter-Service Authorization Scope in `UserController`**:
   - *Observation*: `AccountServiceImpl.get(actorId, id)` currently calls `checkManageScope(actor, target, Permissions.USER_READ)` if `!actorId.equals(id)`, restricting reads exclusively to Admin or Center Manager.
   - *Reasoning*: In inter-service calls forwarded via ApiGateway (e.g. CareRecipientService or ProviderService fetching caregiver or customer info on behalf of an active Customer session), a Customer caller does not possess `ADMIN` or `CENTER_MANAGER` roles. Enforcing `checkManageScope` on `GET /api/v1/users/{id}` would cause inter-service calls to fail with 403 Forbidden.
   - *Action*: `GET /api/v1/users/{id}` should allow any authenticated user to view the target user's non-sensitive profile (name, email, phone, status, roles, centerId) via `userMapper.toResponse(target)`. Administrative filtering is already enforced on `GET /api/v1/users` (the user collection list endpoint).

---

## 3. Caveats

1. **JWKS Endpoint RFC Standard**:
   `GET /.well-known/jwks.json` is a standard JOSE/OAuth2 discovery endpoint defined by RFC 7517. It must NOT be prefixed with `/api/v1/`. Resource servers configure `jwt.jwk-set-uri` to `http://localhost:8081/.well-known/jwks.json` directly.
2. **`InternalUserResponse` Retention vs Deletion**:
   `InternalUserResponse.java` and its mapping in `UserMapper.java` (`toInternalResponse`) are tested in `IdentityMapperTests.java`. If `InternalUserResponse.java` is deleted, `IdentityMapperTests.java` must also be modified. If retained as a harmless response DTO, it guarantees zero side effects while keeping MapStruct compilation clean. The recommended strategy retains the DTO and mapper method, or updates `IdentityMapperTests.java` if strict file deletion is desired. Both options are fully documented.
3. **ApiGateway StripPrefix Coordination**:
   `ApiGateway`'s `RouteConfig.java` currently uses `.before(stripPrefix(GatewayPaths.API_PREFIX_SEGMENTS))`. When services introduce `/api/v1` in `@RequestMapping`, ApiGateway must stop stripping `/api/v1` (handled in Milestone 4). When testing `IdentityService` in isolation (Milestone 1), requests call `http://localhost:8081/api/v1/...` directly.
4. **No Caveats Beyond Microservice Boundary**:
   No external services import classes from `IdentityService`. Downstream services (`CareRecipientService`, `ProviderService`, `BookingService`) only call HTTP endpoints via Feign client DTO copies.

---

## 4. Conclusion & Line-by-Line Implementation Strategy

### 4.1 Step 1: Migrate 5 Endpoints to `AuthenticationController.java`
**Target File**: `IdentityService/src/main/java/mss301/identityservice/controller/AuthenticationController.java`

1. Update class annotation:
   ```java
   @RequestMapping("/api/v1/auth")
   ```
2. Add dependency injection for `RegistrationService`:
   ```java
   AuthenticationService authenticationService;
   RegistrationService registrationService;
   ```
3. Add imports:
   ```java
   import mss301.identityservice.dto.request.EmailRequest;
   import mss301.identityservice.dto.request.RegisterRequest;
   import mss301.identityservice.dto.request.ResetPasswordRequest;
   import mss301.identityservice.dto.request.VerifyOtpRequest;
   import mss301.identityservice.dto.response.OtpResponse;
   import mss301.identityservice.dto.response.UserResponse;
   import mss301.identityservice.service.RegistrationService;
   ```
4. Insert 5 migrated endpoints:
   ```java
   // ===== Đăng ký & quên mật khẩu (public) =====

   @PostMapping("/register")
   @SecurityRequirements({})
   public APIResponse<OtpResponse> register(@Valid @RequestBody RegisterRequest request) {
       return registrationService.register(request);
   }

   @PostMapping("/register/resend-otp")
   @SecurityRequirements({})
   public APIResponse<OtpResponse> resend(@Valid @RequestBody EmailRequest request) {
       return registrationService.resend(request);
   }

   @PostMapping("/register/verify")
   @SecurityRequirements({})
   public APIResponse<UserResponse> verify(@Valid @RequestBody VerifyOtpRequest request) {
       return registrationService.verify(request);
   }

   @PostMapping("/forgot-password")
   @SecurityRequirements({})
   public APIResponse<Void> forgotPassword(@Valid @RequestBody EmailRequest request) {
       return registrationService.forgotPassword(request);
   }

   @PostMapping("/reset-password")
   @SecurityRequirements({})
   public APIResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
       return registrationService.resetPassword(request);
   }
   ```

---

### 4.2 Step 2: Refactor `AccountController` → `UserController` (`/api/v1/users/**`)
1. Create new file: `IdentityService/src/main/java/mss301/identityservice/controller/UserController.java`
2. Delete old file: `IdentityService/src/main/java/mss301/identityservice/controller/AccountController.java`
3. Structure of `UserController.java`:
   ```java
   package mss301.identityservice.controller;

   import io.swagger.v3.oas.annotations.tags.Tag;
   import jakarta.validation.Valid;
   import lombok.AccessLevel;
   import lombok.RequiredArgsConstructor;
   import lombok.experimental.FieldDefaults;
   import mss301.identityservice.dto.request.*;
   import mss301.identityservice.dto.response.APIResponse;
   import mss301.identityservice.dto.response.UserResponse;
   import mss301.identityservice.enums.UserStatus;
   import mss301.identityservice.security.CurrentUserId;
   import mss301.identityservice.service.UserService;
   import org.springframework.security.access.prepost.PreAuthorize;
   import org.springframework.web.bind.annotation.*;

   import java.util.List;
   import java.util.UUID;

   @RestController
   @RequestMapping("/api/v1/users")
   @RequiredArgsConstructor
   @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
   @Tag(name = "User", description = "Quản lý tài khoản cho mọi role (hồ sơ, mật khẩu), thao tác quản trị theo quyền, và tra cứu tài khoản cho service khác.")
   public class UserController {
       UserService userService;

       // ===== Tài khoản của chính mình =====

       @GetMapping("/me")
       public APIResponse<UserResponse> me(@CurrentUserId UUID actorId) {
           return userService.me(actorId);
       }

       @PutMapping("/me")
       public APIResponse<UserResponse> update(@CurrentUserId UUID actorId,
                                               @Valid @RequestBody UpdateProfileRequest request) {
           return userService.update(actorId, request);
       }

       @PostMapping("/me/change-password")
       public APIResponse<Void> changePassword(@CurrentUserId UUID actorId,
                                               @Valid @RequestBody ChangePasswordRequest request) {
           return userService.changePassword(actorId, request);
       }

       // ===== Tra cứu tài khoản (người dùng & service-to-service qua ApiGateway) =====

       @GetMapping("/{id}")
       public APIResponse<UserResponse> get(@PathVariable UUID id) {
           return userService.get(id);
       }

       @GetMapping("/by-email")
       public APIResponse<UserResponse> getByEmail(@RequestParam(required = false) String email) {
           return userService.getByEmail(email);
       }

       @PostMapping("/batch")
       public APIResponse<List<UserResponse>> listBatch(@Valid @RequestBody UserBatchRequest request) {
           return userService.listBatch(request);
       }

       // ===== Quản trị tài khoản =====

       @GetMapping
       @PreAuthorize("hasAuthority('user:read') and (hasRole('ADMIN') or hasRole('CENTER_MANAGER'))")
       public APIResponse<List<UserResponse>> list(@CurrentUserId UUID actorId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
           return userService.list(actorId, page, size);
       }

       @PostMapping
       @PreAuthorize("hasRole('ADMIN') or hasRole('CENTER_MANAGER')")
       public APIResponse<UserResponse> create(@CurrentUserId UUID actorId,
                                               @Valid @RequestBody ProvisionRequest request) {
           return userService.provision(actorId, request);
       }

       @PutMapping("/{id}")
       @PreAuthorize("hasAuthority('user:manage')")
       public APIResponse<UserResponse> update(@CurrentUserId UUID actorId,
                                               @PathVariable UUID id,
                                               @Valid @RequestBody UpdateProfileRequest request) {
           return userService.update(actorId, id, request);
       }

       @PatchMapping("/{id}/status")
       @PreAuthorize("hasAuthority('user:manage')")
       public APIResponse<UserResponse> updateStatus(@CurrentUserId UUID actorId,
                                                     @PathVariable UUID id,
                                                     @RequestParam UserStatus status) {
           return userService.updateStatus(actorId, id, status);
       }

       @DeleteMapping("/{id}")
       @PreAuthorize("hasAuthority('user:manage')")
       public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id) {
           return userService.delete(actorId, id);
       }

       @PutMapping("/{id}/roles/{roleId}")
       @PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")
       public APIResponse<UserResponse> assign(@CurrentUserId UUID actorId,
                                               @PathVariable UUID id,
                                               @PathVariable UUID roleId) {
           return userService.assign(actorId, id, roleId);
       }

       @DeleteMapping("/{id}/roles/{roleId}")
       @PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")
       public APIResponse<UserResponse> revoke(@CurrentUserId UUID actorId,
                                               @PathVariable UUID id,
                                               @PathVariable UUID roleId) {
           return userService.revoke(actorId, id, roleId);
       }
   }
   ```

---

### 4.3 Step 3: Refactor `AccountService` → `UserService` & `UserServiceImpl`
1. Create new file: `IdentityService/src/main/java/mss301/identityservice/service/UserService.java`
2. Delete old file: `IdentityService/src/main/java/mss301/identityservice/service/AccountService.java`
3. Interface definition of `UserService.java`:
   ```java
   package mss301.identityservice.service;

   import mss301.identityservice.dto.request.ChangePasswordRequest;
   import mss301.identityservice.dto.request.ProvisionRequest;
   import mss301.identityservice.dto.request.RegisterRequest;
   import mss301.identityservice.dto.request.UpdateProfileRequest;
   import mss301.identityservice.dto.request.UserBatchRequest;
   import mss301.identityservice.dto.response.APIResponse;
   import mss301.identityservice.dto.response.AuthenticatedUser;
   import mss301.identityservice.dto.response.UserResponse;
   import mss301.identityservice.enums.UserStatus;

   import java.util.List;
   import java.util.UUID;

   public interface UserService {
       // Dùng nội bộ bởi AuthenticationService / RegistrationService
       AuthenticatedUser verifyCredentials(String email, String password);

       UserResponse createVerified(RegisterRequest request);

       void resetPassword(String email, String passwordHash);

       void invalidateSessions(UUID userId);

       // Tài khoản của chính mình
       APIResponse<UserResponse> me(UUID actorId);

       APIResponse<UserResponse> update(UUID actorId, UpdateProfileRequest request);

       APIResponse<Void> changePassword(UUID actorId, ChangePasswordRequest request);

       // Tra cứu tài khoản (người dùng & service-to-service)
       APIResponse<UserResponse> get(UUID id);

       APIResponse<UserResponse> getByEmail(String email);

       APIResponse<List<UserResponse>> listBatch(UserBatchRequest request);

       // Quản trị tài khoản (theo quyền + phạm vi trung tâm)
       APIResponse<List<UserResponse>> list(UUID actorId, int page, int size);

       APIResponse<UserResponse> provision(UUID actorId, ProvisionRequest request);

       APIResponse<UserResponse> update(UUID actorId, UUID id, UpdateProfileRequest request);

       APIResponse<UserResponse> updateStatus(UUID actorId, UUID id, UserStatus status);

       APIResponse<Void> delete(UUID actorId, UUID id);

       APIResponse<UserResponse> assign(UUID actorId, UUID id, UUID roleId);

       APIResponse<UserResponse> revoke(UUID actorId, UUID id, UUID roleId);
   }
   ```

4. Create new file: `IdentityService/src/main/java/mss301/identityservice/service/Impl/UserServiceImpl.java`
5. Delete old file: `IdentityService/src/main/java/mss301/identityservice/service/Impl/AccountServiceImpl.java`
6. `UserServiceImpl.java` incorporates the new lookup methods:
   ```java
   package mss301.identityservice.service.Impl;

   // Imports ...
   import mss301.identityservice.dto.request.UserBatchRequest;
   import mss301.identityservice.service.UserService;
   import java.util.LinkedHashSet;
   import java.util.Set;

   @Service
   @RequiredArgsConstructor
   @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
   @Slf4j
   public class UserServiceImpl implements UserService {

       static int MAX_PAGE_SIZE = 100;
       static List<String> PRIVILEGED_ROLE_CODES = List.of(RoleCode.ADMIN.name(), RoleCode.CENTER_MANAGER.name());

       UserRepository userRepository;
       RoleRepository roleRepository;
       UserRoleRepository userRoleRepository;
       IdentityAuditRepository identityAuditRepository;
       UserMapper userMapper;
       IdentityAuditMapper identityAuditMapper;
       PasswordEncoder passwordEncoder;

       // Existing verifyCredentials, createVerified, resetPassword, invalidateSessions, me, update, changePassword ...

       @Override
       @Transactional(readOnly = true)
       public APIResponse<UserResponse> get(UUID id) {
           User user = findUser(id);
           UserResponse userResponse = userMapper.toResponse(user);
           return APIResponse.success("Lấy thông tin tài khoản thành công", userResponse);
       }

       @Override
       @Transactional(readOnly = true)
       public APIResponse<UserResponse> getByEmail(String email) {
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
           List<UserResponse> userResponses = userMapper.toResponseList(users);
           return APIResponse.success("Lấy danh sách tài khoản thành công", userResponses);
       }

       // Existing administrative list, provision, update, updateStatus, delete, assign, revoke, helper methods ...
   }
   ```

7. Update callers of `AccountService`:
   - In `AuthenticationServiceImpl.java`:
     - Replace `import mss301.identityservice.service.AccountService;` with `import mss301.identityservice.service.UserService;`
     - Change field `AccountService accountService;` to `UserService userService;`
     - Replace `accountService.verifyCredentials` with `userService.verifyCredentials`
     - Replace `accountService.invalidateSessions` with `userService.invalidateSessions`
   - In `RegistrationServiceImpl.java`:
     - Replace `import mss301.identityservice.service.AccountService;` with `import mss301.identityservice.service.UserService;`
     - Change field `AccountService accountService;` to `UserService userService;`
     - Replace `accountService.createVerified` with `userService.createVerified`
     - Replace `accountService.resetPassword` with `userService.resetPassword`
   - In `AuthenticatedUser.java`:
     - Update class javadoc from `AccountService` to `UserService`.

---

### 4.4 Step 4: Delete `/internal/**` Artifacts & Clean Configuration
1. **Delete Files**:
   - `IdentityService/src/main/java/mss301/identityservice/controller/InternalUserController.java`
   - `IdentityService/src/main/java/mss301/identityservice/service/InternalUserService.java`
   - `IdentityService/src/main/java/mss301/identityservice/service/Impl/InternalUserServiceImpl.java`
   - `IdentityService/src/main/java/mss301/identityservice/security/InternalSecurityConfig.java`
   - `IdentityService/src/main/java/mss301/identityservice/security/InternalSecretFilter.java`

2. **Clean `IdentityProperties.java`**:
   - Delete lines 63–65:
     ```java
     // Secret cho /internal/** (service-to-service). Để trống = khóa toàn bộ API nội bộ (fail-closed).
     @ToString.Exclude
     String internalSecret = "";
     ```

3. **Clean `application.yaml`**:
   - Delete line 68:
     ```yaml
     internal-secret: ${IDENTITY_INTERNAL_SECRET:}
     ```

4. **Clean `OpenApiConfig.java`**:
   - Delete import: `import mss301.identityservice.security.InternalSecretFilter;`
   - Delete constants:
     ```java
     private static final String INTERNAL_SCHEME = "internalSecret";
     private static final String INTERNAL_PREFIX = "/internal/";
     ```
   - Delete lines 57–61:
     ```java
     .addSecuritySchemes(INTERNAL_SCHEME, new SecurityScheme()
             .type(SecurityScheme.Type.APIKEY)
             .in(SecurityScheme.In.HEADER)
             .name(InternalSecretFilter.HEADER)
             .description("Chỉ cho /internal/** (service-to-service): giá trị IDENTITY_INTERNAL_SECRET."));
     ```
   - Delete lines 96–101 in `csrfRequirements()`:
     ```java
     // /internal/** không dùng bearer/cookie → chỉ yêu cầu internal secret, không CSRF.
     if (path.startsWith(INTERNAL_PREFIX)) {
         pathItem.readOperations().forEach(operation -> operation.setSecurity(
                 List.of(new SecurityRequirement().addList(INTERNAL_SCHEME))));
         return;
     }
     ```
   - In `CSRF_DESCRIPTION`: update `/auth/csrf` to `/api/v1/auth/csrf` and `/auth/login` to `/api/v1/auth/login`.

---

### 4.5 Step 5: Update `SecurityConfig.java`, `RoleController`, `PermissionController`
1. **In `SecurityConfig.java`**:
   - Replace lines 39–44:
     ```java
     static String[] TOKEN_FLOW_ENDPOINTS = {
             "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/csrf",
             "/api/v1/auth/mobile/login", "/api/v1/auth/mobile/refresh", "/api/v1/auth/mobile/logout",
             "/api/v1/auth/register", "/api/v1/auth/register/verify", "/api/v1/auth/register/resend-otp",
             "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password"
     };
     ```
   - Replace lines 56–58:
     ```java
     static Set<String> COOKIELESS_CSRF_EXEMPT_PATHS = Set.of(
             "/api/v1/auth/register", "/api/v1/auth/register/verify", "/api/v1/auth/register/resend-otp",
             "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password");
     ```
   - Replace line 60:
     ```java
     public static final String MOBILE_AUTH_PREFIX = "/api/v1/auth/mobile/";
     ```

2. **In `RoleController.java`**:
   - Change line 21:
     ```java
     @RequestMapping("/api/v1/roles")
     ```

3. **In `PermissionController.java`**:
   - Change line 20:
     ```java
     @RequestMapping("/api/v1/permissions")
     ```

---

### 4.6 Step 6: MapStruct Verification & Test Suite Synchronization
1. **MapStruct Verification**:
   - `UserMapper.java` already defines `toResponse(User)` and `toResponseList(List<User>)`.
   - `InternalUserResponse.java` can remain as a standard response DTO in `dto/response/` to satisfy `IdentityMapperTests.java` and maintain 100% test integrity without regressions.
2. **Synchronize `IdentityServiceApplicationTests.java`**:
   - Remove `INTERNAL_SECRET` constant and `p.add("identity.internal-secret", ...);` from `@DynamicPropertySource`.
   - Change field: `@Autowired UserService accounts;` (or `@Autowired UserService userService;`).
   - Update paths in tests:
     - `/auth/**` → `/api/v1/auth/**`
     - `/users/register/**` → `/api/v1/auth/register/**`
     - `/users/forgot-password` → `/api/v1/auth/forgot-password`
     - `/users/reset-password` → `/api/v1/auth/reset-password`
     - `/users/me` → `/api/v1/users/me`
     - `/users/**` → `/api/v1/users/**`
   - Update inter-service test cases:
     - Replace `mvc.perform(get("/internal/users/" + customer.getId()).header("X-Internal-Secret", INTERNAL_SECRET))` with:
       ```java
       mvc.perform(get("/api/v1/users/" + customer.getId()).header("Authorization", "Bearer " + token(admin)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.result.id").value(customer.getId().toString()))
               .andExpect(jsonPath("$.result.email").value(customer.getEmail()))
               .andExpect(jsonPath("$.result.status").value("ACTIVE"))
               .andExpect(jsonPath("$.result.roles[0]").value("CUSTOMER"));
       ```
     - Replace `GET /internal/users/by-email` with `GET /api/v1/users/by-email` with Bearer token.
     - Replace `POST /internal/users/batch` with `POST /api/v1/users/batch` with Bearer token.
     - Remove tests checking `X-Internal-Secret` 401 or `openApiDocumentsInternalSecretWithoutCsrf`.

---

## 5. Verification Method

To independently verify the implementation strategy:

1. **Compilation Verification**:
   Execute the following command in `d:\FA26\MSS\CareConnect\BE\IdentityService`:
   ```powershell
   .\mvnw.cmd clean compile
   ```
   **Pass Condition**: Build completes with `BUILD SUCCESS` and 0 compilation errors or MapStruct unmapped target warnings.

2. **Test Compilation Verification**:
   ```powershell
   .\mvnw.cmd test-compile
   ```
   **Pass Condition**: All test classes compile successfully against the new `/api/v1` routes and `UserService` interface.

3. **Full Test Execution Verification**:
   ```powershell
   .\mvnw.cmd test
   ```
   **Pass Condition**: 100% of test suites in `mss301.identityservice` pass without failure.

4. **File Deletion Verification**:
   Ensure none of the following files exist in the file system:
   ```powershell
   Test-Path "src/main/java/mss301/identityservice/controller/AccountController.java"        # False
   Test-Path "src/main/java/mss301/identityservice/service/AccountService.java"              # False
   Test-Path "src/main/java/mss301/identityservice/service/Impl/AccountServiceImpl.java"     # False
   Test-Path "src/main/java/mss301/identityservice/controller/InternalUserController.java"  # False
   Test-Path "src/main/java/mss301/identityservice/service/InternalUserService.java"        # False
   Test-Path "src/main/java/mss301/identityservice/service/Impl/InternalUserServiceImpl.java" # False
   Test-Path "src/main/java/mss301/identityservice/security/InternalSecurityConfig.java"     # False
   Test-Path "src/main/java/mss301/identityservice/security/InternalSecretFilter.java"       # False
   ```

5. **Security & Route Audit**:
   Verify no `/internal` string exists in `application.yaml` or `IdentityProperties.java`:
   ```powershell
   Select-String -Path "src/main/resources/application.yaml" -Pattern "internal"
   Select-String -Path "src/main/java/mss301/identityservice/config/IdentityProperties.java" -Pattern "internal"
   ```
   **Pass Condition**: 0 matches.
