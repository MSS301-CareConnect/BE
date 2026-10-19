# Implementation Blueprint — Milestone 1: CareRecipientService Refactoring

## 1. Observation

### 1.1 Current Architecture & Verification Baseline
- **Maven Baseline**:
  Command: `.\mvnw.cmd test` in `d:\FA26\MSS\CareConnect\BE\CareRecipientService`
  Result: `Tests run: 59, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS (32.034 s)`.
- **Entity Compliance**:
  Inspected `CareRecipient.java`, `EmergencyContact.java`, `Medication.java`, `RecipientManager.java`, `BaseEntity.java`.
  All use `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`.
  Zero `@Data` annotations exist on entities (fully compliant with R5).
- **MapStruct Compliance**:
  `MapStructConfig.java` enforces `unmappedTargetPolicy = ReportingPolicy.ERROR`, `componentModel = MappingConstants.ComponentModel.SPRING`, `builder = @Builder(disableBuilder = true)`. Mappers: `CareRecipientMapper`, `EmergencyContactMapper`, `MedicationMapper`, `RecipientManagerMapper`.

---

### 1.2 Controllers Inventory
1. **`CareRecipientController.java`**
   - Path: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\CareRecipientController.java`
   - Current annotation: `@RequestMapping("/care-recipients")` (Line 23) — missing `/api/v1` prefix.
   - Current endpoints:
     - `POST /care-recipients` (`@PreAuthorize("hasRole('CUSTOMER')")`, lines 30–35)
     - `GET /care-recipients` (`@PreAuthorize("hasRole('CUSTOMER')")`, lines 37–45)
     - `GET /care-recipients/{id}` (`@PreAuthorize("hasRole('CUSTOMER')")`, lines 47–51)
     - `PUT /care-recipients/{id}` (`@PreAuthorize("hasRole('CUSTOMER')")`, lines 53–59)
     - `PATCH /care-recipients/{id}/status` (`@PreAuthorize("hasRole('CUSTOMER')")`, lines 61–67)
     - `DELETE /care-recipients/{id}` (`@PreAuthorize("hasRole('CUSTOMER')")`, lines 69–73)
2. **`AdminCareRecipientController.java`**
   - Path: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\AdminCareRecipientController.java`
   - Current annotation: `@RequestMapping("/admin/care-recipients")` (Line 19) — separated by role `ADMIN`.
   - Current endpoints:
     - `GET /admin/care-recipients` (`@PreAuthorize("hasRole('ADMIN')")`, lines 26–35)
     - `GET /admin/care-recipients/{id}` (`@PreAuthorize("hasRole('ADMIN')")`, lines 37–41)
   - Injected service: `CareRecipientService careRecipientService`.
3. **`CaregiverCareRecipientController.java`**
   - Path: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\CaregiverCareRecipientController.java`
   - Current annotation: `@RequestMapping("/caregiver/care-recipients")` (Line 17) — separated by role `CAREGIVER`.
   - Current endpoint:
     - `GET /caregiver/care-recipients/{id}` (`@PreAuthorize("hasRole('CAREGIVER')")`, lines 24–28)
   - Injected service: `CaregiverRecipientService caregiverRecipientService`.
4. **`InternalCareRecipientController.java`**
   - Path: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\InternalCareRecipientController.java`
   - Current annotation: `@RequestMapping("/internal/care-recipients")` (Line 21).
   - Current endpoints:
     - `GET /internal/care-recipients/{id}/snapshot` (lines 28–31)
     - `GET /internal/care-recipients/{id}/access` (lines 33–38)
     - `POST /internal/care-recipients/batch` (lines 40–43)
   - Injected service: `InternalCareRecipientService internalCareRecipientService`.
5. **Sub-resource Controllers**:
   - `EmergencyContactController.java`: `@RequestMapping("/care-recipients/{recipientId}/emergency-contacts")` (Line 20) — lacks `/api/v1` prefix.
   - `MedicationController.java`: `@RequestMapping("/care-recipients/{recipientId}/medications")` (Line 20) — lacks `/api/v1` prefix.
   - `RecipientManagerController.java`: `@RequestMapping("/care-recipients/{recipientId}/managers")` (Line 20) — lacks `/api/v1` prefix.

---

### 1.3 Services to Merge & Delete
1. **`CaregiverRecipientService.java` & `Impl/CaregiverRecipientServiceImpl.java`**:
   - Files:
     - `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\CaregiverRecipientService.java` (12 lines)
     - `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\Impl\CaregiverRecipientServiceImpl.java` (38 lines)
   - Single method: `APIResponse<RecipientSnapshotResponse> getSnapshot(UUID actorId, UUID recipientId)`.
   - Logic: calls `caregiverAssignmentService.isAssigned(recipientId, actorId)`; if false, throws `AppException(ErrorCode.RECIPIENT_NOT_FOUND)`; else returns `internalCareRecipientService.getSnapshot(recipientId)`.
2. **`CaregiverAssignmentService.java` & `Impl/CaregiverAssignmentServiceImpl.java`**:
   - Files:
     - `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\CaregiverAssignmentService.java` (14 lines)
     - `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\Impl\CaregiverAssignmentServiceImpl.java` (33 lines)
   - Single method: `boolean isAssigned(UUID recipientId, UUID caregiverUserId)`.
   - Logic: calls `BookingClient.checkCaregiverAccess(caregiverUserId, recipientId)`. If response null or result null, throws `AppException(ErrorCode.BOOKING_UNAVAILABLE)`. Returns `response.getResult().isAllowed()`.
3. **`InternalCareRecipientService.java` & `Impl/InternalCareRecipientServiceImpl.java`**:
   - Files:
     - `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\InternalCareRecipientService.java` (21 lines)
     - `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\Impl\InternalCareRecipientServiceImpl.java` (79 lines)
   - Methods:
     - `getSnapshot(UUID id)`: loads `recipient` (`findByIdAndDeletedAtIsNull`), `activeMedications`, `primaryContact`, calls `careRecipientMapper.toSnapshotResponse`.
     - `checkAccess(UUID id, UUID userId, RecipientAccessLevel minLevel)`: calls `recipientAccessService.findAccessLevel`, constructs `AccessCheckResponse`.
     - `list(RecipientBatchRequest request)`: loads `recipients` (`findAllByIdInAndDeletedAtIsNull`), calls `careRecipientMapper.toSummaryResponseList`.

---

### 1.4 Internal Security & Properties Artifacts
1. **`InternalSecurityConfig.java`**:
   - Path: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\security\InternalSecurityConfig.java`
   - Defines secondary `@Order(1)` filter chain matching `/internal/**`.
2. **`InternalSecretFilter.java`**:
   - Path: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\security\InternalSecretFilter.java`
   - Defines `HEADER = "X-Internal-Secret"` and `INTERNAL_SERVICE_AUTHORITY = "INTERNAL_SERVICE"`.
3. **`CareRecipientProperties.java`**:
   - Path: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\config\CareRecipientProperties.java`
   - Line 20: `String internalSecret = "";`
   - Line 55: `String internalSecret = "";` (under `Identity`)
   - Line 67: `String internalSecret = "";` (under `Booking`)
4. **`application.yaml`**:
   - Path: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\resources\application.yaml`
   - Line 77: `internal-secret: ${CARE_RECIPIENT_INTERNAL_SECRET:}`
   - Line 86: `internal-secret: ${CARE_RECIPIENT_IDENTITY_INTERNAL_SECRET:}`
   - Line 91: `internal-secret: ${CARE_RECIPIENT_BOOKING_INTERNAL_SECRET:}`
5. **`OpenApiConfig.java`**:
   - Path: `d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\config\swagger\OpenApiConfig.java`
   - Line 14: `import mss301.carerecipientservice.security.InternalSecretFilter;`
   - Lines 29–30: `INTERNAL_SCHEME = "internalSecret"`, `INTERNAL_PREFIX = "/internal/"`
   - Lines 55–59: `.addSecuritySchemes(INTERNAL_SCHEME, ...)`
   - Lines 90–94: `if (path.startsWith(INTERNAL_PREFIX))` block.
6. **`BookingClientConfig.java` & `IdentityClientConfig.java`**:
   - Currently import `InternalSecretFilter.HEADER` and add header `X-Internal-Secret`.

---

## 2. Logic Chain

```
[Requirement R1: Domain Aggregate Unification]
  ├── Observation: CareRecipient has 3 separate controllers split by role (AdminCareRecipientController, CaregiverCareRecipientController, CareRecipientController)
  │     └── Ingestion: Merge into single CareRecipientController with @RequestMapping("/api/v1/care-recipients") and method-level @PreAuthorize. Delete the other two.
  └── Observation: CareRecipient has 3 fragmented services (CaregiverRecipientService, CaregiverAssignmentService, InternalCareRecipientService)
        └── Ingestion:
              - Inject BookingClient into CareRecipientServiceImpl.
              - Convert CaregiverAssignmentService.isAssigned into private helper in CareRecipientServiceImpl.
              - Add getSnapshotForCaregiver, getSnapshot, checkAccess, listBatch directly to CareRecipientService / CareRecipientServiceImpl.
              - Delete all 6 fragmented service files.

[Requirement R3: API Versioning]
  ├── Observation: All CareRecipient controllers lack /api/v1 prefix
  │     └── Ingestion:
  │           - CareRecipientController -> @RequestMapping("/api/v1/care-recipients")
  │           - EmergencyContactController -> @RequestMapping("/api/v1/care-recipients/{recipientId}/emergency-contacts")
  │           - MedicationController -> @RequestMapping("/api/v1/care-recipients/{recipientId}/medications")
  │           - RecipientManagerController -> @RequestMapping("/api/v1/care-recipients/{recipientId}/managers")

[Requirement R4: Zero /internal/** & ApiGateway Feign Forwarding]
  ├── Observation: InternalCareRecipientController, InternalSecurityConfig, InternalSecretFilter enforce secret-based /internal/** calls
  │     └── Ingestion:
  │           - Move snapshot, access check, batch endpoints to CareRecipientController under /api/v1/care-recipients/** with @PreAuthorize("isAuthenticated()").
  │           - Delete InternalCareRecipientController, InternalSecurityConfig, InternalSecretFilter.
  │           - Remove internalSecret from CareRecipientProperties, application.yaml, OpenApiConfig.
  ├── Observation: BookingClientConfig & IdentityClientConfig depend on InternalSecretFilter.HEADER
  │     └── Ingestion:
  │           - Implement FeignAuthForwardingInterceptor in client/ forwarding Authorization Bearer token from SecurityContext/RequestContext and X-Request-Id from MDC.
  │           - Update BookingClient to @GetMapping("/api/v1/bookings/caregiver-access") and IdentityClient to @GetMapping("/api/v1/users/...").
  │           - Wire FeignAuthForwardingInterceptor into BookingClientConfig and IdentityClientConfig.

[Requirement R5: MapStruct & Entity Integrity]
  ├── Observation: All entities already use @Getter @Setter (zero @Data). MapStruct mappers compile with ReportingPolicy.ERROR.
  │     └── Ingestion:
  │           - No changes needed to entity classes or mapper signatures.
  │           - Ensure new service methods use existing CareRecipientMapper.toSnapshotResponse and toSummaryResponseList.
```

---

## 3. Caveats

1. **Downstream Callers of `/internal/care-recipients/**`**:
   - `BookingService` currently calls `/internal/care-recipients/{id}/access` and `/snapshot` via `CareRecipientClient.java`.
   - In Milestone 3, `BookingService`'s `CareRecipientClient` will be updated to point to `/api/v1/care-recipients/{id}/access` and `/snapshot`.
   - Exposing these on `CareRecipientController` now ensures `CareRecipientService` is fully ready for Milestone 3 and 4.
2. **Gateway Route Harmonization**:
   - `GatewayPaths.java` in `ApiGateway` currently defines `"/api/v1/care-recipients/**"`, `"/api/v1/admin/care-recipients/**"`, and `"/api/v1/caregiver/care-recipients/**"`.
   - Because all endpoints are now consolidated under `/api/v1/care-recipients/**`, all routes route to `CareRecipientService` cleanly.
3. **Scope for Inter-Service Endpoints**:
   - `GET /{id}/snapshot`, `GET /{id}/access`, `POST /batch` are secured with `@PreAuthorize("isAuthenticated()")` because inter-service calls forward the authenticated user's JWT (`CUSTOMER`, `CAREGIVER`, `ADMIN`, or `CENTER_MANAGER`).

---

## 4. Conclusion & Actionable Implementation Blueprint

### 4.1 Summary of File Actions

| Action | Relative File Path | Rationale |
|---|---|---|
| **DELETE** | `controller/AdminCareRecipientController.java` | Endpoints merged into `CareRecipientController` |
| **DELETE** | `controller/CaregiverCareRecipientController.java` | Endpoints merged into `CareRecipientController` |
| **DELETE** | `controller/InternalCareRecipientController.java` | Endpoints migrated to `CareRecipientController` |
| **REWRITE** | `controller/CareRecipientController.java` | Unified controller at `/api/v1/care-recipients` |
| **MODIFY** | `controller/EmergencyContactController.java` | Add `/api/v1` prefix to `@RequestMapping` |
| **MODIFY** | `controller/MedicationController.java` | Add `/api/v1` prefix to `@RequestMapping` |
| **MODIFY** | `controller/RecipientManagerController.java` | Add `/api/v1` prefix to `@RequestMapping` |
| **DELETE** | `service/CaregiverRecipientService.java` | Merged into `CareRecipientService` |
| **DELETE** | `service/Impl/CaregiverRecipientServiceImpl.java` | Merged into `CareRecipientServiceImpl` |
| **DELETE** | `service/CaregiverAssignmentService.java` | Merged into `CareRecipientService` |
| **DELETE** | `service/Impl/CaregiverAssignmentServiceImpl.java` | Merged into `CareRecipientServiceImpl` |
| **DELETE** | `service/InternalCareRecipientService.java` | Merged into `CareRecipientService` |
| **DELETE** | `service/Impl/InternalCareRecipientServiceImpl.java` | Merged into `CareRecipientServiceImpl` |
| **REWRITE** | `service/CareRecipientService.java` | Add 4 merged methods to interface |
| **REWRITE** | `service/Impl/CareRecipientServiceImpl.java` | Implement 4 methods, inject `BookingClient` |
| **DELETE** | `security/InternalSecurityConfig.java` | Remove `/internal/**` filter chain |
| **DELETE** | `security/InternalSecretFilter.java` | Remove `X-Internal-Secret` filter |
| **CREATE** | `client/FeignAuthForwardingInterceptor.java` | Standard Bearer JWT & MDC forwarding interceptor |
| **MODIFY** | `client/BookingClient.java` | Change path to `/api/v1/bookings/caregiver-access` |
| **MODIFY** | `client/BookingClientConfig.java` | Use `FeignAuthForwardingInterceptor` |
| **MODIFY** | `client/IdentityClient.java` | Change paths to `/api/v1/users/**` |
| **MODIFY** | `client/IdentityClientConfig.java` | Use `FeignAuthForwardingInterceptor` |
| **MODIFY** | `config/CareRecipientProperties.java` | Remove `internalSecret` fields |
| **MODIFY** | `src/main/resources/application.yaml` | Remove `internal-secret` keys |
| **MODIFY** | `config/swagger/OpenApiConfig.java` | Remove `INTERNAL_SCHEME` and `InternalSecretFilter` |
| **MODIFY** | `src/test/java/.../CareRecipientApiTests.java` | Update paths to `/api/v1/**`, replace internal secret tests |

---

### 4.2 Exact Implementation Code

#### 4.2.1 `CareRecipientController.java`
**Target File**: `src/main/java/mss301/carerecipientservice/controller/CareRecipientController.java`
```java
package mss301.carerecipientservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;
import mss301.carerecipientservice.security.CurrentUserId;
import mss301.carerecipientservice.service.CareRecipientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-recipients")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Care recipient", description = "Quản lý hồ sơ người bệnh (Customer, Admin, Caregiver, Inter-service)")
public class CareRecipientController {

    CareRecipientService careRecipientService;

    // ===== Customer Endpoints =====

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientDetailResponse> create(@CurrentUserId UUID actorId,
                                                           @Valid @RequestBody CareRecipientCreateRequest request) {
        return careRecipientService.create(actorId, request);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> list(@CurrentUserId UUID actorId,
                                                                        @RequestParam(required = false) RecipientStatus status,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size) {
        return careRecipientService.list(actorId, status, keyword, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientDetailResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return careRecipientService.get(actorId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientResponse> update(@CurrentUserId UUID actorId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody CareRecipientUpdateRequest request) {
        return careRecipientService.update(actorId, id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<CareRecipientResponse> updateStatus(@CurrentUserId UUID actorId,
                                                           @PathVariable UUID id,
                                                           @RequestParam RecipientStatus status) {
        return careRecipientService.updateStatus(actorId, id, status);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return careRecipientService.delete(actorId, id);
    }

    // ===== Admin Endpoints (Merged from AdminCareRecipientController) =====

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> listForAdmin(@RequestParam(required = false) RecipientStatus status,
                                                                                @RequestParam(required = false) String keyword,
                                                                                @RequestParam(required = false) UUID createdBy,
                                                                                @RequestParam(defaultValue = "false") boolean includeDeleted,
                                                                                @RequestParam(defaultValue = "0") int page,
                                                                                @RequestParam(defaultValue = "20") int size) {
        return careRecipientService.listForAdmin(status, keyword, createdBy, includeDeleted, page, size);
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CareRecipientDetailResponse> getForAdmin(@PathVariable UUID id) {
        return careRecipientService.getForAdmin(id);
    }

    // ===== Caregiver Endpoints (Merged from CaregiverCareRecipientController) =====

    @GetMapping("/caregiver/{id}")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<RecipientSnapshotResponse> getForCaregiver(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return careRecipientService.getSnapshotForCaregiver(actorId, id);
    }

    // ===== Inter-Service Endpoints (Migrated from InternalCareRecipientController) =====

    @GetMapping("/{id}/snapshot")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<RecipientSnapshotResponse> getSnapshot(@PathVariable UUID id) {
        return careRecipientService.getSnapshot(id);
    }

    @GetMapping("/{id}/access")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<AccessCheckResponse> checkAccess(@PathVariable UUID id,
                                                        @RequestParam UUID userId,
                                                        @RequestParam(defaultValue = "VIEWER") RecipientAccessLevel minLevel) {
        return careRecipientService.checkAccess(id, userId, minLevel);
    }

    @PostMapping("/batch")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<CareRecipientSummaryResponse>> listBatch(@Valid @RequestBody RecipientBatchRequest request) {
        return careRecipientService.listBatch(request);
    }
}
```

---

#### 4.2.2 `CareRecipientService.java` (Interface)
**Target File**: `src/main/java/mss301/carerecipientservice/service/CareRecipientService.java`
```java
package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;

import java.util.List;
import java.util.UUID;

public interface CareRecipientService {
    // Customer
    APIResponse<CareRecipientDetailResponse> create(UUID actorId, CareRecipientCreateRequest request);

    APIResponse<PageResponse<CareRecipientSummaryResponse>> list(UUID actorId, RecipientStatus status, String keyword,
                                                                 int page, int size);

    APIResponse<CareRecipientDetailResponse> get(UUID actorId, UUID id);

    APIResponse<CareRecipientResponse> update(UUID actorId, UUID id, CareRecipientUpdateRequest request);

    APIResponse<CareRecipientResponse> updateStatus(UUID actorId, UUID id, RecipientStatus status);

    APIResponse<Void> delete(UUID actorId, UUID id);

    // Admin
    APIResponse<PageResponse<CareRecipientSummaryResponse>> listForAdmin(RecipientStatus status, String keyword,
                                                                         UUID createdBy, boolean includeDeleted,
                                                                         int page, int size);

    APIResponse<CareRecipientDetailResponse> getForAdmin(UUID id);

    // Caregiver (hồ sơ người bệnh caregiver được phân công — Booking xác nhận)
    APIResponse<RecipientSnapshotResponse> getSnapshotForCaregiver(UUID actorId, UUID id);

    // Inter-Service / Public contract (thay thế /internal/**)
    APIResponse<RecipientSnapshotResponse> getSnapshot(UUID id);

    APIResponse<AccessCheckResponse> checkAccess(UUID id, UUID userId, RecipientAccessLevel minLevel);

    APIResponse<List<CareRecipientSummaryResponse>> listBatch(RecipientBatchRequest request);
}
```

---

#### 4.2.3 `CareRecipientServiceImpl.java` (Implementation)
**Target File**: `src/main/java/mss301/carerecipientservice/service/Impl/CareRecipientServiceImpl.java`
```java
package mss301.carerecipientservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.client.BookingClient;
import mss301.carerecipientservice.client.dto.BookingApiResponse;
import mss301.carerecipientservice.client.dto.CaregiverAccessResponse;
import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.request.EmergencyContactRequest;
import mss301.carerecipientservice.dto.request.MedicationRequest;
import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.PageResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.EmergencyContact;
import mss301.carerecipientservice.entity.Medication;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RecipientStatus;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.mapper.CareRecipientMapper;
import mss301.carerecipientservice.mapper.EmergencyContactMapper;
import mss301.carerecipientservice.mapper.MedicationMapper;
import mss301.carerecipientservice.mapper.RecipientManagerMapper;
import mss301.carerecipientservice.repository.CareRecipientRepository;
import mss301.carerecipientservice.repository.CareRecipientSpecifications;
import mss301.carerecipientservice.repository.EmergencyContactRepository;
import mss301.carerecipientservice.repository.MedicationRepository;
import mss301.carerecipientservice.repository.RecipientManagerRepository;
import mss301.carerecipientservice.service.CareRecipientService;
import mss301.carerecipientservice.service.RecipientAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareRecipientServiceImpl implements CareRecipientService {

    static int MAX_PAGE_SIZE = 100;

    CareRecipientRepository careRecipientRepository;
    RecipientManagerRepository recipientManagerRepository;
    MedicationRepository medicationRepository;
    EmergencyContactRepository emergencyContactRepository;
    RecipientAccessService recipientAccessService;
    BookingClient bookingClient;
    CareRecipientMapper careRecipientMapper;
    RecipientManagerMapper recipientManagerMapper;
    MedicationMapper medicationMapper;
    EmergencyContactMapper emergencyContactMapper;

    @Override
    @Transactional
    public APIResponse<CareRecipientDetailResponse> create(UUID actorId, CareRecipientCreateRequest request) {
        checkCoordinates(request.getLatitude(), request.getLongitude());
        checkSinglePrimaryContact(request.getEmergencyContacts());
        checkMedicationDates(request.getMedications());
        CareRecipient recipient = careRecipientMapper.toCareRecipient(request, actorId);
        recipient.setStatus(RecipientStatus.ACTIVE);
        CareRecipient savedRecipient = careRecipientRepository.save(recipient);

        RecipientManager owner = recipientManagerMapper.toRecipientManager(savedRecipient, actorId,
                request.getRelationshipType(), RecipientAccessLevel.OWNER, true, actorId);
        recipientManagerRepository.save(owner);

        List<EmergencyContact> emergencyContacts = emergencyContactRepository.saveAll(
                emergencyContactMapper.toEmergencyContactList(request.getEmergencyContacts(), savedRecipient));
        List<Medication> medications = medicationRepository.saveAll(
                medicationMapper.toMedicationList(request.getMedications(), savedRecipient, actorId));
        careRecipientRepository.flush();
        log.info("Care recipient {} created by user {}", savedRecipient.getId(), actorId);
        CareRecipientDetailResponse detailResponse = careRecipientMapper.toDetailResponse(savedRecipient,
                RecipientAccessLevel.OWNER, medications, emergencyContacts);
        return APIResponse.success("Tạo hồ sơ người bệnh thành công", detailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> list(UUID actorId, RecipientStatus status,
                                                                        String keyword, int page, int size) {
        PageRequest pageRequest = toPageRequest(page, size, "recipient.createdAt");
        Page<RecipientManager> memberships = recipientManagerRepository.findAll(
                CareRecipientSpecifications.managedBy(actorId, status, keyword), pageRequest);
        List<CareRecipientSummaryResponse> summaryResponses = memberships.getContent().stream()
                .map(membership -> careRecipientMapper.toSummaryResponse(membership.getRecipient(), membership))
                .toList();
        PageResponse<CareRecipientSummaryResponse> pageResponse = PageResponse.of(memberships, summaryResponses);
        return APIResponse.success("Lấy danh sách hồ sơ thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CareRecipientDetailResponse> get(UUID actorId, UUID id) {
        RecipientManager membership = recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.VIEWER);
        CareRecipientDetailResponse detailResponse = toDetailResponse(membership.getRecipient(), membership.getAccessLevel());
        return APIResponse.success("Lấy hồ sơ người bệnh thành công", detailResponse);
    }

    @Override
    @Transactional
    public APIResponse<CareRecipientResponse> update(UUID actorId, UUID id, CareRecipientUpdateRequest request) {
        recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.EDITOR);
        CareRecipient recipient = lockRecipient(id);
        if (recipient.getVersion() != request.getVersion()) {
            throw new AppException(ErrorCode.RECIPIENT_VERSION_CONFLICT);
        }
        checkCoordinates(request.getLatitude(), request.getLongitude());
        careRecipientMapper.update(request, actorId, recipient);
        careRecipientRepository.flush();
        log.info("Care recipient {} updated by user {}", id, actorId);
        CareRecipientResponse careRecipientResponse = careRecipientMapper.toResponse(recipient);
        return APIResponse.success("Cập nhật hồ sơ người bệnh thành công", careRecipientResponse);
    }

    @Override
    @Transactional
    public APIResponse<CareRecipientResponse> updateStatus(UUID actorId, UUID id, RecipientStatus status) {
        if (status != RecipientStatus.ACTIVE && status != RecipientStatus.INACTIVE) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }
        recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.OWNER);
        CareRecipient recipient = lockRecipient(id);
        recipient.setStatus(status);
        recipient.setUpdatedBy(actorId);
        careRecipientRepository.flush();
        log.info("Care recipient {} status changed to {} by user {}", id, status, actorId);
        CareRecipientResponse careRecipientResponse = careRecipientMapper.toResponse(recipient);
        return APIResponse.success("Cập nhật trạng thái hồ sơ thành công", careRecipientResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id) {
        recipientAccessService.checkAccess(id, actorId, RecipientAccessLevel.OWNER);
        CareRecipient recipient = lockRecipient(id);
        recipient.setStatus(RecipientStatus.ARCHIVED);
        recipient.setDeletedAt(Instant.now());
        recipient.setUpdatedBy(actorId);
        log.info("Care recipient {} archived by user {}", id, actorId);
        return APIResponse.success("Đã lưu trữ hồ sơ người bệnh");
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CareRecipientSummaryResponse>> listForAdmin(RecipientStatus status, String keyword,
                                                                                UUID createdBy, boolean includeDeleted,
                                                                                int page, int size) {
        PageRequest pageRequest = toPageRequest(page, size, "createdAt");
        Page<CareRecipient> recipients = careRecipientRepository.findAll(
                CareRecipientSpecifications.forAdmin(status, keyword, createdBy, includeDeleted), pageRequest);
        List<CareRecipientSummaryResponse> summaryResponses = careRecipientMapper.toSummaryResponseList(recipients.getContent());
        PageResponse<CareRecipientSummaryResponse> pageResponse = PageResponse.of(recipients, summaryResponses);
        return APIResponse.success("Lấy danh sách hồ sơ thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CareRecipientDetailResponse> getForAdmin(UUID id) {
        CareRecipient recipient = careRecipientRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
        CareRecipientDetailResponse detailResponse = toDetailResponse(recipient, null);
        return APIResponse.success("Lấy hồ sơ người bệnh thành công", detailResponse);
    }

    // ===== Merged Caregiver Logic =====

    @Override
    public APIResponse<RecipientSnapshotResponse> getSnapshotForCaregiver(UUID actorId, UUID id) {
        if (!isAssigned(id, actorId)) {
            throw new AppException(ErrorCode.RECIPIENT_NOT_FOUND);
        }
        return getSnapshot(id);
    }

    private boolean isAssigned(UUID recipientId, UUID caregiverUserId) {
        BookingApiResponse<CaregiverAccessResponse> response = bookingClient.checkCaregiverAccess(caregiverUserId, recipientId);
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.BOOKING_UNAVAILABLE);
        }
        return response.getResult().isAllowed();
    }

    // ===== Merged Inter-Service Logic =====

    @Override
    @Transactional(readOnly = true)
    public APIResponse<RecipientSnapshotResponse> getSnapshot(UUID id) {
        CareRecipient recipient = careRecipientRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
        List<Medication> activeMedications = medicationRepository.findAllByRecipientAndActive(id, true);
        EmergencyContact primaryContact = emergencyContactRepository.findPrimary(id).orElse(null);
        RecipientSnapshotResponse snapshotResponse = careRecipientMapper.toSnapshotResponse(recipient, activeMedications,
                primaryContact, Instant.now());
        return APIResponse.success("Lấy snapshot hồ sơ thành công", snapshotResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<AccessCheckResponse> checkAccess(UUID id, UUID userId, RecipientAccessLevel minLevel) {
        Optional<RecipientAccessLevel> accessLevel = recipientAccessService.findAccessLevel(id, userId);
        boolean allowed = accessLevel.map(level -> level.covers(minLevel)).orElse(false);
        AccessCheckResponse accessCheckResponse = recipientManagerMapper.toAccessCheckResponse(id, userId, allowed,
                accessLevel.orElse(null));
        return APIResponse.success("Kiểm tra quyền thành công", accessCheckResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CareRecipientSummaryResponse>> listBatch(RecipientBatchRequest request) {
        List<CareRecipient> recipients = careRecipientRepository.findAllByIdInAndDeletedAtIsNull(
                new LinkedHashSet<>(request.getRecipientIds()));
        List<CareRecipientSummaryResponse> summaryResponses = careRecipientMapper.toSummaryResponseList(recipients);
        return APIResponse.success("Lấy danh sách hồ sơ thành công", summaryResponses);
    }

    // ===== Helpers =====

    private CareRecipientDetailResponse toDetailResponse(CareRecipient recipient, RecipientAccessLevel myAccessLevel) {
        List<Medication> medications = medicationRepository.findAllByRecipient(recipient.getId());
        List<EmergencyContact> emergencyContacts = emergencyContactRepository.findAllByRecipient(recipient.getId());
        return careRecipientMapper.toDetailResponse(recipient, myAccessLevel, medications, emergencyContacts);
    }

    private CareRecipient lockRecipient(UUID id) {
        return careRecipientRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECIPIENT_NOT_FOUND));
    }

    private PageRequest toPageRequest(int page, int size, String sortProperty) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortProperty));
    }

    private void checkCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new AppException(ErrorCode.INVALID_COORDINATES);
        }
    }

    private void checkSinglePrimaryContact(List<EmergencyContactRequest> emergencyContacts) {
        if (emergencyContacts == null) {
            return;
        }
        long primaryCount = emergencyContacts.stream()
                .filter(contact -> Boolean.TRUE.equals(contact.getPrimary()))
                .count();
        if (primaryCount > 1) {
            throw new AppException(ErrorCode.DUPLICATE_PRIMARY_CONTACT);
        }
    }

    private void checkMedicationDates(List<MedicationRequest> medications) {
        if (medications == null) {
            return;
        }
        for (MedicationRequest medication : medications) {
            if (medication.getEndDate() != null && medication.getEndDate().isBefore(medication.getStartDate())) {
                throw new AppException(ErrorCode.INVALID_MEDICATION_DATES);
            }
        }
    }
}
```

---

#### 4.2.4 Sub-Resource Controllers (Add `/api/v1` Prefix)

1. **`EmergencyContactController.java`**
   - File: `src/main/java/mss301/carerecipientservice/controller/EmergencyContactController.java`
   - Line 20:
     ```java
     @RequestMapping("/api/v1/care-recipients/{recipientId}/emergency-contacts")
     ```
2. **`MedicationController.java`**
   - File: `src/main/java/mss301/carerecipientservice/controller/MedicationController.java`
   - Line 20:
     ```java
     @RequestMapping("/api/v1/care-recipients/{recipientId}/medications")
     ```
3. **`RecipientManagerController.java`**
   - File: `src/main/java/mss301/carerecipientservice/controller/RecipientManagerController.java`
   - Line 20:
     ```java
     @RequestMapping("/api/v1/care-recipients/{recipientId}/managers")
     ```

---

#### 4.2.5 Feign Clients & Forwarding Interceptor

1. **`FeignAuthForwardingInterceptor.java` (NEW File)**
   - Target File: `src/main/java/mss301/carerecipientservice/client/FeignAuthForwardingInterceptor.java`
   ```java
   package mss301.carerecipientservice.client;

   import feign.RequestInterceptor;
   import feign.RequestTemplate;
   import jakarta.servlet.http.HttpServletRequest;
   import mss301.carerecipientservice.config.RequestIdFilter;
   import mss301.carerecipientservice.security.CookieUtils;
   import org.slf4j.MDC;
   import org.springframework.security.core.Authentication;
   import org.springframework.security.core.context.SecurityContextHolder;
   import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
   import org.springframework.web.context.request.RequestContextHolder;
   import org.springframework.web.context.request.ServletRequestAttributes;

   public class FeignAuthForwardingInterceptor implements RequestInterceptor {

       @Override
       public void apply(RequestTemplate template) {
           Authentication auth = SecurityContextHolder.getContext().getAuthentication();
           if (auth instanceof JwtAuthenticationToken jwtAuth) {
               template.header("Authorization", "Bearer " + jwtAuth.getToken().getTokenValue());
           } else {
               ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
               if (attrs != null) {
                   HttpServletRequest req = attrs.getRequest();
                   String authHeader = req.getHeader("Authorization");
                   if (authHeader != null && !authHeader.isBlank()) {
                       template.header("Authorization", authHeader);
                   } else {
                       String cookieToken = CookieUtils.read(req, CookieUtils.ACCESS);
                       if (cookieToken != null && !cookieToken.isBlank()) {
                           template.header("Authorization", "Bearer " + cookieToken);
                       }
                   }
               }
           }

           String requestId = MDC.get(RequestIdFilter.MDC_KEY);
           if (requestId != null && !requestId.isBlank()) {
               template.header(RequestIdFilter.HEADER, requestId);
           }
       }
   }
   ```

2. **`BookingClient.java`**
   - File: `src/main/java/mss301/carerecipientservice/client/BookingClient.java`
   - Line 20: Change `@GetMapping("/internal/bookings/caregiver-access")` to `@GetMapping("/api/v1/bookings/caregiver-access")`.

3. **`BookingClientConfig.java`**
   - File: `src/main/java/mss301/carerecipientservice/client/BookingClientConfig.java`
   - Rewrite:
   ```java
   package mss301.carerecipientservice.client;

   import feign.RequestInterceptor;
   import feign.codec.ErrorDecoder;
   import org.springframework.context.annotation.Bean;

   public class BookingClientConfig {

       @Bean
       public RequestInterceptor bookingRequestInterceptor() {
           return new FeignAuthForwardingInterceptor();
       }

       @Bean
       public ErrorDecoder bookingErrorDecoder() {
           return new BookingErrorDecoder();
       }
   }
   ```

4. **`IdentityClient.java`**
   - File: `src/main/java/mss301/carerecipientservice/client/IdentityClient.java`
   - Change line 25: `@GetMapping("/api/v1/users/{userId}")`
   - Change line 28: `@GetMapping("/api/v1/users/by-email")`
   - Change line 31: `@PostMapping("/api/v1/users/batch")`

5. **`IdentityClientConfig.java`**
   - File: `src/main/java/mss301/carerecipientservice/client/IdentityClientConfig.java`
   - Rewrite:
   ```java
   package mss301.carerecipientservice.client;

   import feign.RequestInterceptor;
   import feign.codec.ErrorDecoder;
   import org.springframework.context.annotation.Bean;

   public class IdentityClientConfig {

       @Bean
       public RequestInterceptor identityRequestInterceptor() {
           return new FeignAuthForwardingInterceptor();
       }

       @Bean
       public ErrorDecoder identityErrorDecoder() {
           return new IdentityErrorDecoder();
       }
   }
   ```

---

#### 4.2.6 Configuration Cleanups

1. **`CareRecipientProperties.java`**
   - Target File: `src/main/java/mss301/carerecipientservice/config/CareRecipientProperties.java`
   - Remove lines 18–20:
     ```java
     // DELETE:
     @ToString.Exclude
     String internalSecret = "";
     ```
   - In static inner class `Identity`: remove lines 53–55:
     ```java
     // DELETE:
     @ToString.Exclude
     String internalSecret = "";
     ```
   - In static inner class `Booking`: remove lines 65–67:
     ```java
     // DELETE:
     @ToString.Exclude
     String internalSecret = "";
     ```

2. **`application.yaml`**
   - Target File: `src/main/resources/application.yaml`
   - Line 77: Delete `internal-secret: ${CARE_RECIPIENT_INTERNAL_SECRET:}`
   - Line 86: Delete `internal-secret: ${CARE_RECIPIENT_IDENTITY_INTERNAL_SECRET:}`
   - Line 91: Delete `internal-secret: ${CARE_RECIPIENT_BOOKING_INTERNAL_SECRET:}`

3. **`OpenApiConfig.java`**
   - Target File: `src/main/java/mss301/carerecipientservice/config/swagger/OpenApiConfig.java`
   - Line 14: Delete `import mss301.carerecipientservice.security.InternalSecretFilter;`
   - Lines 29–30: Delete constants `INTERNAL_SCHEME` and `INTERNAL_PREFIX`.
   - Lines 55–59: Delete `.addSecuritySchemes(INTERNAL_SCHEME, ...)`.
   - Lines 89–94: Replace `securityRequirements()` method with:
     ```java
     @Bean
     public OpenApiCustomizer securityRequirements() {
         return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
             pathItem.readOperationsMap().forEach((method, operation) -> {
                 if (WRITE_METHODS.contains(method)) {
                     addCsrfRequirement(operation);
                 }
             });
         });
     }
     ```

---

## 5. Verification Method

To independently verify the implementation after code changes:

1. **Files Deletion Verification**:
   Verify the following 9 files are deleted and do not exist:
   ```powershell
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\AdminCareRecipientController.java
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\CaregiverCareRecipientController.java
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\controller\InternalCareRecipientController.java
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\CaregiverRecipientService.java
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\Impl\CaregiverRecipientServiceImpl.java
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\CaregiverAssignmentService.java
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\Impl\CaregiverAssignmentServiceImpl.java
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\InternalCareRecipientService.java
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\service\Impl\InternalCareRecipientServiceImpl.java
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\security\InternalSecurityConfig.java
   Test-Path d:\FA26\MSS\CareConnect\BE\CareRecipientService\src\main\java\mss301\carerecipientservice\security\InternalSecretFilter.java
   ```
   All must return `False`.

2. **Clean Compilation**:
   ```powershell
   cd d:\FA26\MSS\CareConnect\BE\CareRecipientService
   .\mvnw.cmd clean compile
   ```
   Must exit with code 0 (`BUILD SUCCESS`).

3. **Test Suite Verification**:
   ```powershell
   cd d:\FA26\MSS\CareConnect\BE\CareRecipientService
   .\mvnw.cmd test
   ```
   Must execute all unit and integration tests cleanly with 0 failures and 0 errors.

4. **Invalidation Conditions**:
   - Any remaining reference to `InternalSecretFilter` causing compile error.
   - Any 404 or 403 error on `/api/v1/care-recipients/**` endpoints due to mismatched `@PreAuthorize` or missing `/api/v1` prefix.
   - Any MapStruct compile error (`ReportingPolicy.ERROR`) due to unmapped properties.
