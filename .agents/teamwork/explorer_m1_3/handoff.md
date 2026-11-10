# Handoff Report — Explorer M1.3 (Contracts, MapStruct, Lombok & Compile Verification Strategy)

## 1. Observation

### 1.1 MapStruct Compliance & Configuration in Milestone 1 Services

#### 1.1.1 MapStruct Configuration
Both `IdentityService` and `CareRecipientService` enforce strict MapStruct policies via dedicated `MapStructConfig` interfaces:

1. **`IdentityService/src/main/java/mss301/identityservice/mapper/MapStructConfig.java`** (Lines 15–20):
```java
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        builder = @Builder(disableBuilder = true))
public interface MapStructConfig {
}
```
2. **`CareRecipientService/src/main/java/mss301/carerecipientservice/mapper/MapStructConfig.java`** (Lines 15–20):
```java
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        builder = @Builder(disableBuilder = true))
public interface MapStructConfig {
}
```

**Key Architectural Facts directly observed**:
- **`unmappedTargetPolicy = ReportingPolicy.ERROR`**: Any destination property that is neither mapped from a source property nor explicitly ignored (`@Mapping(target = "fieldName", ignore = true)`) causes a **fatal compilation failure**.
- **`builder = @Builder(disableBuilder = true)`**: Lombok's `@Builder` does not set fields inherited from `@MappedSuperclass BaseEntity` (`id`, `createdAt`, `updatedAt`, `deletedAt`). Disabling the builder forces MapStruct to use the default no-args constructor and JavaBean setters (`@Getter @Setter`), which correctly set inherited properties.
- **Compiler annotation processor setup**: Both `pom.xml` files declare `lombok` followed by `mapstruct-processor:1.6.3` and `lombok-mapstruct-binding:0.2.0` in `<annotationProcessorPaths>`, guaranteeing Lombok generates getters/setters before MapStruct generates implementation classes.

#### 1.1.2 Existing Mappers & DTO Mappings
- **IdentityService mappers**:
  - `AuthenticationMapper.java`: Maps web & mobile `AuthenticationResponse` and `CsrfResponse`. Explicitly ignores `accessToken`/`refreshToken` for web and `csrfToken` for mobile.
  - `IdentityAuditMapper.java`: Maps `IdentityAudit`. Explicitly ignores `id` and `createdAt`.
  - `RegistrationMapper.java`: Maps `RegisterRequest` and `OtpResponse`.
  - `RoleMapper.java`: Uses `@IgnoreServerManagedFields`, ignores `active` and `rolePermissions` on `toRole`, and builds `RolePermissionId` in `toRolePermission`.
  - `UserMapper.java`:
    - Uses `@IgnoreServerManagedFields` + `@IgnoreServerManagedUserFields` on `toUser(RegisterRequest)` and `toUser(ProvisionRequest)`.
    - Partial update via `@BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)` on `update(UpdateProfileRequest request, @MappingTarget User user)`.
    - **CRITICAL OBSERVATION**: `UserMapper.java` (lines 32–36) defines:
      ```java
      @Mapping(target = "roles", expression = "java(user.effectiveRoleCodes())")
      InternalUserResponse toInternalResponse(User user);
      List<InternalUserResponse> toInternalResponseList(List<User> users);
      ```
      If `InternalUserResponse` is deleted during the removal of `/internal/**`, `UserMapper.java` and `IdentityMapperTests.java` will fail to compile unless `toInternalResponse` is removed or updated to use `UserResponse`.
- **CareRecipientService mappers**:
  - `CareRecipientMapper.java`: Uses `@IgnoreServerManagedFields` + `@IgnoreServerManagedRecipientFields`. Custom `calculateAge` default method. Maps `toSnapshotResponse` and `toSummaryResponse`.
  - `EmergencyContactMapper.java`: Uses `@IgnoreServerManagedFields`, `@Context CareRecipient recipient`, `@IterableMapping`.
  - `MedicationMapper.java`: Uses `@IgnoreServerManagedFields`, `@Context CareRecipient recipient`, `@Context UUID updatedBy`.
  - `RecipientManagerMapper.java`: Uses `@BeanMapping(ignoreByDefault = true)` for updates, explicitly maps `toAccessCheckResponse` and `toResponse(RecipientManager, IdentityUserResponse)`.

---

### 1.2 Lombok & Entity Integrity Verification (Requirement R5)

A comprehensive codebase grep for `@Data` within all entity packages reveals:
- **`IdentityService/src/main/java/mss301/identityservice/entity/`**:
  - `User.java`: `@Entity @Table ... @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`
  - `Role.java`: `@Entity @Table ... @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`
  - `Permission.java`: `@Entity @Table ... @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`
  - `IdentityAudit.java`: `@Entity @Table ... @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`
  - `UserRole.java`: `@Entity @Table ... @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`
  - `RolePermission.java`: `@Entity @Table ... @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`
  - `BaseEntity.java`: `@Getter @Setter @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor(access = AccessLevel.PROTECTED) @FieldDefaults(level = AccessLevel.PRIVATE) @MappedSuperclass`
  - *Matches for `@Data`*: Found only in `UserRoleId.java` (line 12) and `RolePermissionId.java` (line 12). Both are `@Embeddable` composite key classes (required by JPA spec for `equals()` and `hashCode()` on composite keys), NOT `@Entity`.
- **`CareRecipientService/src/main/java/mss301/carerecipientservice/entity/`**:
  - `CareRecipient.java`: `@Entity @Table ... @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`
  - `EmergencyContact.java`: `@Entity @Table ... @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`
  - `Medication.java`: `@Entity @Table ... @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`
  - `RecipientManager.java`: `@Entity @Table ... @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`
  - `BaseEntity.java`: `@Getter @Setter @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor(access = AccessLevel.PROTECTED) @FieldDefaults(level = AccessLevel.PRIVATE) @MappedSuperclass`
  - *Matches for `@Data`*: **0 occurrences**.

**Confirmation**: Zero occurrences of `@Data` exist on `@Entity` classes in either service.

---

### 1.3 Test Suites & Mocked Endpoints Inventory

#### 1.3.1 `IdentityServiceApplicationTests.java` (577 lines)
- **Injected Beans**:
  - Line 78: `@Autowired AccountService accounts;` — MUST be changed to `UserService`.
- **Authentication & Registration Endpoints Tested**:
  - Lines 112, 140, 144, 158, 362, 391, 392: `post("/users/register")` → MUST be `post("/api/v1/auth/register")`.
  - Line 162: `post("/users/register/resend-otp")` → MUST be `post("/api/v1/auth/register/resend-otp")`.
  - Lines 166, 263, 264, 269, 270: `post("/users/register/verify")` → MUST be `post("/api/v1/auth/register/verify")`.
  - Lines 150, 170, 274: `post("/users/forgot-password")` → MUST be `post("/api/v1/auth/forgot-password")`.
  - Lines 174, 277, 279: `post("/users/reset-password")` → MUST be `post("/api/v1/auth/reset-password")`.
  - Lines 148, 183, 210, 214, 369, 410: `post("/auth/login")` → MUST be `post("/api/v1/auth/login")`.
  - Lines 187, 417: `post("/auth/refresh")` → MUST be `post("/api/v1/auth/refresh")`.
  - Line 421: `post("/auth/logout")` → MUST be `post("/api/v1/auth/logout")`.
  - Lines 367, 408: `get("/auth/csrf")` → MUST be `get("/api/v1/auth/csrf")`.
  - Lines 218, 402: `post("/auth/mobile/login")` → MUST be `post("/api/v1/auth/mobile/login")`.
  - Lines 221, 222, 241: `post("/auth/mobile/refresh")` → MUST be `post("/api/v1/auth/mobile/refresh")`.
  - Line 312: `post("/auth/mobile/logout")` → MUST be `post("/api/v1/auth/mobile/logout")`.
- **User Account & Management Endpoints Tested**:
  - Lines 122, 126, 130, 226, 233, 240, 278, 288, 307, 313, 415, 453, 483: `get("/users/me")` → MUST be `get("/api/v1/users/me")`.
  - Line 382: `put("/users/me")` → MUST be `put("/api/v1/users/me")`.
  - Lines 134, 193, 284, 397: `get("/users")` → MUST be `get("/api/v1/users")`.
  - Lines 135, 246, 247, 294: `get("/users/" + ...)` → MUST be `get("/api/v1/users/" + ...)`.
  - Lines 253, 257: `post("/users")` → MUST be `post("/api/v1/users")`.
  - Lines 376, 378: `put("/users/" + ...)` → MUST be `put("/api/v1/users/" + ...)`.
  - Lines 248, 293: `patch("/users/" + ... + "/status...")` → MUST be `patch("/api/v1/users/" + ... + "/status...")`.
- **OpenAPI Path Assertions**:
  - Lines 200–206: Test assertions on OpenAPI paths (`paths['/auth/login']`, `paths['/users/register']`, `paths['/users/me']`, `paths['/auth/mobile/login']`) must be updated with the `/api/v1/` prefix.
- **Internal Endpoints & Secret**:
  - Line 42: `static final String INTERNAL_SECRET` and Line 74 `p.add("identity.internal-secret", ...)` → remove.
  - Lines 490–575: Remove tests for `/internal/users/**` with `X-Internal-Secret`. Replace with tests verifying that authenticated callers with JWT can call `GET /api/v1/users/{id}`, `GET /api/v1/users/by-email?email=...`, and `POST /api/v1/users/batch`.

#### 1.3.2 `IdentityMapperTests.java` (188 lines)
- Tests `UserMapper`, `RoleMapper`, and `AuthenticationMapper` directly without Spring context.
- Lines 9, 89–90, 99 test `userMapper.toInternalResponse(user)`. If `toInternalResponse` is replaced by `toResponse`, update assertions to target `UserResponse`.

#### 1.3.3 `CareRecipientApiTests.java` (735 lines)
- **Customer Endpoints Tested**:
  - Lines 131, 141, 149, 153, 159, 169, 180, 189, 200, 212, 215, 218, 221, 232, 240, 243, 251, 256, 261, 266, 274, 277, 645, 654, 686: Update `/care-recipients` → `/api/v1/care-recipients`.
  - Sub-resources:
    - Emergency contacts: `/care-recipients/{id}/emergency-contacts` → `/api/v1/care-recipients/{id}/emergency-contacts` (lines 338, 345, 349).
    - Medications: `/care-recipients/{id}/medications` → `/api/v1/care-recipients/{id}/medications` (lines 203, 300, 307, 311, 314, 320, 326, 329, 381).
    - Managers: `/care-recipients/{id}/managers` → `/api/v1/care-recipients/{id}/managers` (lines 371, 385, 396, 408, 428, 440, 456, 464, 475, 477, 479, 483, 488, 492, 496, 500, 503, 506, 515, 518, 522, 695, 708).
- **Admin Endpoints Tested**:
  - Lines 280, 284, 287: Update `/admin/care-recipients` → `/api/v1/care-recipients/admin` and `/admin/care-recipients/{id}` → `/api/v1/care-recipients/admin/{id}`.
- **Caregiver Endpoints Tested**:
  - Lines 618, 627, 635, 640, 656, 666: Update `/caregiver/care-recipients/{id}` → `/api/v1/care-recipients/caregiver/{id}`.
- **Internal Endpoints & Internal Secret Removal**:
  - Lines 55, 85, 86, 88: Remove `RECIPIENT_INTERNAL_SECRET`, `care-recipient.identity.internal-secret`, `care-recipient.internal-secret`, `care-recipient.booking.internal-secret`.
  - Lines 557–588: Replace tests for `/internal/care-recipients/**` with tests for `/api/v1/care-recipients/{id}/snapshot`, `/api/v1/care-recipients/{id}/access`, and `/api/v1/care-recipients/batch` with Bearer tokens.
  - Remove helper method `internal(MockHttpServletRequestBuilder)`.
  - Line 594: Update OpenAPI assertions to check `/api/v1/care-recipients/batch` instead of `/internal/care-recipients/batch`.

#### 1.3.4 Test Support Servers (`TestIdentityServer.java` & `TestBookingServer.java`)
- **`TestIdentityServer.java`** (Lines 75, 149–186):
  - Current handler routes `/internal/users` and requires `X-Internal-Secret`.
  - Worker must update routing context to `/api/v1/users`, remove the requirement for `X-Internal-Secret`, and verify incoming `Authorization: Bearer <token>` header (or allow valid Bearer token).
  - Supported paths: `/api/v1/users/batch`, `/api/v1/users/by-email`, `/api/v1/users/{id}`.
- **`TestBookingServer.java`** (Lines 37, 61–80):
  - Current handler routes `/internal/bookings/caregiver-access` and checks `X-Internal-Secret`.
  - Worker must update routing context to `/api/v1/bookings/caregiver-access`, remove `X-Internal-Secret` validation, and accept forwarded Bearer token.

---

### 1.4 Baseline Compilation Status & Hidden Compilation Trapdoors

1. **Current Baseline**:
   - `IdentityService`: `.\mvnw.cmd clean compile` completed with `BUILD SUCCESS` (11.075s, 99 source files).
   - `CareRecipientService`: `.\mvnw.cmd clean compile` completed with `BUILD SUCCESS` (16.464s, 102 source files).

2. **Hidden Compilation Trapdoors (High Risk of Build Breakage)**:
   - **Trapdoor 1 — `OpenApiConfig.java` in both services**:
     Both `IdentityService/OpenApiConfig.java` (lines 14, 29, 30, 57–61, 97–101) and `CareRecipientService/OpenApiConfig.java` (lines 14, 29, 30, 55–59, 89–93) import `InternalSecretFilter` and reference `INTERNAL_SCHEME` and `INTERNAL_PREFIX`. If a Worker deletes `InternalSecretFilter.java` without modifying `OpenApiConfig.java`, Maven compiler will throw `cannot find symbol: class InternalSecretFilter`.
   - **Trapdoor 2 — Feign Client Configs in CareRecipientService**:
     Both `IdentityClientConfig.java` (lines 7, 21) and `BookingClientConfig.java` (lines 7, 21) import `InternalSecretFilter.HEADER`. Deleting `InternalSecretFilter.java` will fail compilation unless these client configs are updated to use `Authorization: Bearer <token>`.
   - **Trapdoor 3 — `SecurityConfig.java` Path Constants in IdentityService**:
     `SecurityConfig.java` defines `TOKEN_FLOW_ENDPOINTS` (lines 39–44), `COOKIELESS_CSRF_EXEMPT_PATHS` (lines 56–58), and `MOBILE_AUTH_PREFIX` (line 60). If endpoints in `AuthenticationController` are moved to `/api/v1/auth/**` without updating these constants in `SecurityConfig`, compilation will succeed, but **every authentication and registration test will fail** (401 Unauthorized or 403 CSRF Invalid).
   - **Trapdoor 4 — MapStruct `toInternalResponse` in `UserMapper.java`**:
     If `InternalUserResponse.java` is deleted, `UserMapper.java` (lines 33, 36) will fail compilation (`cannot find symbol: class InternalUserResponse`).

---

## 2. Logic Chain

```
[Requirement R5: MapStruct & Entity Integrity]
  ├── Observation 1.1: MapStructConfig sets unmappedTargetPolicy = ReportingPolicy.ERROR in both services.
  │     └── Ingestion: Mappers will fail compilation if any DTO or Entity field is left unmapped or unignored.
  ├── Observation 1.2: Lombok @Data is 100% absent on entities; only @Getter @Setter + BaseEntity are used.
  │     └── Ingestion: Entities are already fully compliant with R5 and JPA Buddy requirements. No entity Lombok edits needed.
  └── Observation 1.4: MapStructConfig sets builder = @Builder(disableBuilder = true).
        └── Ingestion: Eliminating builders enables MapStruct to use standard setters, correctly populating BaseEntity fields.

[Requirement R4: Zero /internal/** & Public Endpoints Transition]
  ├── Observation 1.4 (Trapdoor 1): OpenApiConfig in both services imports InternalSecretFilter.
  │     └── Ingestion: Deleting InternalSecretFilter requires deleting imports and OpenAPI security scheme definitions in OpenApiConfig.
  ├── Observation 1.4 (Trapdoor 2): IdentityClientConfig and BookingClientConfig import InternalSecretFilter.HEADER.
  │     └── Ingestion: Interceptor must be updated to Feign Bearer forwarding before deleting InternalSecretFilter.
  └── Observation 1.1.2: UserMapper contains toInternalResponse(User).
        └── Ingestion: Replace with toResponse(User) returning UserResponse for the new /api/v1/users/** endpoints.

[Requirement R1, R2, R3: Controller/Service Unification & API Versioning]
  ├── Observation 1.3.1: AccountService is injected in IdentityServiceApplicationTests.
  │     └── Ingestion: Rename to UserService across application and test classes.
  ├── Observation 1.4 (Trapdoor 3): SecurityConfig token flow endpoints and CSRF exemptions lack /api/v1 prefix.
  │     └── Ingestion: SecurityConfig constants must be updated in lockstep with AuthenticationController annotations.
  └── Observation 1.3.3 & 1.3.4: TestIdentityServer and TestBookingServer inspect X-Internal-Secret on /internal/**.
        └── Ingestion: Test servers must be updated to route /api/v1/** and accept Authorization: Bearer <token>.
```

---

## 3. Caveats

1. **Test Execution Environment & Database Requirements**:
   - By default, both services run tests on in-memory H2 with PostgreSQL syntax mode (`jdbc:h2:mem:...;MODE=PostgreSQL`).
   - If PostgreSQL testing flags (`-Didentity.testing.postgres=true` or `-Dcare-recipient.testing.postgres=true`) are activated, running PostgreSQL instances on ports 55494 / 55495 are required. Workers should verify tests with default H2 first, as configured in the Maven build.
2. **JWKS RFC Standard Endpoint**:
   - The JWKS endpoint `/.well-known/jwks.json` is defined by RFC 7517. It **must not** be prefixed with `/api/v1/` and remains public (`permitAll()`).
3. **ApiGateway StripPrefix Coordination**:
   - In the current repository state, `ApiGateway` strips the first two segments (`stripPrefix(2)`). In Milestone 4, `stripPrefix` will be removed. During Milestone 1, service unit/integration tests (`IdentityServiceApplicationTests`, `CareRecipientApiTests`) invoke the service controllers directly via MockMvc, so they must be updated to use `/api/v1/**` immediately.
4. **Scope of Milestone 1**:
   - Only `IdentityService` and `CareRecipientService` are modified in Milestone 1. Upstream calls in `ProviderService` and `BookingService` that call `/internal/**` are addressed in Milestones 2 and 3.

---

## 4. Conclusion & Actionable Transformation Checklists

### 4.1 MapStruct Compliance Checklist
- [ ] In `IdentityService`:
  - In `UserMapper.java`:
    - Remove `InternalUserResponse toInternalResponse(User user);` and `List<InternalUserResponse> toInternalResponseList(List<User> users);`.
    - Ensure `toResponse(User user)` and `toResponseList(List<User> users)` are used by `UserService` for `GET /api/v1/users/{id}`, `GET /api/v1/users/by-email`, and `POST /api/v1/users/batch`.
    - Retain `@IgnoreServerManagedFields` and `@IgnoreServerManagedUserFields` on `toUser(RegisterRequest)` and `toUser(ProvisionRequest)`.
    - Retain `@BeanMapping(ignoreByDefault = true, ...)` on `update(UpdateProfileRequest, @MappingTarget User)`.
- [ ] In `CareRecipientService`:
  - In `CareRecipientMapper.java`:
    - Verify `toSnapshotResponse` and `toSummaryResponseList` continue to map correctly when invoked by `CareRecipientServiceImpl` for the unified endpoints.
  - In `RecipientManagerMapper.java`:
    - Verify `toAccessCheckResponse` continues to map correctly.
  - Ensure all mappers maintain `unmappedTargetPolicy = ReportingPolicy.ERROR` without adding unmapped fields.

### 4.2 Entity Integrity Checklist (R5)
- [ ] Retain `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)` on all entities (`User`, `Role`, `Permission`, `IdentityAudit`, `UserRole`, `RolePermission`, `CareRecipient`, `EmergencyContact`, `Medication`, `RecipientManager`).
- [ ] Strictly ensure **0** occurrences of `@Data` on any `@Entity` or `@MappedSuperclass`.
- [ ] Retain `@Data` only on `@Embeddable` composite key classes (`UserRoleId`, `RolePermissionId`).

### 4.3 Test Suite Alignment Checklist

#### IdentityService Test Updates:
- [ ] **`IdentityServiceApplicationTests.java`**:
  - Replace `@Autowired AccountService accounts;` with `@Autowired UserService users;` (or `@Autowired UserService accounts;`).
  - Update all endpoint path strings:
    - `/users/register` → `/api/v1/auth/register`
    - `/users/register/resend-otp` → `/api/v1/auth/register/resend-otp`
    - `/users/register/verify` → `/api/v1/auth/register/verify`
    - `/users/forgot-password` → `/api/v1/auth/forgot-password`
    - `/users/reset-password` → `/api/v1/auth/reset-password`
    - `/auth/login` → `/api/v1/auth/login`
    - `/auth/refresh` → `/api/v1/auth/refresh`
    - `/auth/logout` → `/api/v1/auth/logout`
    - `/auth/csrf` → `/api/v1/auth/csrf`
    - `/auth/mobile/login` → `/api/v1/auth/mobile/login`
    - `/auth/mobile/refresh` → `/api/v1/auth/mobile/refresh`
    - `/auth/mobile/logout` → `/api/v1/auth/mobile/logout`
    - `/users/me` → `/api/v1/users/me`
    - `/users` → `/api/v1/users`
    - `/users/{id}` → `/api/v1/users/{id}`
    - `/users/{id}/status` → `/api/v1/users/{id}/status`
  - Remove field `INTERNAL_SECRET` (line 42) and property `identity.internal-secret` (line 74).
  - Replace `/internal/users/**` tests (lines 490–575) with tests for `GET /api/v1/users/{id}`, `GET /api/v1/users/by-email`, and `POST /api/v1/users/batch` using Bearer authentication.
- [ ] **`IdentityMapperTests.java`**:
  - Update lines 9, 89–90, 99 to test `toResponse(user)` instead of `toInternalResponse(user)`.

#### CareRecipientService Test Updates:
- [ ] **`CareRecipientApiTests.java`**:
  - Update all endpoint path strings:
    - `/care-recipients` → `/api/v1/care-recipients`
    - `/care-recipients/{id}` → `/api/v1/care-recipients/{id}`
    - `/care-recipients/{id}/emergency-contacts` → `/api/v1/care-recipients/{id}/emergency-contacts`
    - `/care-recipients/{id}/medications` → `/api/v1/care-recipients/{id}/medications`
    - `/care-recipients/{id}/managers` → `/api/v1/care-recipients/{id}/managers`
    - `/admin/care-recipients` → `/api/v1/care-recipients/admin`
    - `/admin/care-recipients/{id}` → `/api/v1/care-recipients/admin/{id}`
    - `/caregiver/care-recipients/{id}` → `/api/v1/care-recipients/caregiver/{id}`
    - `/internal/care-recipients/{id}/snapshot` → `/api/v1/care-recipients/{id}/snapshot`
    - `/internal/care-recipients/{id}/access` → `/api/v1/care-recipients/{id}/access`
    - `/internal/care-recipients/batch` → `/api/v1/care-recipients/batch`
  - Remove `RECIPIENT_INTERNAL_SECRET` and internal-secret properties in `@DynamicPropertySource`.
  - Remove helper method `internal(MockHttpServletRequestBuilder)`.
  - Update OpenAPI path assertions to target `/api/v1/care-recipients/batch`.
- [ ] **`TestIdentityServer.java`**:
  - Change server context path to `/api/v1/users`.
  - Remove `X-Internal-Secret` validation; accept forwarded `Authorization: Bearer <token>`.
  - Handle `/api/v1/users/batch`, `/api/v1/users/by-email`, `/api/v1/users/{id}`.
- [ ] **`TestBookingServer.java`**:
  - Change server context path to `/api/v1/bookings/caregiver-access`.
  - Remove `X-Internal-Secret` validation; accept forwarded Bearer token.

---

### 4.4 Non-Test Support Classes Requiring Simultaneous Edits
To prevent build breakage when deleting `Internal*` files:
1. **`IdentityService/src/main/java/mss301/identityservice/config/swagger/OpenApiConfig.java`**:
   - Remove `import mss301.identityservice.security.InternalSecretFilter;`
   - Remove `INTERNAL_SCHEME` and `INTERNAL_PREFIX` constants.
   - Remove `components.addSecuritySchemes(INTERNAL_SCHEME, ...)` (lines 57–61).
   - Remove `if (path.startsWith(INTERNAL_PREFIX))` block (lines 97–101).
2. **`CareRecipientService/src/main/java/mss301/carerecipientservice/config/swagger/OpenApiConfig.java`**:
   - Remove `import mss301.carerecipientservice.security.InternalSecretFilter;`
   - Remove `INTERNAL_SCHEME` and `INTERNAL_PREFIX` constants.
   - Remove `components.addSecuritySchemes(INTERNAL_SCHEME, ...)` (lines 55–59).
   - Remove `if (path.startsWith(INTERNAL_PREFIX))` block (lines 89–93).
3. **`CareRecipientService/src/main/java/mss301/carerecipientservice/client/IdentityClientConfig.java` & `BookingClientConfig.java`**:
   - Remove `import mss301.carerecipientservice.security.InternalSecretFilter;`
   - Replace `template.header(InternalSecretFilter.HEADER, ...)` with JWT Forwarding `RequestInterceptor` attaching `Authorization: Bearer <token>`.
4. **`IdentityProperties.java` & `CareRecipientProperties.java`**:
   - Remove `internalSecret` fields.

---

## 5. Verification Method

### 5.1 Compilation Verification Commands (Windows / PowerShell)

Run Maven clean compile for both services using the Maven Wrapper:

```powershell
# 1. Compile IdentityService
cd d:\FA26\MSS\CareConnect\BE\IdentityService
.\mvnw.cmd clean compile

# 2. Compile CareRecipientService
cd d:\FA26\MSS\CareConnect\BE\CareRecipientService
.\mvnw.cmd clean compile
```

**Pass Criterion**: Both commands must exit with code 0 and output `[INFO] BUILD SUCCESS`. Any MapStruct unmapped target error or missing symbol will result in immediate compilation failure.

---

### 5.2 Unit & Integration Test Verification Commands

Run full test suites for both services:

```powershell
# 1. Test IdentityService
cd d:\FA26\MSS\CareConnect\BE\IdentityService
.\mvnw.cmd test

# 2. Test CareRecipientService
cd d:\FA26\MSS\CareConnect\BE\CareRecipientService
.\mvnw.cmd test
```

**Pass Criterion**: All tests in `IdentityServiceApplicationTests`, `IdentityMapperTests`, `EmailServiceTests`, `CareRecipientApiTests`, `CareRecipientMapperTests`, `IdentityErrorDecoderTests`, and `RecipientAccessRulesTests` must execute and pass without failures or errors.

---

### 5.3 Static Audit Verification Commands

To independently audit compliance against requirements R1–R5:

```powershell
# Verify 0 occurrences of @Data on entities
Select-String -Path "d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\entity\*.java" -Pattern "@Data"
Select-String -Path "d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\entity\*.java" -Pattern "@Data"

# Verify 0 occurrences of Internal controllers, services, configs, or filters
Get-ChildItem -Path "d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java" -Filter "*Internal*" -Recurse
Get-ChildItem -Path "d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java" -Filter "*Internal*" -Recurse

# Verify all controllers have /api/v1 prefix (except JwksController)
Select-String -Path "d:\FA26\MSS\CareConnect\BE\IdentityService\src\main\java\mss301\identityservice\controller\*.java" -Pattern "@RequestMapping"
Select-String -Path "d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\*.java" -Pattern "@RequestMapping"
```

**Invalidation Conditions**:
1. Any compilation error from MapStruct reporting unmapped target fields (`ReportingPolicy.ERROR`).
2. Any test returning 404 due to endpoint path mismatches or missing `/api/v1` prefix.
3. Any test returning 401/403 due to `SecurityConfig.java` path constants not including `/api/v1/auth/**`.
4. Any reference remaining to `InternalSecretFilter` or `X-Internal-Secret` in source or tests.
