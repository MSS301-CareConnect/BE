# Handoff Report — Explorer Survey 2: ProviderService & BookingService

## 1. Observation

### 1.1. ProviderService Investigation

#### Controllers (11 controllers total)
All 11 controllers reside in `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\controller\`:

1. **`AdminCareCenterController.java`** (Lines 1–91):
   - Path: `@RequestMapping("/admin/centers")`
   - Role: `@PreAuthorize("hasRole('ADMIN')")`
   - Endpoints:
     - `POST /admin/centers` -> `careCenterService.create(actorId, request)`
     - `GET /admin/centers` -> `careCenterService.list(status, keyword, includeDeleted, page, size)`
     - `GET /admin/centers/{id}` -> `careCenterService.get(id)`
     - `PUT /admin/centers/{id}` -> `careCenterService.update(actorId, id, request)`
     - `PATCH /admin/centers/{id}/status` -> `careCenterService.updateStatus(actorId, id, request)`
     - `DELETE /admin/centers/{id}` -> `careCenterService.delete(actorId, id, version)`
   - API Versioning: **Lacks `/api/v1` prefix**. Role-separated controller (`/admin/...`).

2. **`CenterScheduleController.java`** (Lines 1–39):
   - Path: No class-level `@RequestMapping`
   - Role: `@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")`
   - Endpoints:
     - `GET /centers/{centerId}/schedules` -> `scheduleService.centerSchedules(actorId, centerId, page, size)`
     - `PATCH /schedules/{id}/status` -> `scheduleService.updateStatus(actorId, id, request)`
   - API Versioning: **Lacks `/api/v1` prefix**. Fragmented endpoints.

3. **`CaregiverManagementController.java`** (Lines 1–64):
   - Path: No class-level `@RequestMapping`
   - Role: `@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")`
   - Endpoints:
     - `POST /centers/{centerId}/caregivers` -> `caregiverService.create(actorId, centerId, request)`
     - `GET /centers/{centerId}/caregivers` -> `caregiverService.listManaged(actorId, centerId, verification, status, page, size)`
     - `GET /caregivers/{caregiverId}` -> `caregiverService.get(actorId, caregiverId)`
     - `PUT /caregivers/{caregiverId}` -> `caregiverService.update(actorId, caregiverId, request)`
     - `DELETE /caregivers/{caregiverId}` -> `caregiverService.archive(actorId, caregiverId, version)`
   - API Versioning: **Lacks `/api/v1` prefix**.

4. **`CaregiverWorkController.java`** (Lines 1–70):
   - Path: No class-level `@RequestMapping`
   - Role: `@PreAuthorize("hasRole('CAREGIVER')")`
   - Endpoints:
     - `GET /caregiver/profile` -> `caregiverService.myProfile(actorId)`
     - `POST /caregiver/shifts/{shiftId}/registrations` -> `registrationService.register(actorId, shiftId, request)`
     - `GET /caregiver/registrations` -> `registrationService.myRegistrations(actorId, page, size)`
     - `DELETE /caregiver/registrations/{id}` -> `registrationService.cancelOwn(actorId, id, version)`
     - `GET /caregiver/schedules` -> `scheduleService.mySchedules(actorId, page, size)`
     - `PATCH /caregiver/schedules/{id}/status` -> `scheduleService.updateStatus(actorId, id, request)`
   - API Versioning: **Lacks `/api/v1` prefix**. Role-separated controller (`/caregiver/...`).

5. **`AdminVerificationController.java`** (Lines 1–39):
   - Path: `@RequestMapping("/admin")`
   - Role: `@PreAuthorize("hasRole('ADMIN')")`
   - Endpoints:
     - `PATCH /admin/caregivers/{id}/verification` -> `caregiverService.verify(actorId, id, request)`
     - `PATCH /admin/certificates/{id}/verification` -> `certificateService.verify(actorId, id, request)`
   - API Versioning: **Lacks `/api/v1` prefix**. Role-separated controller (`/admin/...`).

6. **`CertificateController.java`** (Lines 1–51):
   - Path: No class-level `@RequestMapping`
   - Role: `@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")`
   - Endpoints:
     - `POST /caregivers/{caregiverId}/certificates` -> `certificateService.create(actorId, caregiverId, request)`
     - `GET /caregivers/{caregiverId}/certificates` -> `certificateService.list(actorId, caregiverId)`
     - `PUT /certificates/{id}` -> `certificateService.update(actorId, id, request)`
     - `DELETE /certificates/{id}` -> `certificateService.delete(actorId, id, version)`
   - API Versioning: **Lacks `/api/v1` prefix**.

7. **`WorkShiftController.java`** (Lines 1–82):
   - Path: No class-level `@RequestMapping`
   - Endpoints:
     - `POST /centers/{centerId}/shifts` (`hasAnyRole('ADMIN','CENTER_MANAGER')`) -> `shiftService.create(actorId, centerId, request)`
     - `GET /shifts` (`hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER','CUSTOMER')`) -> `shiftService.list(centerId, status, fromDate, toDate, page, size)`
     - `GET /shifts/{id}` (`isAuthenticated()`) -> `shiftService.get(id)`
     - `PUT /shifts/{id}` (`hasAnyRole('ADMIN','CENTER_MANAGER')`) -> `shiftService.update(actorId, id, request)`
     - `DELETE /shifts/{id}` (`hasAnyRole('ADMIN','CENTER_MANAGER')`) -> `shiftService.delete(actorId, id, version)`
     - `GET /shifts/{id}/registrations` (`hasAnyRole('ADMIN','CENTER_MANAGER')`) -> `registrationService.listForShift(actorId, id, page, size)`
     - `PATCH /registrations/{id}/review` (`hasAnyRole('ADMIN','CENTER_MANAGER')`) -> `registrationService.review(actorId, id, request)`
   - API Versioning: **Lacks `/api/v1` prefix**.

8. **`ProviderCatalogController.java`** (Lines 1–36):
   - Path: `@RequestMapping("/catalog/caregivers")`
   - Role: `@PreAuthorize("isAuthenticated()")`
   - Endpoints:
     - `GET /catalog/caregivers` -> `caregiverService.catalog(centerId, keyword, page, size)`
     - `GET /catalog/caregivers/{caregiverId}/certificates` -> `certificateService.verifiedList(caregiverId)`
   - API Versioning: **Lacks `/api/v1` prefix**.

9. **`ProviderMediaController.java`** (Lines 1–51):
   - Path: `@RequestMapping("/provider-media")`
   - Role: `@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")`
   - Endpoints:
     - `POST /provider-media/{ownerType}/{ownerId}` -> `providerMediaService.upload(...)`
     - `GET /provider-media/{ownerType}/{ownerId}` -> `providerMediaService.list(...)`
     - `DELETE /provider-media/{ownerType}/{ownerId}/{mediaFileId}` -> `providerMediaService.delete(...)`
   - API Versioning: **Lacks `/api/v1` prefix**.

10. **`InternalCaregiverController.java`** (Lines 1–32):
    - Path: `@RequestMapping("/internal/caregivers")`
    - Endpoints:
      - `GET /internal/caregivers/{caregiverId}`
      - `GET /internal/caregivers/by-user/{userId}`
    - Security: Authenticated by `InternalSecretFilter` via `X-Internal-Secret`.

11. **`InternalCenterController.java`** (Lines 1–27):
    - Path: `@RequestMapping("/internal/centers")`
    - Endpoints:
      - `GET /internal/centers/{centerId}`
    - Security: Authenticated by `InternalSecretFilter` via `X-Internal-Secret`.

#### Services in ProviderService
In `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\service\`:

**Existing Services that are Classes without Interfaces (Violation of R2):**
1. **`CaregiverService.java`** (Line 22: `public class CaregiverService`, 166 lines):
   - Needs extraction to `interface CaregiverService` in `service/` and `class CaregiverServiceImpl` in `service/Impl/`.
   - Methods: `create`, `listManaged`, `get`, `update`, `archive`, `myProfile`, `catalog`, `verify`, `find`.
2. **`CaregiverScheduleService.java`** (Line 26: `public class CaregiverScheduleService`, 105 lines):
   - Needs extraction to `interface CaregiverScheduleService` in `service/` and `class CaregiverScheduleServiceImpl` in `service/Impl/`.
   - Methods: `centerSchedules`, `mySchedules`, `updateStatus`.
3. **`CertificateService.java`** (Line 20: `public class CertificateService`, 126 lines):
   - Needs extraction to `interface CertificateService` in `service/` and `class CertificateServiceImpl` in `service/Impl/`.
   - Methods: `create`, `list`, `verifiedList`, `update`, `delete`, `verify`.
4. **`IdentityAccountService.java`** (Line 14: `public class IdentityAccountService`, 28 lines):
   - Needs extraction to `interface IdentityAccountService` in `service/` and `class IdentityAccountServiceImpl` in `service/Impl/`.
   - Method: `requireActiveRole(UUID userId, String requiredRole)`.
5. **`ShiftRegistrationService.java`** (Line 21: `public class ShiftRegistrationService`, 168 lines):
   - Needs extraction to `interface ShiftRegistrationService` in `service/` and `class ShiftRegistrationServiceImpl` in `service/Impl/`.
   - Methods: `register`, `myRegistrations`, `cancelOwn`, `listForShift`, `review`.
6. **`WorkShiftService.java`** (Line 21: `public class WorkShiftService`, 158 lines):
   - Needs extraction to `interface WorkShiftService` in `service/` and `class WorkShiftServiceImpl` in `service/Impl/`.
   - Methods: `create`, `list`, `get`, `update`, `delete`.

**Existing Services with Interface + Impl:**
- `CareCenterService.java` (interface) + `Impl/CareCenterServiceImpl.java` (class)
- `ProviderMediaService.java` (interface) + `Impl/ProviderMediaServiceImpl.java` (class)
- `InternalProviderService.java` (interface) + `Impl/InternalProviderServiceImpl.java` (class) — to be deleted per R4.

#### Entities and Lombok Annotations (ProviderService)
Directory: `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\entity\`
- Entities: `BaseEntity.java`, `CareCenter.java`, `CaregiverProfile.java`, `CaregiverSchedule.java`, `Certificate.java`, `ProviderMedia.java`, `ShiftRegistration.java`, `WorkShift.java`.
- Lombok Verification:
  - `@Data`: **0 occurrences** (grep confirmed clean).
  - `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`: **100% compliant**.

#### Repositories (ProviderService)
Directory: `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\repository\`
- `CareCenterRepository.java`: `public interface extends JpaRepository<CareCenter, UUID>, JpaSpecificationExecutor<CareCenter>`
- `CaregiverProfileRepository.java`: `public interface extends JpaRepository<CaregiverProfile, UUID>`
- `CaregiverScheduleRepository.java`: `public interface extends JpaRepository<CaregiverSchedule, UUID>`
- `CertificateRepository.java`: `public interface extends JpaRepository<Certificate, UUID>`
- `ProviderMediaRepository.java`: `public interface extends JpaRepository<ProviderMedia, UUID>`
- `ShiftRegistrationRepository.java`: `public interface extends JpaRepository<ShiftRegistration, UUID>`
- `WorkShiftRepository.java`: `public interface extends JpaRepository<WorkShift, UUID>`
- Helper: `CareCenterSpecifications.java` (specification builder class).
- 100% compliant with R2 (all repositories are interfaces).

#### Mappers (ProviderService)
Directory: `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\mapper\`
- `CareCenterMapper.java`: `@Mapper(config = MapStructConfig.class)` — MapStruct interface.
- `InternalProviderMapper.java`: `@Mapper(config = MapStructConfig.class)` — MapStruct interface (serves `/internal/**`).
- `MapStructConfig.java`: `@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING, ...)`
- **`ProviderViewMapper.java`**: **CRITICAL OBSERVATION** — This is a `@Component public class ProviderViewMapper` containing manual builder code:
  ```java
  @Component
  public class ProviderViewMapper {
      public CaregiverResponse caregiver(CaregiverProfile value) { ... }
      public CertificateResponse certificate(Certificate value) { ... }
      public WorkShiftResponse shift(WorkShift value, long approvedCount) { ... }
      public ShiftRegistrationResponse registration(ShiftRegistration value) { ... }
      public ScheduleResponse schedule(CaregiverSchedule value) { ... }
  }
  ```
  It does NOT use MapStruct. To comply with R5, these mappings should be standardized using MapStruct mappers (`@Mapper(config = MapStructConfig.class)`).

#### `/internal/**` Artifacts to Remove in ProviderService (R4)
- Controller:
  - `InternalCaregiverController.java`
  - `InternalCenterController.java`
- Service:
  - `InternalProviderService.java`
  - `InternalProviderServiceImpl.java`
- Security:
  - `InternalSecretFilter.java`
  - `InternalSecurityConfig.java`
- Mapper & DTO:
  - `InternalProviderMapper.java`
  - `InternalCaregiverResponse.java`
  - `InternalCenterResponse.java`
- Test:
  - `InternalProviderApiTests.java`
- Configuration:
  - `provider.internal-secret` in `ProviderProperties.java` and `application.yaml`.

#### Feign Clients in ProviderService (R4)
Directory: `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\client\`
- `IdentityClient.java`:
  - Current: `@FeignClient(name = "identity", url = "${provider.identity.url}")` calling `@GetMapping("/internal/users/{userId}")`.
  - Config: `IdentityClientConfig.java` injects `template.header(InternalSecretFilter.HEADER, secret)`.
  - Required change: Call ApiGateway `http://localhost:8080/api/v1/users/{userId}` forwarding `Authorization: Bearer <token>` via `RequestInterceptor`.
- `MediaClient.java`:
  - Current: `@FeignClient(name = "media", url = "${provider.media.url}")` calling `@PostMapping("/internal/media")`, `@GetMapping("/internal/media/{mediaFileId}")`, etc.
  - Config: `MediaClientConfig.java` injects `X-Internal-Secret`.
  - Required change: Call ApiGateway `http://localhost:8080/api/v1/media/**` forwarding `Authorization: Bearer <token>`.

---

### 1.2. BookingService Investigation

#### Controllers (3 controllers total)
Directory: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\controller\`

1. **`BookingController.java`** (Lines 1–159):
   - No class-level `@RequestMapping`
   - Endpoints split across 3 roles:
     - **CUSTOMER**:
       - `POST /bookings` (`@PreAuthorize("hasRole('CUSTOMER')")`) -> `bookingService.create(...)`
       - `GET /bookings` (`@PreAuthorize("hasRole('CUSTOMER')")`) -> `bookingService.listByCustomer(...)`
       - `GET /bookings/{id}` (`@PreAuthorize("hasRole('CUSTOMER')")`) -> `bookingService.getByCustomer(...)`
       - `GET /bookings/{id}/occurrences` (`@PreAuthorize("hasRole('CUSTOMER')")`) -> `bookingService.listOccurrences(...)`
       - `POST /bookings/{id}/cancel` (`@PreAuthorize("hasRole('CUSTOMER')")`) -> `bookingService.cancelByCustomer(...)`
     - **CENTER_MANAGER**:
       - `GET /center/bookings` (`@PreAuthorize("hasRole('CENTER_MANAGER')")`) -> `bookingService.listByCenter(...)`
       - `GET /center/bookings/{id}` (`@PreAuthorize("hasRole('CENTER_MANAGER')")`) -> `bookingService.getByCenter(...)`
       - `POST /center/bookings/{id}/accept` (`@PreAuthorize("hasRole('CENTER_MANAGER')")`) -> `bookingService.accept(...)`
       - `POST /center/bookings/{id}/reject` (`@PreAuthorize("hasRole('CENTER_MANAGER')")`) -> `bookingService.reject(...)`
       - `PATCH /center/bookings/{id}/caregiver` (`@PreAuthorize("hasRole('CENTER_MANAGER')")`) -> `bookingService.updateCaregiver(...)`
       - `PATCH /center/bookings/occurrences/{occurrenceId}/caregiver` (`@PreAuthorize("hasRole('CENTER_MANAGER')")`) -> `bookingService.updateOccurrenceCaregiver(...)`
       - `POST /center/bookings/{id}/cancel` (`@PreAuthorize("hasRole('CENTER_MANAGER')")`) -> `bookingService.cancelByCenter(...)`
     - **ADMIN**:
       - `GET /admin/bookings` (`@PreAuthorize("hasRole('ADMIN')")`) -> `bookingService.listAll(...)`
       - `GET /admin/bookings/{id}` (`@PreAuthorize("hasRole('ADMIN')")`) -> `bookingService.getAny(...)`
   - API Versioning: **Lacks `/api/v1` prefix**.
   - Path separation: Path prefixes are role-segregated (`/bookings`, `/center/bookings`, `/admin/bookings`).

2. **`CaregiverOccurrenceController.java`** (Lines 1–43):
   - Path: `@RequestMapping("/caregiver/occurrences")`
   - Role: `@PreAuthorize("hasRole('CAREGIVER')")`
   - Endpoints:
     - `GET /caregiver/occurrences` -> `caregiverOccurrenceService.list(actorId, from, to, status)`
     - `GET /caregiver/occurrences/{id}` -> `caregiverOccurrenceService.get(actorId, id)`
   - API Versioning: **Lacks `/api/v1` prefix**. Role-separated controller (`/caregiver/...`).

3. **`InternalBookingController.java`** (Lines 1–49):
   - Path: `@RequestMapping("/internal/bookings")`
   - Endpoints:
     - `GET /internal/bookings/caregiver-access` -> `internalBookingService.checkCaregiverAccess(...)` (called by CareRecipientService)
     - `POST /internal/bookings/{id}/deposit-paid` -> `internalBookingService.markDepositPaid(...)`
     - `GET /internal/bookings/occurrences/{occurrenceId}` -> `internalBookingService.getOccurrence(...)`
     - `GET /internal/bookings/{id}` -> `internalBookingService.get(...)`
   - Security: Authenticated by `InternalSecretFilter` via `X-Internal-Secret`.

#### Services in BookingService
Directory: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\service\` and `service/Impl/`

All 15 services are interfaces in `service/` with implementations in `service/Impl/`:
1. `BookingCodeGenerator` -> `BookingCodeGeneratorImpl`
2. `BookingExpiryService` -> `BookingExpiryServiceImpl`
3. `BookingLifecycleService` -> `BookingLifecycleServiceImpl`
4. `BookingReadService` -> `BookingReadServiceImpl`
5. `BookingService` -> `BookingServiceImpl`
6. `CareRecipientDirectoryService` -> `CareRecipientDirectoryServiceImpl`
7. `CareServiceCatalogPort` -> `CareServiceCatalogPortImpl`
8. `CaregiverOccurrenceService` -> `CaregiverOccurrenceServiceImpl` (to evolve into `BookingOccurrenceService` / `BookingOccurrenceServiceImpl`)
9. `CaregiverPort` -> `CaregiverPortImpl`
10. `CenterPort` -> `CenterPortImpl`
11. `IdentityUserService` -> `IdentityUserServiceImpl`
12. `InternalBookingService` -> `InternalBookingServiceImpl` (to delete per R4)
13. `OccurrenceGenerator` -> `OccurrenceGeneratorImpl`
14. `RefundPolicy` -> `RefundPolicyImpl`
15. `ScheduleConflictService` -> `ScheduleConflictServiceImpl`
- Compliance: **100% compliant with R2 (all root services are interfaces)**.

#### Entities and Lombok Annotations (BookingService)
Directory: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\entity\`
- Entities: `BaseEntity.java`, `Booking.java`, `BookingDetail.java`, `BookingOccurrence.java`, `BookingStatusHistory.java`.
- Lombok Verification:
  - `@Data`: **0 occurrences**.
  - `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)`: **100% compliant**.

#### Repositories (BookingService)
Directory: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\repository\`
- `BookingRepository.java`: `public interface extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking>`
- `BookingDetailRepository.java`: `public interface extends JpaRepository<BookingDetail, UUID>`
- `BookingOccurrenceRepository.java`: `public interface extends JpaRepository<BookingOccurrence, UUID>`
- `BookingStatusHistoryRepository.java`: `public interface extends JpaRepository<BookingStatusHistory, UUID>`
- Helper: `BookingSpecifications.java` (class with static JPA Specification methods).
- Compliance: **100% compliant with R2 (all repositories are interfaces)**.

#### Mappers (BookingService)
Directory: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\mapper\`
- `BookingMapper.java`: `@Mapper(config = MapStructConfig.class, uses = {OccurrenceMapper.class, BookingDetailMapper.class})`
- `BookingDetailMapper.java`: `@Mapper(config = MapStructConfig.class)`
- `OccurrenceMapper.java`: `@Mapper(config = MapStructConfig.class)`
- `BookingStatusHistoryMapper.java`: `@Mapper(config = MapStructConfig.class)`
- `MapStructConfig.java`: `@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING, ...)`
- Compliance: **100% compliant with R5 (MapStruct with spring component model)**.

#### `/internal/**` Artifacts to Remove in BookingService (R4)
- Controller:
  - `InternalBookingController.java`
- Service:
  - `InternalBookingService.java`
  - `InternalBookingServiceImpl.java`
- Security:
  - `InternalSecretFilter.java`
  - `InternalSecurityConfig.java`
- Client Header Helper:
  - `InternalClientHeaders.java`
- Tests:
  - Internal API test blocks in `BookingApiTests.java` (lines 429, 683–700, 779–786).
- Configuration:
  - `booking.internal-secret` and `booking.*.internal-secret` in `BookingProperties.java` and `application.yaml`.

#### Feign Clients in BookingService (R4)
Directory: `d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\client\`
- 4 Clients:
  1. `CareCatalogClient.java`:
     - Current: calls `@PostMapping("/internal/center-services/quote")`
     - Config: `CareCatalogClientConfig.java` uses `InternalClientHeaders.interceptor(...)`
     - Required change: Call ApiGateway `http://localhost:8080/api/v1/care-catalog/quote` (or equivalent) forwarding Bearer token.
  2. `CareRecipientClient.java`:
     - Current: calls `@GetMapping("/internal/care-recipients/{recipientId}/access")` and `@GetMapping("/internal/care-recipients/{recipientId}/snapshot")`
     - Config: `CareRecipientClientConfig.java` uses `InternalClientHeaders.interceptor(...)`
     - Required change: Call ApiGateway `http://localhost:8080/api/v1/care-recipients/{recipientId}/access` and `/snapshot` forwarding Bearer token.
  3. `IdentityClient.java`:
     - Current: calls `@PostMapping("/internal/users/batch")`
     - Config: `IdentityClientConfig.java` uses `InternalClientHeaders.interceptor(...)`
     - Required change: Call ApiGateway `http://localhost:8080/api/v1/users/batch` forwarding Bearer token.
  4. `ProviderClient.java`:
     - Current: calls `@GetMapping("/internal/centers/{centerId}")` and `@GetMapping("/internal/caregivers/{caregiverId}")`
     - Config: `ProviderClientConfig.java` uses `InternalClientHeaders.interceptor(...)`
     - Required change: Call ApiGateway `http://localhost:8080/api/v1/care-centers/{centerId}` and `http://localhost:8080/api/v1/caregivers/{caregiverId}` forwarding Bearer token.

---

## 2. Logic Chain

### 2.1. ProviderService Refactoring Logic

```
Observation: 11 controllers exist with fragmented paths (/admin/centers, /centers/.../schedules, /caregiver/..., /admin/..., /catalog/caregivers, /provider-media, /internal/...) and 0 endpoints have /api/v1.
Logic Step 1: Group by Domain Aggregate according to R1:
  - CareCenter: AdminCareCenterController + CenterScheduleController -> CareCenterController (/api/v1/care-centers/**)
  - Caregiver: CaregiverManagementController + CaregiverWorkController + AdminVerificationController (caregiver portion) + ProviderCatalogController (caregiver portion) -> CaregiverController (/api/v1/caregivers/**)
  - WorkShift: WorkShiftController + ShiftRegistrationController -> WorkShiftController (/api/v1/work-shifts/**)
  - Certificate: CertificateController + AdminVerificationController (certificate portion) + ProviderCatalogController (certificate portion) -> CertificateController (/api/v1/certificates/**)
  - Media: ProviderMediaController -> ProviderMediaController (/api/v1/provider-media/**)
  - Internal: InternalCaregiverController + InternalCenterController -> DELETED per R4.
Conclusion 1: ProviderService collapses from 11 controllers to 4 primary domain controllers + 1 media controller, all prefixed with /api/v1.
```

```
Observation: 6 service classes in ProviderService (CaregiverService, CaregiverScheduleService, CertificateService, IdentityAccountService, ShiftRegistrationService, WorkShiftService) are public classes directly in service/, while R2 mandates 100% root services are interfaces and 100% implementations are in service/Impl/.
Logic Step 2: Extract public interfaces for all 6 classes into service/ and move implementation classes with "Impl" suffix into service/Impl/. Apply @Service and @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true) on Impl classes.
Conclusion 2: All 6 services will strictly conform to the Interface + Impl convention.
```

```
Observation: ProviderViewMapper is a @Component class performing manual builder mapping, while CareCenterMapper uses MapStruct.
Logic Step 3: R5 specifies MapStruct mapper usage compatible with JPA Buddy workflow. ProviderViewMapper should be refactored into MapStruct interfaces (@Mapper(config = MapStructConfig.class)).
Conclusion 3: Elevate ProviderService mapper layer to full MapStruct standard.
```

### 2.2. BookingService Refactoring Logic

```
Observation: BookingController contains role-split paths (/bookings, /center/bookings, /admin/bookings), CaregiverOccurrenceController is role-separated (/caregiver/occurrences), and InternalBookingController serves /internal/**. 0 endpoints have /api/v1.
Logic Step 4:
  - Consolidate all booking operations into unified BookingController (@RequestMapping("/api/v1/bookings")).
    * Create, list, get, accept, reject, assign caregiver, cancel -> unified under /api/v1/bookings/** with method-level @PreAuthorize.
  - Consolidate occurrence operations into BookingOccurrenceController (@RequestMapping("/api/v1/booking-occurrences")):
    * Occurrence status updates (check-in, complete, cancel occurrence)
    * Occurrence list and detail (caregiver schedule view and center manager occurrence view)
    * Single occurrence caregiver reassignment
  - Delete InternalBookingController per R4.
Conclusion 4: BookingService adheres to clean 2-controller aggregate architecture: BookingController and BookingOccurrenceController.
```

```
Observation: CaregiverOccurrenceService in BookingService is an interface with CaregiverOccurrenceServiceImpl in Impl/, but is named narrowly for caregiver.
Logic Step 5: Refactor CaregiverOccurrenceService to BookingOccurrenceService and CaregiverOccurrenceServiceImpl to BookingOccurrenceServiceImpl to support the full aggregate scope (caregiver check-in/complete, center manager occurrence management).
Conclusion 5: Domain service naming matches aggregate controller.
```

```
Observation: CareRecipientService calls GET /internal/bookings/caregiver-access to determine caregiver access to recipient records.
Logic Step 6: With /internal/** removed per R4, this endpoint must become an authorized public endpoint /api/v1/bookings/caregiver-access (or /api/v1/booking-occurrences/caregiver-access) accessible through ApiGateway using the caller's JWT token.
Conclusion 6: Service-to-service communication routes cleanly through ApiGateway without internal backdoors.
```

```
Observation: 4 Feign clients in BookingService (CareCatalogClient, CareRecipientClient, IdentityClient, ProviderClient) and 2 in ProviderService (IdentityClient, MediaClient) inject X-Internal-Secret and call /internal/** endpoints directly.
Logic Step 7: Under R4, replace X-Internal-Secret interceptors with a JWT Bearer token forwarding RequestInterceptor:
  ```java
  @Bean
  public RequestInterceptor bearerTokenRequestInterceptor() {
      return template -> {
          Authentication auth = SecurityContextHolder.getContext().getAuthentication();
          if (auth instanceof JwtAuthenticationToken jwtAuth) {
              template.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuth.getToken().getTokenValue());
          }
      };
  }
  ```
  And point Feign client URLs to ApiGateway `http://localhost:8080/api/v1/...`.
Conclusion 7: 100% compliance with R4 (Zero `/internal/**`, 100% ApiGateway routed).
```

---

## 3. Caveats

1. **ApiGateway Route Stripping**: Currently `RouteConfig` uses `stripPrefix(2)` which strips `/api/v1`. If services add `/api/v1` to controller mappings, `stripPrefix` must either be removed or adjusted so services receive the expected path.
2. **Batch User Endpoint**: In BookingService, `IdentityClient` calls `POST /internal/users/batch`. Under R4, IdentityService's `UserController` (or `InternalUserController` migration) must provide a `/api/v1/users/batch` endpoint reachable through ApiGateway.
3. **Caregiver Access Check Authorization**: `CareRecipientService` checking caregiver access via `GET /internal/bookings/caregiver-access` passes caregiverUserId as a query param. When routing through ApiGateway with Bearer token, the token belongs to the logged-in caregiver (actor), so the access endpoint can verify the caller context directly from JWT `sub`.
4. **Deposit Paid Endpoint**: `InternalBookingController` has `POST /internal/bookings/{id}/deposit-paid`. In the absence of PaymentService, this endpoint must be exposed as `/api/v1/bookings/{id}/deposit-paid` (or ADMIN/SYSTEM authorized) when /internal is eliminated.
5. **Database Schemas and Data**: No database DDL migrations are required for R1, R2, R4, R5, as entities and tables remain unchanged.

---

## 4. Conclusion & Actionable Mapping

### 4.1. ProviderService Action Plan

| Current File | Type | Target Action | Target File / Destination |
|---|---|---|---|
| `AdminCareCenterController.java` | Controller | **Merge** into unified CareCenterController | `controller/CareCenterController.java` (`/api/v1/care-centers/**`) |
| `CenterScheduleController.java` | Controller | **Merge** into unified CareCenterController | `controller/CareCenterController.java` (`/api/v1/care-centers/**`) |
| `CaregiverManagementController.java` | Controller | **Merge** into unified CaregiverController | `controller/CaregiverController.java` (`/api/v1/caregivers/**`) |
| `CaregiverWorkController.java` | Controller | **Merge** into CaregiverController & WorkShiftController | `controller/CaregiverController.java` & `controller/WorkShiftController.java` |
| `AdminVerificationController.java` | Controller | **Merge** into CaregiverController & CertificateController | `controller/CaregiverController.java` & `controller/CertificateController.java` |
| `CertificateController.java` | Controller | **Refactor & version** with `/api/v1` | `controller/CertificateController.java` (`/api/v1/certificates/**`) |
| `WorkShiftController.java` | Controller | **Refactor & version** with `/api/v1` | `controller/WorkShiftController.java` (`/api/v1/work-shifts/**`) |
| `ProviderCatalogController.java` | Controller | **Merge** into CaregiverController & CertificateController | `controller/CaregiverController.java` & `controller/CertificateController.java` |
| `ProviderMediaController.java` | Controller | **Version** with `/api/v1` | `controller/ProviderMediaController.java` (`/api/v1/provider-media/**`) |
| `InternalCaregiverController.java` | Controller | **DELETE** | None |
| `InternalCenterController.java` | Controller | **DELETE** | None |
| `CaregiverService.java` (class) | Service | **Extract interface + Impl** | Interface `service/CaregiverService.java`, Class `service/Impl/CaregiverServiceImpl.java` |
| `CaregiverScheduleService.java` (class) | Service | **Extract interface + Impl** | Interface `service/CaregiverScheduleService.java`, Class `service/Impl/CaregiverScheduleServiceImpl.java` |
| `CertificateService.java` (class) | Service | **Extract interface + Impl** | Interface `service/CertificateService.java`, Class `service/Impl/CertificateServiceImpl.java` |
| `IdentityAccountService.java` (class) | Service | **Extract interface + Impl** | Interface `service/IdentityAccountService.java`, Class `service/Impl/IdentityAccountServiceImpl.java` |
| `ShiftRegistrationService.java` (class) | Service | **Extract interface + Impl** | Interface `service/ShiftRegistrationService.java`, Class `service/Impl/ShiftRegistrationServiceImpl.java` |
| `WorkShiftService.java` (class) | Service | **Extract interface + Impl** | Interface `service/WorkShiftService.java`, Class `service/Impl/WorkShiftServiceImpl.java` |
| `InternalProviderService.java` | Service | **DELETE** | None |
| `InternalProviderServiceImpl.java` | Service | **DELETE** | None |
| `InternalSecretFilter.java` | Security | **DELETE** | None |
| `InternalSecurityConfig.java` | Security | **DELETE** | None |
| `InternalProviderMapper.java` | Mapper | **DELETE** | None |
| `ProviderViewMapper.java` | Mapper | **Standardize with MapStruct** | Convert to MapStruct interfaces or keep temporary adapter |
| `IdentityClient.java` & config | Client | **Update to ApiGateway + Bearer** | Route to `http://localhost:8080/api/v1/users/{userId}` |
| `MediaClient.java` & config | Client | **Update to ApiGateway + Bearer** | Route to `http://localhost:8080/api/v1/media/**` |

### 4.2. BookingService Action Plan

| Current File | Type | Target Action | Target File / Destination |
|---|---|---|---|
| `BookingController.java` | Controller | **Refactor & version** with `/api/v1/bookings/**` | `controller/BookingController.java` (`/api/v1/bookings/**`) |
| `CaregiverOccurrenceController.java` | Controller | **Refactor & expand** to BookingOccurrenceController | `controller/BookingOccurrenceController.java` (`/api/v1/booking-occurrences/**`) |
| `InternalBookingController.java` | Controller | **DELETE** (move necessary endpoints to public controllers) | `controller/BookingController.java` (`/api/v1/bookings/caregiver-access`) |
| `CaregiverOccurrenceService.java` | Service | **Rename / Refactor** to BookingOccurrenceService | `service/BookingOccurrenceService.java` |
| `CaregiverOccurrenceServiceImpl.java` | Service | **Rename / Refactor** to BookingOccurrenceServiceImpl | `service/Impl/BookingOccurrenceServiceImpl.java` |
| `InternalBookingService.java` | Service | **DELETE** | None |
| `InternalBookingServiceImpl.java` | Service | **DELETE** | None |
| `InternalSecretFilter.java` | Security | **DELETE** | None |
| `InternalSecurityConfig.java` | Security | **DELETE** | None |
| `InternalClientHeaders.java` | Client Helper | **DELETE** | None |
| `CareCatalogClient.java` & config | Client | **Update to ApiGateway + Bearer** | Route to `http://localhost:8080/api/v1/catalog/...` |
| `CareRecipientClient.java` & config | Client | **Update to ApiGateway + Bearer** | Route to `http://localhost:8080/api/v1/care-recipients/...` |
| `IdentityClient.java` & config | Client | **Update to ApiGateway + Bearer** | Route to `http://localhost:8080/api/v1/users/batch` |
| `ProviderClient.java` & config | Client | **Update to ApiGateway + Bearer** | Route to `http://localhost:8080/api/v1/care-centers/...` & `/caregivers/...` |

---

## 5. Verification Method

To verify the findings and the resulting refactored code:

1. **Verify Interface & Impl Separation (R2)**:
   - Check ProviderService service root:
     `Get-ChildItem -Path d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\service\*.java`
     Must only contain `public interface`.
   - Check ProviderService Impl:
     `Get-ChildItem -Path d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\service\Impl\*.java`
     Must contain all implementation classes ending with `ServiceImpl.java`.

2. **Verify Deletion of Internal Artifacts (R4)**:
   - Search across codebase:
     `rg -n "InternalSecretFilter|InternalSecurityConfig|X-Internal-Secret" d:\FA26\MSS\CareConnect\BE\ProviderService d:\FA26\MSS\CareConnect\BE\BookingService`
     Result must be 0 matches.

3. **Verify API Versioning (R3)**:
   - Search controller request mappings:
     `rg -n "@RequestMapping" d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\controller`
     `rg -n "@RequestMapping" d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\controller`
     Every base path must start with `/api/v1/`.

4. **Verify Lombok & MapStruct Conventions (R5)**:
   - Search entities for `@Data`:
     `rg -n "@Data" d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\entity`
     `rg -n "@Data" d:\FA26\MSS\CareConnect\BE\BookingService\src\main\java\mss301\bookingservice\entity`
     Result must be 0 matches.

5. **Build and Test Verification**:
   - ProviderService build:
     `cd d:\FA26\MSS\CareConnect\BE\ProviderService; .\mvnw.cmd clean compile`
   - BookingService build:
     `cd d:\FA26\MSS\CareConnect\BE\BookingService; .\mvnw.cmd clean compile`
   - Both builds must complete with `BUILD SUCCESS` and zero compilation errors.
