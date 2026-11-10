# Handoff Report — Explorer Survey 1 (IdentityService & CareRecipientService)

## 1. Observation

### 1.1 IdentityService Architecture & Codebase State

#### Controllers
1. **`AccountController.java`**
   - **Path**: `d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\controller\AccountController.java`
   - **Current Annotation**: `@RequestMapping("/users")` (Line 24) — lacks `/api/v1/` prefix.
   - **Injected Services**: `AccountService accountService` (Line 29), `RegistrationService registrationService` (Line 30).
   - **Endpoints currently present**:
     - *Registration / OTP / Password (lines 34–62)*:
       - `POST /users/register` (`RegisterRequest` → `APIResponse<OtpResponse>`)
       - `POST /users/register/resend-otp` (`EmailRequest` → `APIResponse<OtpResponse>`)
       - `POST /users/register/verify` (`VerifyOtpRequest` → `APIResponse<UserResponse>`)
       - `POST /users/forgot-password` (`EmailRequest` → `APIResponse<Void>`)
       - `POST /users/reset-password` (`ResetPasswordRequest` → `APIResponse<Void>`)
     - *Self-Account Operations (lines 66–81)*:
       - `GET /users/me` (`@CurrentUserId UUID actorId` → `APIResponse<UserResponse>`)
       - `PUT /users/me` (`@CurrentUserId UUID actorId`, `@Valid UpdateProfileRequest` → `APIResponse<UserResponse>`)
       - `POST /users/me/change-password` (`@CurrentUserId UUID actorId`, `@Valid ChangePasswordRequest` → `APIResponse<Void>`)
     - *Administration Operations (lines 85–141)*:
       - `GET /users` (`@CurrentUserId UUID actorId`, `page`, `size` → `APIResponse<List<UserResponse>>`) with `@PreAuthorize("hasAuthority('user:read') and (hasRole('ADMIN') or hasRole('CENTER_MANAGER'))")`
       - `GET /users/{id}` (`@CurrentUserId UUID actorId`, `@PathVariable UUID id` → `APIResponse<UserResponse>`)
       - `POST /users` (`@CurrentUserId UUID actorId`, `@Valid ProvisionRequest` → `APIResponse<UserResponse>`) with `@PreAuthorize("hasRole('ADMIN') or hasRole('CENTER_MANAGER')")`
       - `PUT /users/{id}` (`@CurrentUserId UUID actorId`, `@PathVariable UUID id`, `@Valid UpdateProfileRequest` → `APIResponse<UserResponse>`) with `@PreAuthorize("hasAuthority('user:manage')")`
       - `PATCH /users/{id}/status` (`@CurrentUserId UUID actorId`, `@PathVariable UUID id`, `@RequestParam UserStatus status` → `APIResponse<UserResponse>`) with `@PreAuthorize("hasAuthority('user:manage')")`
       - `DELETE /users/{id}` (`@CurrentUserId UUID actorId`, `@PathVariable UUID id` → `APIResponse<Void>`) with `@PreAuthorize("hasAuthority('user:manage')")`
       - `PUT /users/{id}/roles/{roleId}` (`@CurrentUserId UUID actorId`, `id`, `roleId` → `APIResponse<UserResponse>`) with `@PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")`
       - `DELETE /users/{id}/roles/{roleId}` (`@CurrentUserId UUID actorId`, `id`, `roleId` → `APIResponse<UserResponse>`) with `@PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")`

2. **`AuthenticationController.java`**
   - **Path**: `d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\controller\AuthenticationController.java`
   - **Current Annotation**: `@RequestMapping("/auth")` (Line 24) — lacks `/api/v1/` prefix.
   - **Injected Services**: `AuthenticationService authenticationService` (Line 29).
   - **Endpoints currently present**:
     - `GET /auth/csrf` (`CsrfToken` → `APIResponse<CsrfResponse>`)
     - `POST /auth/login` (`LoginRequest`, `HttpServletResponse` → `APIResponse<AuthenticationResponse>`)
     - `POST /auth/refresh` (`HttpServletRequest`, `HttpServletResponse` → `APIResponse<AuthenticationResponse>`)
     - `POST /auth/logout` (`HttpServletRequest`, `HttpServletResponse` → `APIResponse<Void>`)
     - `POST /auth/mobile/login` (`LoginRequest` → `APIResponse<AuthenticationResponse>`)
     - `POST /auth/mobile/refresh` (`RefreshRequest` → `APIResponse<AuthenticationResponse>`)
     - `POST /auth/mobile/logout` (`RefreshRequest`, `HttpServletRequest` → `APIResponse<Void>`)
     - `POST /auth/logout-all` (`@CurrentUserId UUID actorId`, `HttpServletResponse` → `APIResponse<Void>`)

3. **`RoleController.java`**
   - **Path**: `d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\controller\RoleController.java`
   - **Current Annotation**: `@RequestMapping("/roles")` (Line 21) — lacks `/api/v1/` prefix.
   - **Endpoints**: `GET /roles`, `POST /roles`, `PUT /roles/{id}/permissions` with `@PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")`.

4. **`PermissionController.java`**
   - **Path**: `d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\controller\PermissionController.java`
   - **Current Annotation**: `@RequestMapping("/permissions")` (Line 20) — lacks `/api/v1/` prefix.
   - **Endpoints**: `GET /permissions` with `@PreAuthorize("hasRole('ADMIN') and hasAuthority('role:manage')")`.

5. **`JwksController.java`**
   - **Path**: `d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\controller\JwksController.java`
   - **Endpoint**: `GET /.well-known/jwks.json` (Line 22). Standard OAuth2/JOSE endpoint required by downstream Resource Servers (`JwtDecoder`).

6. **`InternalUserController.java`**
   - **Path**: `d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\controller\InternalUserController.java`
   - **Current Annotation**: `@RequestMapping("/internal/users")` (Line 19).
   - **Endpoints**:
     - `GET /internal/users/{userId}` → `internalUserService.get(userId)`
     - `GET /internal/users/by-email` → `internalUserService.getByEmail(email)`
     - `POST /internal/users/batch` → `internalUserService.list(request)`
   - **Security**: Protected by `InternalSecurityConfig` via `X-Internal-Secret` header.

#### Services & Service Implementations
- `AccountService.java` (`service/AccountService.java`) is an interface; `AccountServiceImpl.java` (`service/Impl/AccountServiceImpl.java`) is its implementation.
- `AuthenticationService.java` is an interface; `AuthenticationServiceImpl.java` is in `service/Impl/`.
- `RegistrationService.java` is an interface; `RegistrationServiceImpl.java` is in `service/Impl/`.
- `RoleService.java` is an interface; `RoleServiceImpl.java` is in `service/Impl/`.
- `InternalUserService.java` is an interface; `InternalUserServiceImpl.java` is in `service/Impl/`.
- `CurrentIdentityService.java`, `EmailService.java`, `IdentityStateService.java`, `JwtService.java` are all interfaces with implementations under `service/Impl/`.
- 100% of repositories in `repository/` (`UserRepository`, `RoleRepository`, `PermissionRepository`, `RolePermissionRepository`, `UserRoleRepository`, `IdentityAuditRepository`) are `interface`.

#### Internal Security
- **`InternalSecurityConfig.java`**: `d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\security\InternalSecurityConfig.java` defines a separate security filter chain for `/internal/**` (Line 25 `INTERNAL_PATH = "/internal/**"`).
- **`InternalSecretFilter.java`**: `d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\security\InternalSecretFilter.java` checks header `X-Internal-Secret` against `identityProperties.getInternalSecret()`.
- **References in Configuration**: `IdentityProperties.java` (Line 65 `internalSecret`), `OpenApiConfig.java` (Line 29 `internalSecret`, Line 57 `INTERNAL_SCHEME`), `application.yaml` (Line 68 `internal-secret: ${IDENTITY_INTERNAL_SECRET:}`).

#### Entities & Lombok
- `User.java`, `Role.java`, `Permission.java`, `RolePermission.java`, `UserRole.java`, `IdentityAudit.java`, `BaseEntity.java`:
  All use `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`.
  **Zero entity classes use `@Data`**. (Only `@Embeddable` key classes `RolePermissionId` and `UserRoleId` use `@Data`, conforming to standard JPA requirements for composite keys).
- **MapStruct**: `MapStructConfig.java` enforces:
  ```java
  @MapperConfig(
      componentModel = MappingConstants.ComponentModel.SPRING,
      unmappedTargetPolicy = ReportingPolicy.ERROR,
      injectionStrategy = InjectionStrategy.CONSTRUCTOR,
      builder = @Builder(disableBuilder = true)
  )
  ```
  All mappers (`UserMapper`, `RoleMapper`, `RegistrationMapper`, `AuthenticationMapper`, `IdentityAuditMapper`) inherit this configuration.

#### Feign Clients
- No Feign clients exist in `IdentityService` (no `client/` package). IdentityService is strictly a provider.

---

### 1.2 CareRecipientService Architecture & Codebase State

#### Controllers
1. **`AdminCareRecipientController.java`**
   - **Path**: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\AdminCareRecipientController.java`
   - **Current Annotation**: `@RequestMapping("/admin/care-recipients")` (Line 19) — separated by role `ADMIN`, lacks `/api/v1/`.
   - **Endpoints**:
     - `GET /admin/care-recipients` (`@PreAuthorize("hasRole('ADMIN')")`)
     - `GET /admin/care-recipients/{id}` (`@PreAuthorize("hasRole('ADMIN')")`)
   - **Delegates to**: `careRecipientService.listForAdmin` and `careRecipientService.getForAdmin`.

2. **`CaregiverCareRecipientController.java`**
   - **Path**: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\CaregiverCareRecipientController.java`
   - **Current Annotation**: `@RequestMapping("/caregiver/care-recipients")` (Line 17) — separated by role `CAREGIVER`, lacks `/api/v1/`.
   - **Endpoints**:
     - `GET /caregiver/care-recipients/{id}` (`@PreAuthorize("hasRole('CAREGIVER')")`)
   - **Delegates to**: `caregiverRecipientService.getSnapshot(actorId, id)`.

3. **`CareRecipientController.java`**
   - **Path**: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\CareRecipientController.java`
   - **Current Annotation**: `@RequestMapping("/care-recipients")` (Line 23) — lacks `/api/v1/`.
   - **Endpoints**:
     - `POST /care-recipients` (`@PreAuthorize("hasRole('CUSTOMER')")`)
     - `GET /care-recipients` (`@PreAuthorize("hasRole('CUSTOMER')")`)
     - `GET /care-recipients/{id}` (`@PreAuthorize("hasRole('CUSTOMER')")`)
     - `PUT /care-recipients/{id}` (`@PreAuthorize("hasRole('CUSTOMER')")`)
     - `PATCH /care-recipients/{id}/status` (`@PreAuthorize("hasRole('CUSTOMER')")`)
     - `DELETE /care-recipients/{id}` (`@PreAuthorize("hasRole('CUSTOMER')")`)

4. **`InternalCareRecipientController.java`**
   - **Path**: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\InternalCareRecipientController.java`
   - **Current Annotation**: `@RequestMapping("/internal/care-recipients")` (Line 21).
   - **Endpoints**:
     - `GET /internal/care-recipients/{id}/snapshot`
     - `GET /internal/care-recipients/{id}/access?userId=&minLevel=`
     - `POST /internal/care-recipients/batch`
   - **Delegates to**: `internalCareRecipientService`.

5. **Sub-resource Controllers**:
   - **`EmergencyContactController.java`**: `@RequestMapping("/care-recipients/{recipientId}/emergency-contacts")` (Line 20).
   - **`MedicationController.java`**: `@RequestMapping("/care-recipients/{recipientId}/medications")` (Line 20).
   - **`RecipientManagerController.java`**: `@RequestMapping("/care-recipients/{recipientId}/managers")` (Line 20).
   - None currently have `/api/v1/` prefix.

#### Services & Service Implementations
- **`CareRecipientService.java`** (interface in `service/`) & **`CareRecipientServiceImpl.java`** (class in `service/Impl/`).
- **`CaregiverRecipientService.java`** & **`CaregiverRecipientServiceImpl.java`**: Single-method service (`getSnapshot`) that calls `CaregiverAssignmentService.isAssigned(...)` then `InternalCareRecipientService.getSnapshot(...)`.
- **`CaregiverAssignmentService.java`** & **`CaregiverAssignmentServiceImpl.java`**: Single-method service (`isAssigned`) that wraps `BookingClient.checkCaregiverAccess(...)`.
- **`InternalCareRecipientService.java`** & **`InternalCareRecipientServiceImpl.java`**: Service for snapshot, access check, and batch lookups.
- **`EmergencyContactService.java`** & `EmergencyContactServiceImpl.java`.
- **`MedicationService.java`** & `MedicationServiceImpl.java`.
- **`RecipientManagerService.java`** & `RecipientManagerServiceImpl.java`.
- **`RecipientAccessService.java`** & `RecipientAccessServiceImpl.java` (internal domain authorization).
- **`IdentityUserService.java`** & `IdentityUserServiceImpl.java` (wraps `IdentityClient`).
- 100% of repositories in `repository/` (`CareRecipientRepository`, `EmergencyContactRepository`, `MedicationRepository`, `RecipientManagerRepository`) are `interface`. (`CareRecipientSpecifications` is a static Specification predicate helper).

#### Internal Security
- **`InternalSecurityConfig.java`**: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\security\InternalSecurityConfig.java` creates filter chain for `/internal/**` (Line 25 `INTERNAL_PATH = "/internal/**"`).
- **`InternalSecretFilter.java`**: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\security\InternalSecretFilter.java`.
- **References in Configuration**: `CareRecipientProperties.java` (Lines 20, 55, 67), `OpenApiConfig.java` (Lines 29, 58), `application.yaml` (Lines 77, 86, 91).

#### Entities & Lombok
- `CareRecipient.java`, `EmergencyContact.java`, `Medication.java`, `RecipientManager.java`, `BaseEntity.java`:
  All use `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`.
  **Zero entity classes use `@Data`**. Fully conforms to R5.
- **MapStruct**: `MapStructConfig.java` sets `unmappedTargetPolicy = ReportingPolicy.ERROR`, `componentModel = MappingConstants.ComponentModel.SPRING`, `builder = @Builder(disableBuilder = true)`.

#### Feign Clients in `client/`
- **`IdentityClient.java`**:
  - `name = "identity", url = "${care-recipient.identity.url}"`
  - Endpoints:
    - `GET /internal/users/{userId}`
    - `GET /internal/users/by-email`
    - `POST /internal/users/batch`
  - Config: `IdentityClientConfig.java` attaches `X-Internal-Secret`.
  - Fallback: `IdentityClientFallbackFactory.java`, `IdentityErrorDecoder.java`.
- **`BookingClient.java`**:
  - `name = "booking", url = "${care-recipient.booking.url}"`
  - Endpoints:
    - `GET /internal/bookings/caregiver-access`
  - Config: `BookingClientConfig.java` attaches `X-Internal-Secret`.
  - Fallback: `BookingClientFallbackFactory.java`, `BookingErrorDecoder.java`.

---

### 1.3 Downstream / Upstream Client Calls
1. **`BookingService` calls**:
   - `CareRecipientClient.java` calls `/internal/care-recipients/{recipientId}/access` and `/internal/care-recipients/{recipientId}/snapshot`.
   - `IdentityClient.java` calls `POST /internal/users/batch`.
2. **`ProviderService` calls**:
   - `IdentityClient.java` calls `GET /internal/users/{userId}`.
3. **`ApiGateway`**:
   - `GatewayPaths.java` defines routes for `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/care-recipients/**`, `/api/v1/admin/care-recipients/**`, `/api/v1/caregiver/care-recipients/**`.
   - `RouteConfig.java` applies `.before(stripPrefix(GatewayPaths.API_PREFIX_SEGMENTS))` which strips the first 2 segments (`/api/v1`).
   - `InternalPathBlockFilter.java` blocks any path containing segment `internal`.

---

## 2. Logic Chain

```
[Requirement R1: Unified Domain Aggregates]
  ├── Observation: CareRecipient has 3 separate controllers split by role (AdminCareRecipientController, CaregiverCareRecipientController, CareRecipientController)
  │     └── Ingestion: Merge into single CareRecipientController with @PreAuthorize per method; eliminate AdminCareRecipientController and CaregiverCareRecipientController.
  ├── Observation: CareRecipient has fragmented services (CaregiverRecipientService, CaregiverAssignmentService)
  │     └── Ingestion: Merge logic directly into CareRecipientService & CareRecipientServiceImpl; delete standalone fragmented interfaces and implementations.
  └── Observation: IdentityService uses AccountController / AccountService while domain entity is User
        └── Ingestion: Rename AccountController -> UserController, AccountService -> UserService, AccountServiceImpl -> UserServiceImpl.

[Requirement R2: Pure Interface & Implementation Layers]
  ├── Observation: All services in IdentityService and CareRecipientService are ALREADY interfaces in service/ with classes in service/Impl/
  │     └── Ingestion: Maintain this clean interface-impl separation during class renames and merges.
  └── Observation: Repositories are all interfaces in repository/
        └── Ingestion: Compliant; no changes needed to repository interfaces.

[Requirement R3: IdentityService Auth vs User & API Versioning]
  ├── Observation: AccountController currently hosts register, resend-otp, verify, forgot-password, reset-password
  │     └── Ingestion: Move all 5 endpoints to AuthenticationController with prefix /api/v1/auth/**. Remove RegistrationService dependency from UserController.
  ├── Observation: All controller RequestMapping annotations in both services lack /api/v1/
  │     └── Ingestion: Add /api/v1/ prefix to UserController (/api/v1/users), AuthenticationController (/api/v1/auth), RoleController (/api/v1/roles), PermissionController (/api/v1/permissions), CareRecipientController (/api/v1/care-recipients), EmergencyContactController (/api/v1/care-recipients/{recipientId}/emergency-contacts), MedicationController (/api/v1/care-recipients/{recipientId}/medications), RecipientManagerController (/api/v1/care-recipients/{recipientId}/managers).
  └── Observation: SecurityConfig token exemptions (TOKEN_FLOW_ENDPOINTS, COOKIELESS_CSRF_EXEMPT_PATHS, MOBILE_AUTH_PREFIX) in IdentityService use hardcoded paths without /api/v1
        └── Ingestion: Synchronize SecurityConfig constants with /api/v1/auth/** paths to avoid 401/403 CSRF failures on public auth endpoints.

[Requirement R4: Elimination of /internal/** & Routing via ApiGateway with Feign JWT Forwarding]
  ├── Observation: InternalUserController, InternalCareRecipientController, InternalSecurityConfig, InternalSecretFilter, X-Internal-Secret exist in both services
  │     └── Ingestion: Delete InternalUserController, InternalCareRecipientController, InternalSecurityConfig, InternalSecretFilter from both services. Remove internalSecret from properties and application.yaml.
  ├── Observation: Other services (BookingService, ProviderService, CareRecipientService) rely on endpoints currently in InternalUserController and InternalCareRecipientController
  │     └── Ingestion:
  │           - IdentityService: Expose batch and profile lookups in UserController (/api/v1/users/batch, /api/v1/users/{userId}, /api/v1/users/by-email).
  │           - CareRecipientService: Expose snapshot, access check, and batch in CareRecipientController (/api/v1/care-recipients/{id}/snapshot, /api/v1/care-recipients/{id}/access, /api/v1/care-recipients/batch).
  ├── Observation: Existing Feign clients send X-Internal-Secret directly to target services
  │     └── Ingestion:
  │           - Replace interceptors with RequestInterceptor that extracts Bearer token from caller's SecurityContext (JwtAuthenticationToken) or request Authorization header and forwards "Authorization: Bearer <token>".
  │           - Re-point Feign client URLs to ApiGateway (http://localhost:8080/api/v1/...).
  └── Observation: ApiGateway RouteConfig currently strips "/api/v1" via stripPrefix(2)
        └── Ingestion: If services now declare /api/v1 in their @RequestMapping, ApiGateway MUST NOT strip /api/v1, OR routing will 404. Delete stripPrefix(2) in ApiGateway RouteConfig or harmonize path handling!

[Requirement R5: Entity & MapStruct Mapper Compliance]
  ├── Observation: Both IdentityService and CareRecipientService entities already strictly use @Getter @Setter (no @Data)
  │     └── Ingestion: Perfect compliance with JPA Buddy / Lombok rules; retain existing entity structure.
  └── Observation: MapStructConfig enforces unmappedTargetPolicy = ReportingPolicy.ERROR
        └── Ingestion: Any changes to DTOs or entity mappings will fail compilation unless explicit mappings/ignores are supplied.
```

---

## 3. Caveats

1. **ApiGateway StripPrefix Coordination**:
   `RouteConfig.java` in `ApiGateway` currently uses `.before(stripPrefix(GatewayPaths.API_PREFIX_SEGMENTS))` (stripping `/api/v1`). If downstream services change `@RequestMapping` to include `/api/v1`, keeping `stripPrefix(2)` in `ApiGateway` would cause requests forwarded to services to lack `/api/v1`, resulting in 404 Not Found. The Gateway specialist and Service refactorers must align: either `stripPrefix(2)` is removed so `/api/v1/...` is passed verbatim, or paths will mismatch.
2. **JWKS Endpoint RFC Standard**:
   `GET /.well-known/jwks.json` is defined by RFC 7517 and Spring Security Resource Server specifications. Downstream services configure `jwt.jwk-set-uri` directly to `http://identity-service:8081/.well-known/jwks.json`. It should NOT be prefixed with `/api/v1/` nor routed through ApiGateway, as documented in existing architecture and tests.
3. **Actor Scope vs Service-to-Service Scope in UserController**:
   `AccountServiceImpl.get(actorId, id)` currently checks `checkManageScope(actor, target, Permissions.USER_READ)`. When `ProviderService` or `CareRecipientService` calls `GET /api/v1/users/{userId}` via Feign with a forwarded Customer token, a plain Customer will fail `checkManageScope` (yielding 403 Forbidden). When migrating from `InternalUserController` to `UserController`, the user lookup for basic profile data (name, phone, status, roles) must be authorized for authenticated users (`@PreAuthorize("isAuthenticated()")`) rather than restricted strictly to Admin/Center Manager.
4. **Scope of this Survey**:
   This survey specifically covered `IdentityService` and `CareRecipientService`. Interacting services (`ProviderService`, `BookingService`, `ApiGateway`) were inspected for incoming and outgoing Feign contracts, but their internal refactoring details (e.g. Provider 11 controllers) are covered by Survey 2.

---

## 4. Conclusion & Actionable Transformation Matrix

### 4.1 IdentityService Modifications

| Action | Target File / Class | Details / Lines |
|---|---|---|
| **Rename** | `AccountController.java` → `UserController.java` | Change `@RequestMapping("/users")` to `@RequestMapping("/api/v1/users")`. Rename class and constructor to `UserController`. Inject `UserService` instead of `AccountService`. Remove `RegistrationService`. |
| **Modify** | `AuthenticationController.java` | Change `@RequestMapping("/auth")` to `@RequestMapping("/api/v1/auth")`. Add injection of `RegistrationService registrationService`. Move 5 endpoints from `AccountController`: `/api/v1/auth/register`, `/register/resend-otp`, `/register/verify`, `/forgot-password`, `/reset-password`. Prefix all existing endpoints with `/api/v1/auth`. |
| **Modify** | `RoleController.java` | Change `@RequestMapping("/roles")` to `@RequestMapping("/api/v1/roles")`. |
| **Modify** | `PermissionController.java` | Change `@RequestMapping("/permissions")` to `@RequestMapping("/api/v1/permissions")`. |
| **Keep as-is** | `JwksController.java` | Keep `GET /.well-known/jwks.json` as RFC-compliant endpoint. |
| **Delete** | `InternalUserController.java` | Remove entire class (`controller/InternalUserController.java`). |
| **Delete** | `InternalUserService.java` | Remove entire interface (`service/InternalUserService.java`). |
| **Delete** | `InternalUserServiceImpl.java` | Remove entire class (`service/Impl/InternalUserServiceImpl.java`). |
| **Rename** | `AccountService.java` → `UserService.java` | Rename interface to `UserService`. Add methods for user lookup: `getProfile(UUID id)`, `getByEmail(String email)`, `listBatch(UserBatchRequest request)`. |
| **Rename** | `AccountServiceImpl.java` → `UserServiceImpl.java` | Rename class to `UserServiceImpl implements UserService`. Implement `getProfile`, `getByEmail`, `listBatch`. |
| **Modify** | `AuthenticationServiceImpl.java` | Update injected dependency from `AccountService` to `UserService`. |
| **Modify** | `RegistrationServiceImpl.java` | Update injected dependency from `AccountService` to `UserService`. |
| **Delete** | `InternalSecurityConfig.java` | Remove entire class (`security/InternalSecurityConfig.java`). |
| **Delete** | `InternalSecretFilter.java` | Remove entire class (`security/InternalSecretFilter.java`). |
| **Modify** | `SecurityConfig.java` | Update `TOKEN_FLOW_ENDPOINTS`, `COOKIELESS_CSRF_EXEMPT_PATHS`, `MOBILE_AUTH_PREFIX` to use `/api/v1/auth/**`. Remove any references to old `/users/register...` paths. |
| **Modify** | `OpenApiConfig.java` | Remove `INTERNAL_SCHEME`, `INTERNAL_PREFIX`, and `InternalSecretFilter.HEADER` references. |
| **Modify** | `IdentityProperties.java` | Remove field `internalSecret` (line 65). |
| **Modify** | `application.yaml` | Remove `internal-secret: ${IDENTITY_INTERNAL_SECRET:}` (line 68). |
| **Update Tests** | `IdentityServiceApplicationTests.java` | Update mocked endpoints from `/users/register`, `/auth/...`, `/internal/users/...` to `/api/v1/auth/...` and `/api/v1/users/...`. Replace `AccountService accounts` with `UserService users`. |

---

### 4.2 CareRecipientService Modifications

| Action | Target File / Class | Details / Lines |
|---|---|---|
| **Delete** | `AdminCareRecipientController.java` | Remove class (`controller/AdminCareRecipientController.java`). Logic migrated into `CareRecipientController`. |
| **Delete** | `CaregiverCareRecipientController.java` | Remove class (`controller/CaregiverCareRecipientController.java`). Logic migrated into `CareRecipientController`. |
| **Delete** | `InternalCareRecipientController.java` | Remove class (`controller/InternalCareRecipientController.java`). Endpoints migrated into `CareRecipientController`. |
| **Modify** | `CareRecipientController.java` | Update `@RequestMapping("/care-recipients")` to `@RequestMapping("/api/v1/care-recipients")`. Merge in: <br>1. Admin list & get: `GET /api/v1/care-recipients/admin`, `GET /api/v1/care-recipients/admin/{id}` (`@PreAuthorize("hasRole('ADMIN')")`).<br>2. Caregiver snapshot: `GET /api/v1/care-recipients/caregiver/{id}` (`@PreAuthorize("hasRole('CAREGIVER')")`).<br>3. Snapshot & access check: `GET /api/v1/care-recipients/{id}/snapshot`, `GET /api/v1/care-recipients/{id}/access`, `POST /api/v1/care-recipients/batch`. |
| **Modify** | `EmergencyContactController.java` | Update to `@RequestMapping("/api/v1/care-recipients/{recipientId}/emergency-contacts")`. |
| **Modify** | `MedicationController.java` | Update to `@RequestMapping("/api/v1/care-recipients/{recipientId}/medications")`. |
| **Modify** | `RecipientManagerController.java` | Update to `@RequestMapping("/api/v1/care-recipients/{recipientId}/managers")`. |
| **Delete** | `CaregiverRecipientService.java` & `CaregiverRecipientServiceImpl.java` | Delete interface and class. Migrate snapshot check logic to `CareRecipientServiceImpl`. |
| **Delete** | `CaregiverAssignmentService.java` & `CaregiverAssignmentServiceImpl.java` | Delete interface and class. Migrate `BookingClient.checkCaregiverAccess` call to `CareRecipientServiceImpl`. |
| **Delete** | `InternalCareRecipientService.java` & `InternalCareRecipientServiceImpl.java` | Delete interface and class. Migrate snapshot, access check, and batch methods into `CareRecipientService` / `CareRecipientServiceImpl`. |
| **Modify** | `CareRecipientService.java` & `CareRecipientServiceImpl.java` | Add methods: `getSnapshotForCaregiver(actorId, recipientId)`, `getSnapshot(id)`, `checkAccess(id, userId, minLevel)`, `listBatch(request)`. Inject `BookingClient bookingClient`. |
| **Delete** | `InternalSecurityConfig.java` | Remove class (`security/InternalSecurityConfig.java`). |
| **Delete** | `InternalSecretFilter.java` | Remove class (`security/InternalSecretFilter.java`). |
| **Modify** | `IdentityClient.java` | Change endpoints: `GET /api/v1/users/{userId}`, `GET /api/v1/users/by-email`, `POST /api/v1/users/batch`. Change URL to ApiGateway base URL (`http://localhost:8080`). |
| **Modify** | `IdentityClientConfig.java` | Replace `InternalSecretFilter.HEADER` interceptor with JWT Forwarding `RequestInterceptor` (`Authorization: Bearer <token>`). |
| **Modify** | `BookingClient.java` | Change endpoint: `GET /api/v1/bookings/caregiver-access`. Change URL to ApiGateway base URL (`http://localhost:8080`). |
| **Modify** | `BookingClientConfig.java` | Replace `InternalSecretFilter.HEADER` interceptor with JWT Forwarding `RequestInterceptor` (`Authorization: Bearer <token>`). |
| **Modify** | `OpenApiConfig.java` | Remove `INTERNAL_SCHEME`, `INTERNAL_PREFIX`, and `InternalSecretFilter.HEADER` references. |
| **Modify** | `CareRecipientProperties.java` | Remove `internalSecret`, `identity.internalSecret`, `booking.internalSecret`. |
| **Modify** | `application.yaml` | Remove lines 77, 86, 91 (`internal-secret` keys). |
| **Update Tests** | `CareRecipientApiTests.java` | Update endpoint paths to `/api/v1/care-recipients/...`, `/api/v1/care-recipients/admin/...`, `/api/v1/care-recipients/caregiver/...`. Update test mock servers for Identity and Booking. |

---

## 5. Verification Method

To independently verify all findings and validate subsequent refactoring:

1. **Verify Current State**:
   - Check files exist and observe current `@RequestMapping` annotations:
     ```powershell
     Select-String -Path "d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\controller\*.java" -Pattern "@RequestMapping"
     Select-String -Path "d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\*.java" -Pattern "@RequestMapping"
     ```
   - Check existing entity annotations:
     ```powershell
     Select-String -Path "d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\entity\*.java" -Pattern "@Data"
     Select-String -Path "d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\entity\*.java" -Pattern "@Data"
     ```

2. **Verify After Refactoring**:
   - Compile IdentityService:
     ```powershell
     cd d:\FA26\MSS\CareConnect\BE\IdentityService
     .\mvnw.cmd clean compile
     ```
   - Compile CareRecipientService:
     ```powershell
     cd d:\FA26\MSS\CareConnect\BE\CareRecipientService
     .\mvnw.cmd clean compile
     ```
   - Run unit/integration tests:
     ```powershell
     cd d:\FA26\MSS\CareConnect\BE\IdentityService
     .\mvnw.cmd test
     cd d:\FA26\MSS\CareConnect\BE\CareRecipientService
     .\mvnw.cmd test
     ```
   - Invalidation conditions:
     - Any compile failure due to missing MapStruct mappings (`ReportingPolicy.ERROR`).
     - Any test failure due to route mismatch between ApiGateway `stripPrefix` and service `@RequestMapping("/api/v1/...")`.
     - Any 403 Forbidden on inter-service calls if `UserController` restricts user profile lookups excessively.
