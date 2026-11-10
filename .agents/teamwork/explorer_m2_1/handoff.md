# Handoff Report — Explorer M2.1: ProviderService Controller Unification Blueprint

## 1. Observation

### 1.1. Current Controller Inventory (11 Controllers)
All 11 existing controller files reside in:
`d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\controller\`

#### 1. `AdminCareCenterController.java` (91 lines)
- Annotations: `@RestController`, `@RequestMapping("/admin/centers")`, `@PreAuthorize("hasRole('ADMIN')")`, `@RequiredArgsConstructor`, `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)`
- Injected: `CareCenterService careCenterService`, `IdentityAccountService identityAccountService`
- Endpoints (Lines 43–89):
  - `POST /admin/centers` -> `careCenterService.create(actorId, request)` (validates manager role via `identityAccountService.requireActiveRole`)
  - `GET /admin/centers` -> `careCenterService.list(status, keyword, includeDeleted, page, size)`
  - `GET /admin/centers/{id}` -> `careCenterService.get(id)`
  - `PUT /admin/centers/{id}` -> `careCenterService.update(actorId, id, request)`
  - `PATCH /admin/centers/{id}/status` -> `careCenterService.updateStatus(actorId, id, request)`
  - `DELETE /admin/centers/{id}` -> `careCenterService.delete(actorId, id, version)`
- Defect: Role-segregated class (`/admin/centers`), missing `/api/v1` prefix.

#### 2. `CenterScheduleController.java` (39 lines)
- Annotations: `@RestController`, `@RequiredArgsConstructor`, `@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")`
- Injected: `CaregiverScheduleService scheduleService`
- Endpoints (Lines 23–37):
  - `GET /centers/{centerId}/schedules` -> `scheduleService.centerSchedules(actorId, centerId, page, size)`
  - `PATCH /schedules/{id}/status` -> `scheduleService.updateStatus(actorId, id, request)`
- Defect: Fragmented paths (`/centers/{centerId}/schedules`, `/schedules/{id}/status`), missing class-level mapping and `/api/v1` prefix.

#### 3. `CaregiverManagementController.java` (64 lines)
- Annotations: `@RestController`, `@RequiredArgsConstructor`, `@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")`
- Injected: `CaregiverService caregiverService`, `IdentityAccountService identityAccountService`
- Endpoints (Lines 26–62):
  - `POST /centers/{centerId}/caregivers` -> `caregiverService.create(actorId, centerId, request)` (validates role `CAREGIVER`)
  - `GET /centers/{centerId}/caregivers` -> `caregiverService.listManaged(actorId, centerId, verification, status, page, size)`
  - `GET /caregivers/{caregiverId}` -> `caregiverService.get(actorId, caregiverId)`
  - `PUT /caregivers/{caregiverId}` -> `caregiverService.update(actorId, caregiverId, request)`
  - `DELETE /caregivers/{caregiverId}` -> `caregiverService.archive(actorId, caregiverId, version)`
- Defect: Missing class-level mapping and `/api/v1` prefix.

#### 4. `CaregiverWorkController.java` (70 lines)
- Annotations: `@RestController`, `@RequiredArgsConstructor`, `@PreAuthorize("hasRole('CAREGIVER')")`
- Injected: `ShiftRegistrationService registrationService`, `CaregiverScheduleService scheduleService`, `CaregiverService caregiverService`
- Endpoints (Lines 25–68):
  - `GET /caregiver/profile` -> `caregiverService.myProfile(actorId)`
  - `POST /caregiver/shifts/{shiftId}/registrations` -> `registrationService.register(actorId, shiftId, request)`
  - `GET /caregiver/registrations` -> `registrationService.myRegistrations(actorId, page, size)`
  - `DELETE /caregiver/registrations/{id}` -> `registrationService.cancelOwn(actorId, id, version)`
  - `GET /caregiver/schedules` -> `scheduleService.mySchedules(actorId, page, size)`
  - `PATCH /caregiver/schedules/{id}/status` -> `scheduleService.updateStatus(actorId, id, request)`
- Defect: Role-segregated class (`/caregiver/...`), mixed concerns (caregiver profile + shift registration + work schedules), missing `/api/v1` prefix.

#### 5. `AdminVerificationController.java` (39 lines)
- Annotations: `@RestController`, `@RequestMapping("/admin")`, `@PreAuthorize("hasRole('ADMIN')")`, `@RequiredArgsConstructor`
- Injected: `CaregiverService caregiverService`, `CertificateService certificateService`
- Endpoints (Lines 25–37):
  - `PATCH /admin/caregivers/{id}/verification` -> `caregiverService.verify(actorId, id, request)`
  - `PATCH /admin/certificates/{id}/verification` -> `certificateService.verify(actorId, id, request)`
- Defect: Role-segregated class (`/admin`), crosses domain aggregates (caregivers and certificates), missing `/api/v1` prefix.

#### 6. `CertificateController.java` (51 lines)
- Annotations: `@RestController`, `@RequiredArgsConstructor`, `@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")`
- Injected: `CertificateService certificateService`
- Endpoints (Lines 23–49):
  - `POST /caregivers/{caregiverId}/certificates` -> `certificateService.create(actorId, caregiverId, request)`
  - `GET /caregivers/{caregiverId}/certificates` -> `certificateService.list(actorId, caregiverId)`
  - `PUT /certificates/{id}` -> `certificateService.update(actorId, id, request)`
  - `DELETE /certificates/{id}` -> `certificateService.delete(actorId, id, version)`
- Defect: Missing class-level mapping and `/api/v1` prefix.

#### 7. `WorkShiftController.java` (82 lines)
- Annotations: `@RestController`, `@RequiredArgsConstructor`
- Injected: `WorkShiftService shiftService`, `ShiftRegistrationService registrationService`
- Endpoints (Lines 25–80):
  - `POST /centers/{centerId}/shifts` (`hasAnyRole('ADMIN','CENTER_MANAGER')`) -> `shiftService.create(actorId, centerId, request)`
  - `GET /shifts` (`hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER','CUSTOMER')`) -> `shiftService.list(centerId, status, fromDate, toDate, page, size)`
  - `GET /shifts/{id}` (`isAuthenticated()`) -> `shiftService.get(id)`
  - `PUT /shifts/{id}` (`hasAnyRole('ADMIN','CENTER_MANAGER')`) -> `shiftService.update(actorId, id, request)`
  - `DELETE /shifts/{id}` (`hasAnyRole('ADMIN','CENTER_MANAGER')`) -> `shiftService.delete(actorId, id, version)`
  - `GET /shifts/{id}/registrations` (`hasAnyRole('ADMIN','CENTER_MANAGER')`) -> `registrationService.listForShift(actorId, id, page, size)`
  - `PATCH /registrations/{id}/review` (`hasAnyRole('ADMIN','CENTER_MANAGER')`) -> `registrationService.review(actorId, id, request)`
- Defect: Missing class-level mapping, missing `/api/v1` prefix, missing caregiver self-registration endpoints (which were isolated in `CaregiverWorkController`).

#### 8. `ProviderCatalogController.java` (36 lines)
- Annotations: `@RestController`, `@RequestMapping("/catalog/caregivers")`, `@PreAuthorize("isAuthenticated()")`, `@RequiredArgsConstructor`
- Injected: `CaregiverService caregiverService`, `CertificateService certificateService`
- Endpoints (Lines 22–34):
  - `GET /catalog/caregivers` -> `caregiverService.catalog(centerId, keyword, page, size)`
  - `GET /catalog/caregivers/{caregiverId}/certificates` -> `certificateService.verifiedList(caregiverId)`
- Defect: Separate catalog controller crossing caregiver and certificate domains, missing `/api/v1` prefix.

#### 9. `ProviderMediaController.java` (51 lines)
- Annotations: `@RestController`, `@RequestMapping("/provider-media")`, `@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")`, `@RequiredArgsConstructor`, `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)`
- Injected: `ProviderMediaService providerMediaService`
- Endpoints (Lines 30–49):
  - `POST /provider-media/{ownerType}/{ownerId}` -> `providerMediaService.upload(actorId, ownerType, ownerId, files)`
  - `GET /provider-media/{ownerType}/{ownerId}` -> `providerMediaService.list(actorId, ownerType, ownerId)`
  - `DELETE /provider-media/{ownerType}/{ownerId}/{mediaFileId}` -> `providerMediaService.delete(actorId, ownerType, ownerId, mediaFileId)`
- Defect: Missing `/api/v1` prefix.

#### 10. `InternalCaregiverController.java` (32 lines)
- Annotations: `@RestController`, `@RequestMapping("/internal/caregivers")`, `@RequiredArgsConstructor`, `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)`
- Injected: `InternalProviderService internalProviderService`
- Endpoints (Lines 22–30):
  - `GET /internal/caregivers/{caregiverId}` -> `internalProviderService.getCaregiver(caregiverId)`
  - `GET /internal/caregivers/by-user/{userId}` -> `internalProviderService.getCaregiverByUser(userId)`
- Defect: Backdoor `/internal/**` endpoint protected by `X-Internal-Secret` (violates R4).

#### 11. `InternalCenterController.java` (27 lines)
- Annotations: `@RestController`, `@RequestMapping("/internal/centers")`, `@RequiredArgsConstructor`, `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)`
- Injected: `InternalProviderService internalProviderService`
- Endpoints (Lines 22–25):
  - `GET /internal/centers/{centerId}` -> `internalProviderService.getCenter(centerId)`
- Defect: Backdoor `/internal/**` endpoint protected by `X-Internal-Secret` (violates R4).

---

### 1.2. Internal Security & Supporting Artifacts
- `InternalSecurityConfig.java` (51 lines): Intercepts `/internal/**` at `@Order(1)` using `InternalSecretFilter`. Must be deleted per R4.
- `InternalSecretFilter.java` (58 lines): Validates header `X-Internal-Secret`. Must be deleted per R4.
- `InternalProviderService.java` & `InternalProviderServiceImpl.java`: Encapsulates internal query methods. Must be deleted per R4.
- `InternalProviderMapper.java`: Maps entity to `InternalCaregiverResponse` and `InternalCenterResponse`. Must be deleted per R4.
- `InternalProviderApiTests.java`: Integration tests for `/internal/**` endpoints. Must be deleted per R4.

---

## 2. Logic Chain

```
Observation: 11 controllers exist in ProviderService with fragmented paths, role-segregated classes (/admin/..., /caregiver/...), non-versioned paths (0 endpoints have /api/v1), and internal backdoors (/internal/...).
Logic Step 1 (Domain Aggregate Unification): Group all endpoints strictly by entity/aggregate boundary:
  - CareCenter: AdminCareCenterController + CenterScheduleController -> CareCenterController (@RequestMapping("/api/v1/care-centers"))
  - Caregiver: CaregiverManagementController + CaregiverWorkController (caregiver & schedule endpoints) + AdminVerificationController (caregiver portion) + ProviderCatalogController (caregiver portion) -> CaregiverController (@RequestMapping("/api/v1/caregivers"))
  - WorkShift: WorkShiftController + CaregiverWorkController (shift registration endpoints) -> WorkShiftController (@RequestMapping("/api/v1/work-shifts"))
  - Certificate: CertificateController + AdminVerificationController (certificate portion) + ProviderCatalogController (certificate portion) -> CertificateController (@RequestMapping("/api/v1/certificates"))
  - Media: ProviderMediaController -> ProviderMediaController (@RequestMapping("/api/v1/provider-media"))
```

```
Observation: InternalCaregiverController and InternalCenterController provide 3 lookups (get caregiver by ID, get caregiver by user ID, get center by ID) used by BookingService.
Logic Step 2 (Inter-Service Exposure):
  - Expose GET /api/v1/care-centers/{centerId} on CareCenterController with @PreAuthorize("isAuthenticated()").
  - Expose GET /api/v1/caregivers/{caregiverId} on CaregiverController with @PreAuthorize("isAuthenticated()").
  - Expose GET /api/v1/caregivers/by-user/{userId} on CaregiverController with @PreAuthorize("isAuthenticated()").
  - Add getCaregiverId() / centerId getters on response DTOs for 100% backward/forward JSON compatibility with BookingService ProviderClient.
```

```
Observation: Currently, CaregiverService.get(actorId, caregiverId) strictly restricts lookup to the caregiver themselves or their center manager (requireCaregiverSelfOrCenterManagement). However, customers and BookingService require caregiver details to browse and book.
Logic Step 3 (Public Caregiver Lookup):
  - In CaregiverService, provide an overloaded get(UUID caregiverId) and getByUser(UUID userId) that retrieves the active profile and returns CaregiverResponse without actor-restriction.
  - Retain the managing method or use this public lookup for GET /api/v1/caregivers/{caregiverId} and GET /api/v1/caregivers/by-user/{userId}.
```

```
Observation: Shift registrations are created by caregivers (POST /caregiver/shifts/{shiftId}/registrations), listed by caregivers (GET /caregiver/registrations), cancelled by caregivers (DELETE /caregiver/registrations/{id}), listed by managers (GET /shifts/{id}/registrations), and reviewed by managers (PATCH /registrations/{id}/review).
Logic Step 4 (WorkShift Aggregate Consolidation):
  - ShiftRegistration is a child entity of WorkShift.
  - Consolidating all 5 ShiftRegistration endpoints into WorkShiftController under /api/v1/work-shifts/** provides a single, cohesive lifecycle controller.
```

```
Observation: SecurityConfig already has @EnableMethodSecurity and requires authentication on all non-public endpoints.
Logic Step 5 (Security Normalization):
  - Place method-level @PreAuthorize on every method with exact roles: hasRole('ADMIN'), hasAnyRole('ADMIN','CENTER_MANAGER'), hasRole('CAREGIVER'), hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER'), or isAuthenticated().
```

---

## 3. Caveats

1. **DTO Field Compatibility (`id` vs `caregiverId` / `centerId`)**:
   - BookingService's `ProviderCenterResponse` expects `centerId` (as well as `centerName`, `centerStatus`, `managerUserId`).
   - `CenterResponse` in ProviderService defines `UUID id`.
   - **Mitigation**: Add `@JsonProperty("centerId")` or getter `public UUID getCenterId() { return id; }` to `CenterResponse` so the JSON payload includes both keys.
   - Similarly, in `CaregiverResponse`, add `public UUID getCaregiverId() { return id; }` and ensure `centerStatus` is populated.
2. **ApiGateway Routing Configuration**:
   - In Milestone 4, `ApiGateway` will route `/api/v1/care-centers/**`, `/api/v1/caregivers/**`, `/api/v1/work-shifts/**`, `/api/v1/certificates/**`, and `/api/v1/provider-media/**` to ProviderService on port 8084.
   - Until Gateway `stripPrefix` is removed in M4, direct calls to ProviderService on port 8084 will use `/api/v1/**`.
3. **Internal Test Deletion**:
   - `InternalProviderApiTests.java` tests `/internal/**` with `X-Internal-Secret`. When `/internal/**` is removed, this test file must be deleted or replaced with tests verifying the new `/api/v1/**` public lookups.

---

## 4. Conclusion & Actionable Blueprint

### 4.1. Controller Unification Architecture Summary

| Unified Controller | Request Mapping | Merged From (Old Controllers) | Endpoint Count |
|---|---|---|---|
| **`CareCenterController`** | `/api/v1/care-centers` | `AdminCareCenterController`, `CenterScheduleController`, `InternalCenterController` | 8 |
| **`CaregiverController`** | `/api/v1/caregivers` | `CaregiverManagementController`, `CaregiverWorkController` (caregiver & schedule), `AdminVerificationController` (caregiver), `ProviderCatalogController` (caregiver), `InternalCaregiverController` | 11 |
| **`WorkShiftController`** | `/api/v1/work-shifts` | `WorkShiftController`, `CaregiverWorkController` (registrations) | 10 |
| **`CertificateController`** | `/api/v1/certificates` | `CertificateController`, `AdminVerificationController` (certificates), `ProviderCatalogController` (certificates) | 6 |
| **`ProviderMediaController`** | `/api/v1/provider-media` | `ProviderMediaController` (prefixed with `/api/v1`) | 3 |
| **TOTAL** | — | **11 Old Controllers -> 5 Unified Controllers** | **38** |

---

### 4.2. Detailed Line-by-Line Controller Blueprint

#### 1. `CareCenterController.java`
**Target File**: `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\controller\CareCenterController.java`
**Package**: `mss301.providerservice.controller`

```java
package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CareCenterService;
import mss301.providerservice.service.CaregiverScheduleService;
import mss301.providerservice.service.IdentityAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-centers")
@Tag(name = "Care Centers", description = "Quản lý và tra cứu trung tâm chăm sóc")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CareCenterController {

    CareCenterService careCenterService;
    IdentityAccountService identityAccountService;
    CaregiverScheduleService scheduleService;

    @PostMapping
    @Operation(summary = "Tạo trung tâm chăm sóc mới (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> create(@CurrentUserId UUID actorId,
                                              @RequestBody @Valid CenterCreateRequest request) {
        if (request.getManagerUserId() != null) {
            identityAccountService.requireActiveRole(request.getManagerUserId(), "CENTER_MANAGER");
        }
        return careCenterService.create(actorId, request);
    }

    @GetMapping
    @Operation(summary = "Danh sách trung tâm chăm sóc")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<CenterResponse>> list(
            @RequestParam(required = false) CenterStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return careCenterService.list(status, keyword, includeDeleted, page, size);
    }

    @GetMapping("/{centerId}")
    @Operation(summary = "Chi tiết trung tâm chăm sóc (public / inter-service)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CenterResponse> get(@PathVariable UUID centerId) {
        return careCenterService.get(centerId);
    }

    @PutMapping("/{centerId}")
    @Operation(summary = "Cập nhật thông tin trung tâm chăm sóc (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> update(@CurrentUserId UUID actorId,
                                              @PathVariable UUID centerId,
                                              @RequestBody @Valid CenterUpdateRequest request) {
        if (request.getManagerUserId() != null) {
            identityAccountService.requireActiveRole(request.getManagerUserId(), "CENTER_MANAGER");
        }
        return careCenterService.update(actorId, centerId, request);
    }

    @PatchMapping("/{centerId}/status")
    @Operation(summary = "Cập nhật trạng thái trung tâm chăm sóc (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CenterResponse> updateStatus(@CurrentUserId UUID actorId,
                                                    @PathVariable UUID centerId,
                                                    @RequestBody @Valid CenterStatusUpdateRequest request) {
        return careCenterService.updateStatus(actorId, centerId, request);
    }

    @DeleteMapping("/{centerId}")
    @Operation(summary = "Xóa mềm trung tâm chăm sóc (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID centerId,
                                    @RequestParam Long version) {
        return careCenterService.delete(actorId, centerId, version);
    }

    @GetMapping("/{centerId}/schedules")
    @Operation(summary = "Danh sách lịch người chăm sóc của trung tâm")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<ScheduleResponse>> listSchedules(
            @CurrentUserId UUID actorId,
            @PathVariable UUID centerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scheduleService.centerSchedules(actorId, centerId, page, size);
    }

    @PatchMapping("/schedules/{id}/status")
    @Operation(summary = "Cập nhật hoặc hủy lịch người chăm sóc từ phía trung tâm")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<ScheduleResponse> updateScheduleStatus(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleStatusRequest request) {
        return scheduleService.updateStatus(actorId, id, request);
    }
}
```

---

#### 2. `CaregiverController.java`
**Target File**: `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\controller\CaregiverController.java`
**Package**: `mss301.providerservice.controller`

```java
package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.CaregiverUpdateRequest;
import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CaregiverScheduleService;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.IdentityAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/caregivers")
@Tag(name = "Caregivers", description = "Quản lý và tra cứu hồ sơ người chăm sóc")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CaregiverController {

    CaregiverService caregiverService;
    IdentityAccountService identityAccountService;
    CaregiverScheduleService scheduleService;

    @PostMapping("/centers/{centerId}")
    @Operation(summary = "Tạo hồ sơ caregiver thuộc trung tâm")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<CaregiverResponse> create(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID centerId,
                                                 @Valid @RequestBody CaregiverCreateRequest request) {
        identityAccountService.requireActiveRole(request.getUserId(), "CAREGIVER");
        return caregiverService.create(actorId, centerId, request);
    }

    @GetMapping("/centers/{centerId}")
    @Operation(summary = "Danh sách caregiver do trung tâm quản lý")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<CaregiverResponse>> listManaged(
            @CurrentUserId UUID actorId,
            @PathVariable UUID centerId,
            @RequestParam(required = false) VerificationStatus verification,
            @RequestParam(required = false) CaregiverStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caregiverService.listManaged(actorId, centerId, verification, status, page, size);
    }

    @GetMapping
    @Operation(summary = "Tìm kiếm caregiver đang hoạt động (Catalog)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<PageResponse<CaregiverResponse>> catalog(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caregiverService.catalog(centerId, keyword, page, size);
    }

    @GetMapping("/me")
    @Operation(summary = "Hồ sơ caregiver của tôi")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CaregiverResponse> myProfile(@CurrentUserId UUID actorId) {
        return caregiverService.myProfile(actorId);
    }

    @GetMapping("/{caregiverId}")
    @Operation(summary = "Chi tiết hồ sơ caregiver (public / inter-service)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CaregiverResponse> get(@PathVariable UUID caregiverId) {
        return caregiverService.get(caregiverId);
    }

    @GetMapping("/by-user/{userId}")
    @Operation(summary = "Tra cứu hồ sơ caregiver theo userId tài khoản Identity (public / inter-service)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CaregiverResponse> getByUser(@PathVariable UUID userId) {
        return caregiverService.getByUser(userId);
    }

    @PutMapping("/{caregiverId}")
    @Operation(summary = "Cập nhật hồ sơ caregiver")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<CaregiverResponse> update(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID caregiverId,
                                                 @Valid @RequestBody CaregiverUpdateRequest request) {
        return caregiverService.update(actorId, caregiverId, request);
    }

    @DeleteMapping("/{caregiverId}")
    @Operation(summary = "Lưu trữ hồ sơ caregiver (xóa mềm)")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<Void> archive(@CurrentUserId UUID actorId,
                                     @PathVariable UUID caregiverId,
                                     @RequestParam Long version) {
        return caregiverService.archive(actorId, caregiverId, version);
    }

    @PatchMapping("/{id}/verification")
    @Operation(summary = "Duyệt hoặc từ chối hồ sơ caregiver (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CaregiverResponse> verify(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody VerificationRequest request) {
        return caregiverService.verify(actorId, id, request);
    }

    @GetMapping("/me/schedules")
    @Operation(summary = "Lịch làm việc của tôi (Caregiver)")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<PageResponse<ScheduleResponse>> mySchedules(
            @CurrentUserId UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scheduleService.mySchedules(actorId, page, size);
    }

    @PatchMapping("/schedules/{id}/status")
    @Operation(summary = "Bắt đầu hoặc hoàn thành lịch làm việc (Caregiver / Manager / Admin)")
    @PreAuthorize("hasAnyRole('CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<ScheduleResponse> updateScheduleStatus(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleStatusRequest request) {
        return scheduleService.updateStatus(actorId, id, request);
    }
}
```

---

#### 3. `WorkShiftController.java`
**Target File**: `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\controller\WorkShiftController.java`
**Package**: `mss301.providerservice.controller`

```java
package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.ShiftRegistrationService;
import mss301.providerservice.service.WorkShiftService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-shifts")
@Tag(name = "Work Shifts", description = "Quản lý ca làm việc và đăng ký ca")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WorkShiftController {

    WorkShiftService shiftService;
    ShiftRegistrationService registrationService;

    @PostMapping("/centers/{centerId}")
    @Operation(summary = "Tạo ca làm việc cho trung tâm")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftResponse> create(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID centerId,
                                                 @Valid @RequestBody WorkShiftRequest request) {
        return shiftService.create(actorId, centerId, request);
    }

    @GetMapping
    @Operation(summary = "Tìm ca làm việc (lọc theo trung tâm, trạng thái và khoảng ngày)")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER','CUSTOMER')")
    public APIResponse<PageResponse<WorkShiftResponse>> list(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return shiftService.list(centerId, status, fromDate, toDate, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết ca làm việc")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<WorkShiftResponse> get(@PathVariable UUID id) {
        return shiftService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật ca làm việc")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftResponse> update(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody WorkShiftRequest request) {
        return shiftService.update(actorId, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Hủy và xóa mềm ca làm việc")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID id,
                                    @RequestParam Long version) {
        return shiftService.delete(actorId, id, version);
    }

    @PostMapping("/{shiftId}/registrations")
    @Operation(summary = "Caregiver đăng ký ca làm việc")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<ShiftRegistrationResponse> register(
            @CurrentUserId UUID actorId,
            @PathVariable UUID shiftId,
            @Valid @RequestBody ShiftRegistrationRequest request) {
        return registrationService.register(actorId, shiftId, request);
    }

    @GetMapping("/registrations/me")
    @Operation(summary = "Danh sách đăng ký ca của tôi (Caregiver)")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<PageResponse<ShiftRegistrationResponse>> myRegistrations(
            @CurrentUserId UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return registrationService.myRegistrations(actorId, page, size);
    }

    @DeleteMapping("/registrations/{id}")
    @Operation(summary = "Hủy đăng ký ca đang chờ duyệt (Caregiver)")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<Void> cancelRegistration(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @RequestParam Long version) {
        return registrationService.cancelOwn(actorId, id, version);
    }

    @GetMapping("/{id}/registrations")
    @Operation(summary = "Danh sách caregiver đăng ký ca (Center Manager / Admin)")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listRegistrationsForShift(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return registrationService.listForShift(actorId, id, page, size);
    }

    @PatchMapping("/registrations/{id}/review")
    @Operation(summary = "Duyệt hoặc từ chối đăng ký ca (Center Manager / Admin)")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<ShiftRegistrationResponse> reviewRegistration(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationReviewRequest request) {
        return registrationService.review(actorId, id, request);
    }
}
```

---

#### 4. `CertificateController.java`
**Target File**: `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\controller\CertificateController.java`
**Package**: `mss301.providerservice.controller`

```java
package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CertificateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/certificates")
@Tag(name = "Certificates", description = "Quản lý chứng chỉ caregiver")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CertificateController {

    CertificateService certificateService;

    @PostMapping("/caregivers/{caregiverId}")
    @Operation(summary = "Thêm chứng chỉ cho caregiver")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<CertificateResponse> create(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID caregiverId,
                                                   @Valid @RequestBody CertificateRequest request) {
        return certificateService.create(actorId, caregiverId, request);
    }

    @GetMapping("/caregivers/{caregiverId}")
    @Operation(summary = "Danh sách chứng chỉ của caregiver")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<List<CertificateResponse>> list(@CurrentUserId UUID actorId,
                                                       @PathVariable UUID caregiverId) {
        return certificateService.list(actorId, caregiverId);
    }

    @GetMapping("/caregivers/{caregiverId}/verified")
    @Operation(summary = "Xem danh sách chứng chỉ đã xác minh của caregiver (Catalog public)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<CertificateResponse>> verifiedList(@PathVariable UUID caregiverId) {
        return certificateService.verifiedList(caregiverId);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật chứng chỉ")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<CertificateResponse> update(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody CertificateRequest request) {
        return certificateService.update(actorId, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa mềm chứng chỉ")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID id,
                                    @RequestParam Long version) {
        return certificateService.delete(actorId, id, version);
    }

    @PatchMapping("/{id}/verification")
    @Operation(summary = "Duyệt hoặc từ chối chứng chỉ (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CertificateResponse> verify(@CurrentUserId UUID actorId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody VerificationRequest request) {
        return certificateService.verify(actorId, id, request);
    }
}
```

---

#### 5. `ProviderMediaController.java`
**Target File**: `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\controller\ProviderMediaController.java`
**Package**: `mss301.providerservice.controller`

```java
package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.ProviderMediaResponse;
import mss301.providerservice.enums.MediaOwnerType;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.ProviderMediaService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/provider-media")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
@Tag(name = "Provider Media", description = "File của center, caregiver profile và certificate")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProviderMediaController {

    ProviderMediaService providerMediaService;

    @PostMapping(value = "/{ownerType}/{ownerId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload nhiều file cho một owner")
    public APIResponse<List<ProviderMediaResponse>> upload(
            @CurrentUserId UUID actorId,
            @PathVariable MediaOwnerType ownerType,
            @PathVariable UUID ownerId,
            @RequestPart("files") List<MultipartFile> files) {
        return providerMediaService.upload(actorId, ownerType, ownerId, files);
    }

    @GetMapping("/{ownerType}/{ownerId}")
    @Operation(summary = "Xem các file và URL tải tạm thời")
    public APIResponse<List<ProviderMediaResponse>> list(
            @CurrentUserId UUID actorId,
            @PathVariable MediaOwnerType ownerType,
            @PathVariable UUID ownerId) {
        return providerMediaService.list(actorId, ownerType, ownerId);
    }

    @DeleteMapping("/{ownerType}/{ownerId}/{mediaFileId}")
    @Operation(summary = "Xóa một file khỏi owner và khỏi R2")
    public APIResponse<Void> delete(
            @CurrentUserId UUID actorId,
            @PathVariable MediaOwnerType ownerType,
            @PathVariable UUID ownerId,
            @PathVariable UUID mediaFileId) {
        return providerMediaService.delete(actorId, ownerType, ownerId, mediaFileId);
    }
}
```

---

### 4.3. Files to Delete

| File Path | Component | Reason |
|---|---|---|
| `mss301/providerservice/controller/AdminCareCenterController.java` | Controller | Merged into `CareCenterController` |
| `mss301/providerservice/controller/CenterScheduleController.java` | Controller | Merged into `CareCenterController` |
| `mss301/providerservice/controller/CaregiverManagementController.java` | Controller | Merged into `CaregiverController` |
| `mss301/providerservice/controller/CaregiverWorkController.java` | Controller | Merged into `CaregiverController` & `WorkShiftController` |
| `mss301/providerservice/controller/AdminVerificationController.java` | Controller | Merged into `CaregiverController` & `CertificateController` |
| `mss301/providerservice/controller/ProviderCatalogController.java` | Controller | Merged into `CaregiverController` & `CertificateController` |
| `mss301/providerservice/controller/InternalCaregiverController.java` | Controller | Deleted per R4; replaced by public endpoints on `CaregiverController` |
| `mss301/providerservice/controller/InternalCenterController.java` | Controller | Deleted per R4; replaced by public endpoint on `CareCenterController` |
| `mss301/providerservice/service/InternalProviderService.java` | Service Interface | Deleted per R4 |
| `mss301/providerservice/service/Impl/InternalProviderServiceImpl.java` | Service Class | Deleted per R4 |
| `mss301/providerservice/security/InternalSecretFilter.java` | Security Filter | Deleted per R4 |
| `mss301/providerservice/security/InternalSecurityConfig.java` | Security Config | Deleted per R4 |
| `mss301/providerservice/mapper/InternalProviderMapper.java` | Mapper | Deleted per R4 |
| `src/test/java/mss301/providerservice/InternalProviderApiTests.java` | Test | Obsolete tests for deleted `/internal/**` |

---

### 4.4. Service & DTO Compatibility Adjustments Required in M2

1. **`CaregiverService` Additions**:
   In `CaregiverService` (and `CaregiverServiceImpl`):
   ```java
   APIResponse<CaregiverResponse> get(UUID caregiverId);
   APIResponse<CaregiverResponse> getByUser(UUID userId);
   ```
   Both methods query `caregiverProfileRepository` (by ID or by user ID with `deletedAtIsNull`), ensure active status if necessary, and return `APIResponse<CaregiverResponse>` mapped via `ProviderViewMapper.caregiver(profile)`.

2. **`CenterResponse.java` Backward Compatibility**:
   Add alias getter in `CenterResponse.java`:
   ```java
   public UUID getCenterId() {
       return id;
   }
   ```
   Ensures Jackson serializes `"centerId"` alongside `"id"`, satisfying `ProviderCenterResponse` in BookingService.

3. **`CaregiverResponse.java` Backward Compatibility**:
   Add alias getter and center status in `CaregiverResponse.java`:
   ```java
   public UUID getCaregiverId() {
       return id;
   }
   CenterStatus centerStatus;
   ```
   Populate `centerStatus` in mapper:
   ```java
   if (value.getCenter() != null) {
       builder.centerStatus(value.getCenter().getCenterStatus());
   }
   ```

---

## 5. Verification Method

To verify the unified controller blueprint upon implementation:

1. **Controller File Count Verification**:
   ```powershell
   Get-ChildItem -Path d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\controller\*.java
   ```
   - Must return exactly 5 files:
     - `CareCenterController.java`
     - `CaregiverController.java`
     - `WorkShiftController.java`
     - `CertificateController.java`
     - `ProviderMediaController.java`
   - Must NOT contain any `*Admin*.java`, `*Internal*.java`, `*Work*.java`, or `*Catalog*.java`.

2. **API Versioning Verification**:
   ```powershell
   rg -n "@RequestMapping" d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\controller\
   ```
   - Expected outputs:
     - `CareCenterController.java`: `@RequestMapping("/api/v1/care-centers")`
     - `CaregiverController.java`: `@RequestMapping("/api/v1/caregivers")`
     - `WorkShiftController.java`: `@RequestMapping("/api/v1/work-shifts")`
     - `CertificateController.java`: `@RequestMapping("/api/v1/certificates")`
     - `ProviderMediaController.java`: `@RequestMapping("/api/v1/provider-media")`
   - 100% of controller classes must declare `@RequestMapping("/api/v1/...")`.

3. **Internal Artifact Deletion Verification**:
   ```powershell
   rg -n "InternalSecretFilter|InternalSecurityConfig|X-Internal-Secret|/internal/" d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\
   ```
   - Expected output: 0 matches.

4. **Compilation Verification**:
   ```powershell
   cd d:\FA26\MSS\CareConnect\BE\ProviderService
   .\mvnw.cmd clean compile
   ```
   - Expected output: `BUILD SUCCESS` with zero compilation errors.
