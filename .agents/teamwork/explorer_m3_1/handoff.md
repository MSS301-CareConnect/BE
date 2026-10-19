# Handoff Report — Explorer M3.1: BookingService Refactoring Blueprint

## 1. Observation

A forensic investigation of `BookingService` (`d:\FA26\MSS\CareConnect\BE\BookingService`) was performed against requirements R1, R2, R3, R4, and R5. The following current state was directly observed:

### 1.1. Controller Layer (`mss301.bookingservice.controller`)
- **`BookingController.java`** (`src/main/java/mss301/bookingservice/controller/BookingController.java:30`):
  Lacks a class-level `@RequestMapping` annotation. Endpoints are fragmented with role prefixes hardcoded per method:
  * Customer endpoints (lines 35-71): `@PostMapping("/bookings")`, `@GetMapping("/bookings")`, `@GetMapping("/bookings/{id}")`, `@GetMapping("/bookings/{id}/occurrences")`, `@PostMapping("/bookings/{id}/cancel")`.
  * Center Manager endpoints (lines 75-136): `@GetMapping("/center/bookings")`, `@GetMapping("/center/bookings/{id}")`, `@PostMapping("/center/bookings/{id}/accept")`, `@PostMapping("/center/bookings/{id}/reject")`, `@PatchMapping("/center/bookings/{id}/caregiver")`, `@PatchMapping("/center/bookings/occurrences/{occurrenceId}/caregiver")`, `@PostMapping("/center/bookings/{id}/cancel")`.
  * Admin endpoints (lines 140-157): `@GetMapping("/admin/bookings")`, `@GetMapping("/admin/bookings/{id}")`.
- **`CaregiverOccurrenceController.java`** (`src/main/java/mss301/bookingservice/controller/CaregiverOccurrenceController.java:21`):
  Annotated with `@RequestMapping("/caregiver/occurrences")`. Missing `/api/v1` prefix and named specifically for the `CAREGIVER` role rather than the Domain Aggregate (`BookingOccurrence`). Endpoints:
  * Line 28: `@GetMapping` (`hasRole('CAREGIVER')`)
  * Line 37: `@GetMapping("/{id}")` (`hasRole('CAREGIVER')`)
- **`InternalBookingController.java`** (`src/main/java/mss301/bookingservice/controller/InternalBookingController.java:19`):
  Annotated with `@RequestMapping("/internal/bookings")`. Exposes 4 endpoints behind `X-Internal-Secret`:
  * Line 27: `GET /internal/bookings/caregiver-access?caregiverUserId={caregiverUserId}&recipientId={recipientId}` -> checks caregiver assignment for CareRecipientService.
  * Line 33: `POST /internal/bookings/{id}/deposit-paid` -> marks booking as paid/confirmed.
  * Line 39: `GET /internal/bookings/occurrences/{occurrenceId}` -> returns `CaregiverOccurrenceResponse`.
  * Line 44: `GET /internal/bookings/{id}` -> returns `BookingDetailResponse`.

### 1.2. Service Layer (`mss301.bookingservice.service`)
- Root `service/` directory contains 15 files, all of which are pure `interface` definitions:
  * `BookingCodeGenerator`, `BookingExpiryService`, `BookingLifecycleService`, `BookingReadService`, `BookingService`, `CareRecipientDirectoryService`, `CareServiceCatalogPort`, `CaregiverOccurrenceService`, `CaregiverPort`, `CenterPort`, `IdentityUserService`, `InternalBookingService`, `OccurrenceGenerator`, `RefundPolicy`, `ScheduleConflictService`.
- Subpackage `service/Impl/` contains 15 corresponding concrete classes ending in `ServiceImpl` or `PortImpl` or `Impl`:
  * `BookingCodeGeneratorImpl`, `BookingExpiryServiceImpl`, `BookingLifecycleServiceImpl`, `BookingReadServiceImpl`, `BookingServiceImpl`, `CareRecipientDirectoryServiceImpl`, `CareServiceCatalogPortImpl`, `CaregiverOccurrenceServiceImpl`, `CaregiverPortImpl`, `CenterPortImpl`, `IdentityUserServiceImpl`, `InternalBookingServiceImpl`, `OccurrenceGeneratorImpl`, `RefundPolicyImpl`, `ScheduleConflictServiceImpl`.
- `CaregiverOccurrenceService` and its implementation `CaregiverOccurrenceServiceImpl` need renaming to `BookingOccurrenceService` and `BookingOccurrenceServiceImpl`.
- `InternalBookingService` and `InternalBookingServiceImpl` need deletion, with `checkCaregiverAccess` and `markDepositPaid` merged into `BookingService`/`BookingServiceImpl`, and `getOccurrence` merged into `BookingOccurrenceService`/`BookingOccurrenceServiceImpl` as `getById(UUID id)`.

### 1.3. Internal Security & Artifacts (`mss301.bookingservice.security`)
- **`InternalSecurityConfig.java`** (`src/main/java/mss301/bookingservice/security/InternalSecurityConfig.java:23`):
  Configures an independent `SecurityFilterChain` with `@Order(1)` matching `/internal/**`, validating header `X-Internal-Secret`.
- **`InternalSecretFilter.java`** (`src/main/java/mss301/bookingservice/security/InternalSecretFilter.java:23`):
  Checks `HEADER = "X-Internal-Secret"` and assigns authority `INTERNAL_SERVICE`.
- **`InternalClientHeaders.java`** (`src/main/java/mss301/bookingservice/client/InternalClientHeaders.java:12`):
  Attaches `X-Internal-Secret` to outgoing Feign requests.
- **`BookingProperties.java`** (`src/main/java/mss301/bookingservice/config/BookingProperties.java:27,76`):
  Contains `internalSecret` in root configuration and in nested `RemoteService` static class.
- **`application.yaml`** (`src/main/resources/application.yaml:85,94,98,102,106`):
  Declares placeholders for `BOOKING_INTERNAL_SECRET`, `BOOKING_IDENTITY_INTERNAL_SECRET`, `BOOKING_CARE_RECIPIENT_INTERNAL_SECRET`, `BOOKING_PROVIDER_INTERNAL_SECRET`, and `BOOKING_CARE_CATALOG_INTERNAL_SECRET`.
- **`OpenApiConfig.java`** (`src/main/java/mss301/bookingservice/config/swagger/OpenApiConfig.java:30,31,56-60,91-95`):
  Configures `INTERNAL_SCHEME` and skips CSRF/Bearer for `/internal/**`.

### 1.4. Feign Clients (`mss301.bookingservice.client`)
- **`IdentityClient.java`** (`src/main/java/mss301/bookingservice/client/IdentityClient.java:20`):
  Calls `@PostMapping("/internal/users/batch")`. IdentityService has refactored this to `@PostMapping("/api/v1/users/batch")`.
- **`CareRecipientClient.java`** (`src/main/java/mss301/bookingservice/client/CareRecipientClient.java:21,26`):
  Calls `@GetMapping("/internal/care-recipients/{recipientId}/access")` and `@GetMapping("/internal/care-recipients/{recipientId}/snapshot")`. CareRecipientService has refactored these to `/api/v1/care-recipients/{recipientId}/access` and `/api/v1/care-recipients/{recipientId}/snapshot`.
- **`ProviderClient.java`** (`src/main/java/mss301/bookingservice/client/ProviderClient.java:20,23`):
  Calls `@GetMapping("/internal/centers/{centerId}")` and `@GetMapping("/internal/caregivers/{caregiverId}")`. ProviderService has refactored these to `/api/v1/care-centers/{centerId}` and `/api/v1/caregivers/{caregiverId}`.
- **`CareCatalogClient.java`** (`src/main/java/mss301/bookingservice/client/CareCatalogClient.java:16`):
  Calls `@PostMapping("/internal/center-services/quote")`. CareCatalogService exposes this at `/api/v1/center-services/quote`.
- **Client Configurations**:
  `IdentityClientConfig`, `CareRecipientClientConfig`, `ProviderClientConfig`, and `CareCatalogClientConfig` currently inject `InternalClientHeaders.interceptor(...)`.
- **Client DTOs**:
  `CareCatalogQuoteResponse`, `IdentityUserResponse`, `ProviderCaregiverResponse`, `ProviderCenterResponse`, `RecipientAccessResponse`, and `RemoteApiResponse` already have `@JsonIgnoreProperties(ignoreUnknown = true)`. However, `CareCatalogQuoteRequest` and `IdentityUserBatchRequest` do not.

### 1.5. Test Suite & Baseline Execution
- Ran `./mvnw.cmd test-compile` on `BookingService`: **`BUILD SUCCESS`** (128 source files compiled).
- Ran `./mvnw.cmd test` on `BookingService`: **`BUILD SUCCESS`** (69 tests run, 0 failures, 0 errors, 0 skipped).
- `src/test/java/mss301/bookingservice/support/TestPlatformServer.java` is an in-memory HTTP mock server for Identity, CareRecipient, and Provider services. It currently expects `/internal/**` paths and checks `X-Internal-Secret`.
- `src/test/java/mss301/bookingservice/BookingApiTests.java` calls the old `/bookings`, `/center/bookings`, `/admin/bookings`, `/caregiver/occurrences`, and `/internal/bookings/**` paths.

---

## 2. Logic Chain

1. **Domain Aggregate Consolidation (R1 & R3)**:
   - Annotating `BookingController` with `@RequestMapping("/api/v1/bookings")` unifies all booking operations into a single aggregate entrypoint.
   - The Customer flow represents the default resource paths: `POST /api/v1/bookings`, `GET /api/v1/bookings`, `GET /api/v1/bookings/{id}`, `GET /api/v1/bookings/{id}/occurrences`, `POST /api/v1/bookings/{id}/cancel`.
   - The Center Manager flow maps under `/center`: `GET /api/v1/bookings/center`, `GET /api/v1/bookings/center/{id}`, `POST /api/v1/bookings/center/{id}/accept`, `POST /api/v1/bookings/center/{id}/reject`, `PATCH /api/v1/bookings/center/{id}/caregiver`, `PATCH /api/v1/bookings/center/occurrences/{occurrenceId}/caregiver`, `POST /api/v1/bookings/center/{id}/cancel`.
   - The Admin flow maps under `/admin`: `GET /api/v1/bookings/admin`, `GET /api/v1/bookings/admin/{id}`.
   - Relocating `/internal/bookings/caregiver-access` to `GET /api/v1/bookings/caregiver-access` directly fulfills CareRecipientService's `BookingClient` expectation (`@GetMapping("/api/v1/bookings/caregiver-access")`).
   - Relocating `/internal/bookings/{id}/deposit-paid` to `POST /api/v1/bookings/{id}/deposit-paid` provides the public payment confirmation endpoint.
   - Granular authorization is maintained using method-level `@PreAuthorize` (`hasRole('CUSTOMER')`, `hasRole('CENTER_MANAGER')`, `hasRole('ADMIN')`, `isAuthenticated()`).
2. **BookingOccurrence Controller & Service Alignment (R1, R2, R3)**:
   - Renaming `CaregiverOccurrenceController` to `BookingOccurrenceController` with `@RequestMapping("/api/v1/booking-occurrences")` shifts focus to the `BookingOccurrence` aggregate.
   - Renaming `CaregiverOccurrenceService` to `BookingOccurrenceService` (interface) and `BookingOccurrenceServiceImpl` (implementation) satisfies interface purity and naming conventions.
   - Adding `getById(UUID id)` to `BookingOccurrenceService`/`Impl` absorbs the functionality of `/internal/bookings/occurrences/{occurrenceId}`, allowing `GET /api/v1/booking-occurrences/{id}` to serve occurrence details.
3. **Internal Security Purge (R4)**:
   - Deleting `InternalBookingController`, `InternalBookingService`, `InternalBookingServiceImpl`, `InternalSecurityConfig`, `InternalSecretFilter`, and `InternalClientHeaders` completely eliminates backdoor communication and secondary filter chains.
   - Removing `internalSecret` configuration properties from `BookingProperties.java`, `application.yaml`, and `OpenApiConfig.java` closes all configuration remnants.
4. **Feign Client Auth Forwarding (R4)**:
   - Introducing `FeignAuthForwardingInterceptor` extracts the JWT Bearer token from the `SecurityContext` (or `RequestContext` for cookie-based browser sessions) and forwards it along with `X-Request-Id` from MDC to ApiGateway.
   - Rewiring all Feign clients to `/api/v1/**` ensures all downstream service calls adhere to the unified versioning scheme.
   - Adding `@JsonIgnoreProperties(ignoreUnknown = true)` to all client DTOs ensures forward and backward JSON serialization resilience.
5. **Test Harness Adaptation**:
   - Updating `TestPlatformServer` to listen on `/api/v1/**` and accept Bearer tokens ensures tests run against realistic inter-service security conditions.
   - Updating endpoint paths and removing internal secret requirements in `BookingApiTests` ensures the entire test suite validates the new architecture without skipping tests.

---

## 3. Caveats

- **No Database Schema Changes**: BookingService's database schema, JPA entities (`Booking`, `BookingDetail`, `BookingOccurrence`, `BookingStatusHistory`), repositories, and enums remain untouched. No SQL migration or DB alteration is required.
- **Gateway Routing Dependency**: ApiGateway currently routes `/api/v1/bookings/**`, `/api/v1/center/bookings/**`, `/api/v1/admin/bookings/**`, and `/api/v1/caregiver/occurrences/**` with `stripPrefix(2)`. In Milestone 4, ApiGateway will be updated to route `/api/v1/bookings/**` and `/api/v1/booking-occurrences/**` directly without stripping prefixes.
- **No Caveats in Code Execution**: All 69 existing tests can be preserved and run green once updated to target the new URLs and Bearer auth mechanism.

---

## 4. Conclusion & Line-by-Line Blueprint

### 4.1. Summary of Changes

| Category | File | Action | Details |
|---|---|---|---|
| **Delete** | `controller/InternalBookingController.java` | DELETE | Removed internal backdoor controller |
| **Delete** | `service/InternalBookingService.java` | DELETE | Merged into `BookingService` & `BookingOccurrenceService` |
| **Delete** | `service/Impl/InternalBookingServiceImpl.java` | DELETE | Merged into `BookingServiceImpl` & `BookingOccurrenceServiceImpl` |
| **Delete** | `security/InternalSecurityConfig.java` | DELETE | Removed internal security filter chain |
| **Delete** | `security/InternalSecretFilter.java` | DELETE | Removed internal secret filter |
| **Delete** | `client/InternalClientHeaders.java` | DELETE | Replaced by `FeignAuthForwardingInterceptor` |
| **Rename/Refactor** | `controller/CaregiverOccurrenceController.java` -> `controller/BookingOccurrenceController.java` | RENAME / REFACTOR | `@RequestMapping("/api/v1/booking-occurrences")` |
| **Rename/Refactor** | `service/CaregiverOccurrenceService.java` -> `service/BookingOccurrenceService.java` | RENAME / REFACTOR | Pure interface with `list`, `get`, `getById` |
| **Rename/Refactor** | `service/Impl/CaregiverOccurrenceServiceImpl.java` -> `service/Impl/BookingOccurrenceServiceImpl.java` | RENAME / REFACTOR | Class implementing `BookingOccurrenceService` |
| **Create** | `client/FeignAuthForwardingInterceptor.java` | CREATE | Forwards `Authorization: Bearer <token>` and `X-Request-Id` |
| **Modify** | `controller/BookingController.java` | MODIFY | `@RequestMapping("/api/v1/bookings")`, consolidate Customer/Center/Admin, add `caregiver-access` and `deposit-paid` |
| **Modify** | `service/BookingService.java` | MODIFY | Add `checkCaregiverAccess` and `markDepositPaid` |
| **Modify** | `service/Impl/BookingServiceImpl.java` | MODIFY | Implement `checkCaregiverAccess` and `markDepositPaid` |
| **Modify** | `client/IdentityClient.java` | MODIFY | Endpoint path -> `/api/v1/users/batch` |
| **Modify** | `client/CareRecipientClient.java` | MODIFY | Endpoint paths -> `/api/v1/care-recipients/{recipientId}/access`, `/snapshot` |
| **Modify** | `client/ProviderClient.java` | MODIFY | Endpoint paths -> `/api/v1/care-centers/{centerId}`, `/api/v1/caregivers/{caregiverId}` |
| **Modify** | `client/CareCatalogClient.java` | MODIFY | Endpoint path -> `/api/v1/center-services/quote` |
| **Modify** | `client/IdentityClientConfig.java` | MODIFY | Register `FeignAuthForwardingInterceptor` |
| **Modify** | `client/CareRecipientClientConfig.java` | MODIFY | Register `FeignAuthForwardingInterceptor` |
| **Modify** | `client/ProviderClientConfig.java` | MODIFY | Register `FeignAuthForwardingInterceptor` |
| **Modify** | `client/CareCatalogClientConfig.java` | MODIFY | Register `FeignAuthForwardingInterceptor` |
| **Modify** | `client/dto/CareCatalogQuoteRequest.java` | MODIFY | Add `@JsonIgnoreProperties(ignoreUnknown = true)` |
| **Modify** | `client/dto/IdentityUserBatchRequest.java` | MODIFY | Add `@JsonIgnoreProperties(ignoreUnknown = true)` |
| **Modify** | `config/BookingProperties.java` | MODIFY | Remove `internalSecret` fields |
| **Modify** | `config/swagger/OpenApiConfig.java` | MODIFY | Remove `INTERNAL_SCHEME`, `INTERNAL_PREFIX`, and clean customizers |
| **Modify** | `src/main/resources/application.yaml` | MODIFY | Remove all `internal-secret` settings |
| **Modify** | `src/test/java/mss301/bookingservice/support/TestPlatformServer.java` | MODIFY | Listen on `/api/v1/**` and accept Bearer token |
| **Modify** | `src/test/java/mss301/bookingservice/BookingApiTests.java` | MODIFY | Update request paths to `/api/v1/...` and test Bearer auth |

---

### 4.2. Exact Code Blueprint

#### 1. `BookingController.java`
Path: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\controller\BookingController.java`
```java
package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.AcceptRequest;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.CaregiverAssignRequest;
import mss301.bookingservice.dto.request.DepositPaidRequest;
import mss301.bookingservice.dto.request.ReasonRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.BookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Booking", description = "Booking domain — tạo / xem / thao tác lịch chăm sóc (Customer, Center Manager, Admin, Inter-service)")
public class BookingController {

    BookingService bookingService;

    // ─── CUSTOMER ENDPOINTS ───────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Tạo booking mới (Customer)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> create(@CurrentUserId UUID actorId,
                                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                     @Valid @RequestBody BookingCreateRequest request) {
        return bookingService.create(actorId, idempotencyKey, request);
    }

    @GetMapping
    @Operation(summary = "Danh sách booking của khách hàng (Customer)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<PageResponse<BookingSummaryResponse>> listByCustomer(@CurrentUserId UUID actorId,
                                                                            @RequestParam(required = false) BookingStatus status,
                                                                            @RequestParam(required = false) UUID recipientId,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return bookingService.listByCustomer(actorId, status, recipientId, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết booking của khách hàng (Customer)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> getByCustomer(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return bookingService.getByCustomer(actorId, id);
    }

    @GetMapping("/{id}/occurrences")
    @Operation(summary = "Danh sách ca/buổi chăm sóc của booking (Customer)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<OccurrenceResponse>> listOccurrences(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return bookingService.listOccurrences(actorId, id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Hủy booking (Customer)")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<BookingDetailResponse> cancelByCustomer(@CurrentUserId UUID actorId,
                                                               @PathVariable UUID id,
                                                               @Valid @RequestBody ReasonRequest request) {
        return bookingService.cancelByCustomer(actorId, id, request);
    }

    // ─── CENTER MANAGER ENDPOINTS ─────────────────────────────────────────────

    @GetMapping("/center")
    @Operation(summary = "Danh sách booking của trung tâm (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PageResponse<BookingSummaryResponse>> listByCenter(@CurrentCenterId UUID centerId,
                                                                          @RequestParam(required = false) BookingStatus status,
                                                                          @RequestParam(required = false) UUID caregiverId,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        return bookingService.listByCenter(centerId, status, caregiverId, from, to, page, size);
    }

    @GetMapping("/center/{id}")
    @Operation(summary = "Chi tiết booking của trung tâm (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> getByCenter(@CurrentCenterId UUID centerId, @PathVariable UUID id) {
        return bookingService.getByCenter(centerId, id);
    }

    @PostMapping("/center/{id}/accept")
    @Operation(summary = "Chấp nhận booking (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> accept(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody(required = false) AcceptRequest request) {
        return bookingService.accept(actorId, centerId, id, request);
    }

    @PostMapping("/center/{id}/reject")
    @Operation(summary = "Từ chối booking (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> reject(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody ReasonRequest request) {
        return bookingService.reject(actorId, centerId, id, request);
    }

    @PatchMapping("/center/{id}/caregiver")
    @Operation(summary = "Phân công / đổi caregiver cho booking (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> updateCaregiver(@CurrentUserId UUID actorId,
                                                              @CurrentCenterId UUID centerId,
                                                              @PathVariable UUID id,
                                                              @Valid @RequestBody CaregiverAssignRequest request) {
        return bookingService.updateCaregiver(actorId, centerId, id, request);
    }

    @PatchMapping("/center/occurrences/{occurrenceId}/caregiver")
    @Operation(summary = "Phân công / đổi caregiver riêng cho 1 ca chăm sóc (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(@CurrentUserId UUID actorId,
                                                                     @CurrentCenterId UUID centerId,
                                                                     @PathVariable UUID occurrenceId,
                                                                     @Valid @RequestBody CaregiverAssignRequest request) {
        return bookingService.updateOccurrenceCaregiver(actorId, centerId, occurrenceId, request);
    }

    @PostMapping("/center/{id}/cancel")
    @Operation(summary = "Trung tâm hủy booking (Center Manager)")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<BookingDetailResponse> cancelByCenter(@CurrentUserId UUID actorId,
                                                             @CurrentCenterId UUID centerId,
                                                             @PathVariable UUID id,
                                                             @Valid @RequestBody ReasonRequest request) {
        return bookingService.cancelByCenter(actorId, centerId, id, request);
    }

    // ─── ADMIN ENDPOINTS ──────────────────────────────────────────────────────

    @GetMapping("/admin")
    @Operation(summary = "Xem toàn bộ booking trong hệ thống (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<PageResponse<BookingSummaryResponse>> listAll(@RequestParam(required = false) BookingStatus status,
                                                                     @RequestParam(required = false) UUID centerId,
                                                                     @RequestParam(required = false) UUID customerId,
                                                                     @RequestParam(required = false) UUID recipientId,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return bookingService.listAll(status, centerId, customerId, recipientId, from, to, page, size);
    }

    @GetMapping("/admin/{id}")
    @Operation(summary = "Chi tiết booking bất kỳ (Admin)")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<BookingDetailResponse> getAny(@PathVariable UUID id) {
        return bookingService.getAny(id);
    }

    // ─── INTER-SERVICE & SHARED ENDPOINTS (Relocated from InternalBookingController) ──

    @GetMapping("/caregiver-access")
    @Operation(summary = "Kiểm tra caregiver có được phân công cho người bệnh không (Inter-service / Authenticated)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CaregiverAccessResponse> checkCaregiverAccess(@RequestParam UUID caregiverUserId,
                                                                     @RequestParam UUID recipientId) {
        return bookingService.checkCaregiverAccess(caregiverUserId, recipientId);
    }

    @PostMapping("/{id}/deposit-paid")
    @Operation(summary = "Xác nhận đặt cọc thành công (Inter-service / Authenticated)")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<BookingDetailResponse> markDepositPaid(@PathVariable UUID id,
                                                              @Valid @RequestBody(required = false) DepositPaidRequest request) {
        return bookingService.markDepositPaid(id, request);
    }
}
```

---

#### 2. `BookingOccurrenceController.java`
Path: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\controller\BookingOccurrenceController.java`
(Refactored from `CaregiverOccurrenceController.java`)
```java
package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.BookingOccurrenceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking-occurrences")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Booking Occurrence", description = "Quản lý ca/buổi chăm sóc (Caregiver, Admin, Inter-service)")
public class BookingOccurrenceController {

    BookingOccurrenceService bookingOccurrenceService;

    @GetMapping
    @Operation(summary = "Lịch chăm sóc được giao cho caregiver đang đăng nhập")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<List<CaregiverOccurrenceResponse>> list(@CurrentUserId UUID actorId,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                               @RequestParam(required = false) OccurrenceStatus status) {
        return bookingOccurrenceService.list(actorId, from, to, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết một ca/buổi chăm sóc")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CaregiverOccurrenceResponse> get(@CurrentUserId UUID actorId,
                                                        @PathVariable UUID id,
                                                        Authentication authentication) {
        boolean isCaregiverOnly = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CAREGIVER"))
                && authentication.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_CENTER_MANAGER"));
        if (isCaregiverOnly) {
            return bookingOccurrenceService.get(actorId, id);
        }
        return bookingOccurrenceService.getById(id);
    }
}
```

---

#### 3. `BookingOccurrenceService.java` & `BookingOccurrenceServiceImpl.java`
Path: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\service\BookingOccurrenceService.java`
(Pure interface)
```java
package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BookingOccurrenceService {

    APIResponse<List<CaregiverOccurrenceResponse>> list(UUID actorId, LocalDate from, LocalDate to,
                                                        OccurrenceStatus status);

    APIResponse<CaregiverOccurrenceResponse> get(UUID actorId, UUID id);

    APIResponse<CaregiverOccurrenceResponse> getById(UUID id);
}
```

Path: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\service\Impl\BookingOccurrenceServiceImpl.java`
(Concrete class implementing `BookingOccurrenceService`)
```java
package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.service.BookingOccurrenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BookingOccurrenceServiceImpl implements BookingOccurrenceService {

    BookingOccurrenceRepository bookingOccurrenceRepository;
    OccurrenceMapper occurrenceMapper;
    BookingProperties bookingProperties;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CaregiverOccurrenceResponse>> list(UUID actorId, LocalDate from, LocalDate to,
                                                               OccurrenceStatus status) {
        if (to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) + 1 > bookingProperties.getSchedule().getMaxCaregiverRangeDays()) {
            throw new AppException(ErrorCode.INVALID_DATE_FILTER);
        }
        ZoneId zone = bookingProperties.getSchedule().getTimezone();
        Instant rangeStart = from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        Set<OccurrenceStatus> statuses = status == null ? EnumSet.allOf(OccurrenceStatus.class) : EnumSet.of(status);
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findCaregiverSchedule(actorId, rangeStart,
                rangeEnd, statuses, BookingStatus.CAREGIVER_VISIBLE);
        List<CaregiverOccurrenceResponse> occurrenceResponses = occurrenceMapper.toCaregiverResponseList(occurrences);
        return APIResponse.success("Lấy lịch chăm sóc thành công", occurrenceResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverOccurrenceResponse> get(UUID actorId, UUID id) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findForCaregiver(id, actorId,
                        BookingStatus.CAREGIVER_VISIBLE)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        CaregiverOccurrenceResponse occurrenceResponse = occurrenceMapper.toCaregiverDetailResponse(occurrence);
        return APIResponse.success("Lấy chi tiết buổi chăm sóc thành công", occurrenceResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverOccurrenceResponse> getById(UUID id) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(id)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        CaregiverOccurrenceResponse occurrenceResponse = occurrenceMapper.toCaregiverDetailResponse(occurrence);
        return APIResponse.success("Lấy buổi chăm sóc thành công", occurrenceResponse);
    }
}
```

---

#### 4. `BookingService.java` & `BookingServiceImpl.java` Additions
In `BookingService.java`:
Add the following two methods:
```java
    // ─── INTER-SERVICE / SHARED ───────────────────────────────────────────────

    APIResponse<CaregiverAccessResponse> checkCaregiverAccess(UUID caregiverUserId, UUID recipientId);

    APIResponse<BookingDetailResponse> markDepositPaid(UUID id, DepositPaidRequest request);
```

In `BookingServiceImpl.java`:
Implement the two methods using existing fields (`bookingRepository`, `bookingLifecycleService`, `bookingReadService`, `bookingMapper`, `clock`):
```java
    static String PAYMENT_REFERENCE_PREFIX = "paymentReference=";

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverAccessResponse> checkCaregiverAccess(UUID caregiverUserId, UUID recipientId) {
        boolean allowed = bookingRepository.existsCaregiverAssignment(caregiverUserId, recipientId,
                BookingStatus.CAREGIVER_RECIPIENT_ACCESS, OccurrenceStatus.ACTIVE);
        CaregiverAccessResponse caregiverAccessResponse = bookingMapper.toCaregiverAccessResponse(caregiverUserId,
                recipientId, allowed);
        return APIResponse.success("Kiểm tra phân công thành công", caregiverAccessResponse);
    }

    @Override
    @Transactional
    public APIResponse<BookingDetailResponse> markDepositPaid(UUID id, DepositPaidRequest request) {
        Booking booking = bookingRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            BookingDetailResponse confirmedResponse = bookingReadService.toDetailResponse(booking, false);
            return APIResponse.success("Booking đã được xác nhận đặt cọc trước đó", confirmedResponse);
        }
        BookingAction.PAY_DEPOSIT.apply(booking.getStatus());
        if (!clock.instant().isBefore(booking.getDepositDueAt())) {
            throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
        }
        String paymentReference = request == null ? null : request.getPaymentReference();
        String reason = paymentReference == null || paymentReference.isBlank()
                ? null
                : PAYMENT_REFERENCE_PREFIX + paymentReference.strip();
        bookingLifecycleService.apply(booking, BookingAction.PAY_DEPOSIT, null, reason);
        log.info("Booking {} deposit confirmed", id);
        BookingDetailResponse bookingDetailResponse = bookingReadService.toDetailResponse(booking, false);
        return APIResponse.success("Xác nhận đặt cọc thành công", bookingDetailResponse);
    }
```

---

#### 5. `FeignAuthForwardingInterceptor.java`
Path: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\client\FeignAuthForwardingInterceptor.java`
```java
package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import mss301.bookingservice.config.RequestIdFilter;
import mss301.bookingservice.security.CookieUtils;
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

---

#### 6. Feign Clients Rewiring to `/api/v1/**`
- **`IdentityClient.java`**:
  ```java
  @PostMapping("/api/v1/users/batch")
  RemoteApiResponse<List<IdentityUserResponse>> list(@RequestBody IdentityUserBatchRequest request);
  ```
- **`CareRecipientClient.java`**:
  ```java
  @GetMapping("/api/v1/care-recipients/{recipientId}/access")
  RemoteApiResponse<RecipientAccessResponse> checkAccess(@PathVariable("recipientId") UUID recipientId,
                                                         @RequestParam("userId") UUID userId,
                                                         @RequestParam("minLevel") String minLevel);

  @GetMapping("/api/v1/care-recipients/{recipientId}/snapshot")
  RemoteApiResponse<RecipientSnapshot> getSnapshot(@PathVariable("recipientId") UUID recipientId);
  ```
- **`ProviderClient.java`**:
  ```java
  @GetMapping("/api/v1/care-centers/{centerId}")
  RemoteApiResponse<ProviderCenterResponse> getCenter(@PathVariable("centerId") UUID centerId);

  @GetMapping("/api/v1/caregivers/{caregiverId}")
  RemoteApiResponse<ProviderCaregiverResponse> getCaregiver(@PathVariable("caregiverId") UUID caregiverId);
  ```
- **`CareCatalogClient.java`**:
  ```java
  @PostMapping("/api/v1/center-services/quote")
  RemoteApiResponse<List<CareCatalogQuoteResponse>> quote(@RequestBody CareCatalogQuoteRequest request);
  ```

---

#### 7. Client Configuration Updates
In `IdentityClientConfig.java`, `CareRecipientClientConfig.java`, `ProviderClientConfig.java`, and `CareCatalogClientConfig.java`:
Replace `InternalClientHeaders.interceptor(...)` with `new FeignAuthForwardingInterceptor()`:
```java
    @Bean
    public RequestInterceptor requestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }
```

---

#### 8. Client DTO Updates
Add `@JsonIgnoreProperties(ignoreUnknown = true)` to:
- `mss301.bookingservice.client.dto.CareCatalogQuoteRequest`
- `mss301.bookingservice.client.dto.IdentityUserBatchRequest`

---

#### 9. Configuration Cleanup
- **`BookingProperties.java`**:
  * Delete `String internalSecret = "";` from `BookingProperties` class.
  * Delete `String internalSecret = "";` from `RemoteService` static class.
- **`application.yaml`**:
  * Remove `internal-secret: ${BOOKING_INTERNAL_SECRET:}` under `booking`.
  * Remove `internal-secret` from `booking.identity`, `booking.care-recipient`, `booking.provider`, and `booking.care-catalog`.
- **`OpenApiConfig.java`**:
  * Remove unused `import mss301.bookingservice.security.InternalSecretFilter;`.
  * Remove `INTERNAL_SCHEME` and `INTERNAL_PREFIX` constants.
  * Remove `.addSecuritySchemes(INTERNAL_SCHEME, ...)` from `bookingOpenApi`.
  * In `securityRequirements()`, remove the `if (path.startsWith(INTERNAL_PREFIX))` branch.

---

#### 10. Test Updates (`TestPlatformServer.java` & `BookingApiTests.java`)
In `TestPlatformServer.java`:
1. Register contexts:
   ```java
   server.createContext("/.well-known/jwks.json", this::handleJwks);
   server.createContext("/api/v1/users", this::handleUsers);
   server.createContext("/api/v1/care-recipients", this::handleCareRecipients);
   server.createContext("/api/v1/care-centers", this::handleCenters);
   server.createContext("/api/v1/caregivers", this::handleCaregivers);
   ```
2. In `handleCareRecipients`:
   Path is `/api/v1/care-recipients/{id}/{action}`, so `segments[4]` is `recipientId`, and `segments[5]` is `action`.
3. In `handleCenters`:
   Substring length is `"/api/v1/care-centers/".length()`.
4. In `handleCaregivers`:
   Substring length is `"/api/v1/caregivers/".length()`.
5. In `rejected()`:
   Check `Authorization` header instead of `X-Internal-Secret`:
   ```java
   private boolean rejected(HttpExchange exchange, AtomicBoolean failing) throws IOException {
       String auth = exchange.getRequestHeaders().getFirst("Authorization");
       if (auth == null || !auth.startsWith("Bearer ")) {
           write(exchange, 401, "{\"code\":401,\"message\":\"Bạn chưa được xác thực\"}");
           return true;
       }
       if (failing.get()) {
           write(exchange, 500, "{\"code\":9999,\"message\":\"Uncategorized error\"}");
           return true;
       }
       return false;
   }
   ```

In `BookingApiTests.java`:
1. Update endpoint paths:
   - `/bookings` -> `/api/v1/bookings`
   - `/center/bookings` -> `/api/v1/bookings/center`
   - `/admin/bookings` -> `/api/v1/bookings/admin`
   - `/caregiver/occurrences` -> `/api/v1/booking-occurrences`
   - `/internal/bookings/{id}/deposit-paid` -> `/api/v1/bookings/{id}/deposit-paid`
   - `/internal/bookings/caregiver-access` -> `/api/v1/bookings/caregiver-access`
2. Remove `registry.add("booking.internal-secret", ...)` and client internal secrets.
3. Replace `internalApiRequiresInternalSecret` test with an authenticated security check verifying that requests to `/api/v1/bookings/{id}/deposit-paid` and `/api/v1/bookings/caregiver-access` require valid Bearer authentication.

---

## 5. Verification Method

To independently verify the implementation blueprint once applied:

1. **Verify Interface / Implementation Purity (R2)**:
   ```powershell
   # Root service directory must contain only interfaces (0 classes)
   Get-ChildItem -Path BookingService\src\main\java\mss301\bookingservice\service\*.java | ForEach-Object {
       if ((Get-Content $_.FullName -Raw) -match "public\s+class\s+") { Write-Error "Class found in root service: $($_.Name)" }
   }

   # service/Impl directory must contain only classes ending with Impl (0 interfaces)
   Get-ChildItem -Path BookingService\src\main\java\mss301\bookingservice\service\Impl\*.java | ForEach-Object {
       if ($_.Name -notmatch "Impl\.java$") { Write-Error "Invalid impl name: $($_.Name)" }
   }
   ```

2. **Verify Elimination of All Internal Backdoors (R4)**:
   ```powershell
   # No files with Internal in their names should remain in production code
   Get-ChildItem -Path BookingService\src\main\java -Recurse -Filter "*Internal*.java" | Select-Object FullName
   # Output must be empty.

   # Zero occurrences of X-Internal-Secret or /internal/ in java source files
   Select-String -Path BookingService\src\main\java\**\*.java -Pattern "X-Internal-Secret", "/internal/"
   # Output must be empty.
   ```

3. **Verify API Versioning on All Controllers (R3)**:
   ```powershell
   Get-ChildItem -Path BookingService\src\main\java\mss301\bookingservice\controller\*.java | Select-Object Name
   # Output must be exactly:
   # BookingController.java
   # BookingOccurrenceController.java
   ```

4. **Verify Clean Maven Compilation & Test Execution**:
   ```powershell
   cd d:\FA26\MSS\CareConnect\BE\BookingService
   .\mvnw.cmd clean compile
   # Output: BUILD SUCCESS

   .\mvnw.cmd test
   # Output: BUILD SUCCESS (All tests passing)
   ```
