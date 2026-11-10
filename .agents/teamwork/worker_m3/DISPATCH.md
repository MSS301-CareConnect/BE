# Task Dispatch — Worker M3

## Objective
Implement Milestone 3 refactoring across **BookingService** and **CareCatalogService** per the blueprints in `explorer_m3_1/handoff.md` and `explorer_m3_2/handoff.md` and requirements R1, R2, R3, R4, R5 in `ORIGINAL_REQUEST.md`.

## Mandatory Warnings
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A forensic auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Detailed Tasks

### Track 1: BookingService (`d:\FA26\MSS\CareConnect\BE\BookingService`)
1. **Controller Consolidation (R1 & R3)**:
   - Refactor `BookingController.java` with `@RequestMapping("/api/v1/bookings")`:
     - Customer: `POST /`, `GET /`, `GET /{id}`, `GET /{id}/occurrences`, `POST /{id}/cancel`
     - Center Manager: `GET /center`, `GET /center/{id}`, `POST /center/{id}/accept`, `POST /center/{id}/reject`, `PATCH /center/{id}/caregiver`, `PATCH /center/occurrences/{occurrenceId}/caregiver`, `POST /center/{id}/cancel`
     - Admin: `GET /admin`, `GET /admin/{id}`
     - Inter-service & Shared: `GET /caregiver-access` and `POST /{id}/deposit-paid` with `@PreAuthorize("isAuthenticated()")`
   - Refactor `CaregiverOccurrenceController.java` -> `BookingOccurrenceController.java` with `@RequestMapping("/api/v1/booking-occurrences")`. Add `GET /{id}` delegating to `bookingOccurrenceService.getById(id)` for non-caregiver callers.
2. **Service Layer Interface-Impl Purity (R2)**:
   - Rename `CaregiverOccurrenceService` (interface) and `CaregiverOccurrenceServiceImpl` (class) to `BookingOccurrenceService` and `BookingOccurrenceServiceImpl`. Add `getById(UUID id)` to interface and impl.
   - Add `checkCaregiverAccess(UUID caregiverUserId, UUID recipientId)` and `markDepositPaid(UUID id, DepositPaidRequest request)` to `BookingService` interface and implement them in `BookingServiceImpl`.
3. **Internal Purge (R4)**:
   - Delete 6 files:
     - `src/main/java/mss301/bookingservice/controller/InternalBookingController.java`
     - `src/main/java/mss301/bookingservice/service/InternalBookingService.java`
     - `src/main/java/mss301/bookingservice/service/Impl/InternalBookingServiceImpl.java`
     - `src/main/java/mss301/bookingservice/security/InternalSecurityConfig.java`
     - `src/main/java/mss301/bookingservice/security/InternalSecretFilter.java`
     - `src/main/java/mss301/bookingservice/client/InternalClientHeaders.java`
   - Clean up `BookingProperties.java` (remove `internalSecret`), `application.yaml` (remove all `internal-secret` keys), and `OpenApiConfig.java` (remove `INTERNAL_SCHEME`, `INTERNAL_PREFIX`, and clean customizers).
4. **Feign Auth Forwarding & Resilient Client DTOs (R4)**:
   - Create `src/main/java/mss301/bookingservice/client/FeignAuthForwardingInterceptor.java`.
   - Rewire `IdentityClient` to `/api/v1/users/batch`.
   - Rewire `CareRecipientClient` to `/api/v1/care-recipients/{recipientId}/access` and `/snapshot`.
   - Rewire `ProviderClient` to `/api/v1/care-centers/{centerId}` and `/api/v1/caregivers/{caregiverId}`.
   - Rewire `CareCatalogClient` to `/api/v1/center-services/quote`.
   - Update `IdentityClientConfig`, `CareRecipientClientConfig`, `ProviderClientConfig`, and `CareCatalogClientConfig` to register `new FeignAuthForwardingInterceptor()`.
   - Ensure all client DTOs have `@JsonIgnoreProperties(ignoreUnknown = true)`.
5. **Test Updates & Verification**:
   - Update `TestPlatformServer.java` to listen on `/api/v1/**` and validate `Authorization: Bearer ...`.
   - Update `BookingApiTests.java` to test `/api/v1/...` paths.
   - Run:
     `cd d:\FA26\MSS\CareConnect\BE\BookingService`
     `.\mvnw.cmd clean compile`
     `.\mvnw.cmd test`
     Ensure BUILD SUCCESS and all 69+ tests pass.

### Track 2: CareCatalogService (`d:\FA26\MSS\CareConnect\BE\CareCatalogService`)
1. **Controller Versioning (R1 & R3)**:
   - Prefix `CatalogItemController.java` with `@RequestMapping({"/api/v1/catalog-items", "/api/v1/care-services"})`. Add `@PreAuthorize("isAuthenticated()")` to public read methods.
   - Prefix `CenterOfferingController.java` with `@RequestMapping("/api/v1/center-services")`. Add `@PreAuthorize("isAuthenticated()")` to public read methods. Add `POST /api/v1/center-services/quote` delegating to `centerOfferingService.quote(request)`.
   - Prefix `PricePlanController.java` with `@RequestMapping("/api/v1/price-plans")`. Add `@PreAuthorize("isAuthenticated()")` to public read methods.
2. **Service Layer & DTOs (R2, R4)**:
   - Delete `InternalCatalogController.java`, `InternalSecurityConfig.java`, and `InternalSecretFilter.java`.
   - Rename `InternalServiceQuoteRequest.java` -> `ServiceQuoteRequest.java` and `InternalServiceQuoteResponse.java` -> `ServiceQuoteResponse.java`.
   - Update `CatalogQuoteService` and `CatalogQuoteServiceImpl` to use `ServiceQuoteRequest` and `ServiceQuoteResponse`.
   - Add `quote(ServiceQuoteRequest request)` to `CenterOfferingService` (interface) and implement in `CenterOfferingServiceImpl` (delegating to `CatalogQuoteService`).
3. **Configuration & Documentation Cleanup**:
   - Clean up `CareCatalogProperties.java`, `application.yaml`, `.env.example`, and `OpenApiConfig.java`.
4. **Test Updates & Verification**:
   - Update `CatalogQuoteServiceTests.java` imports.
   - Run:
     `cd d:\FA26\MSS\CareConnect\BE\CareCatalogService`
     `.\mvnw.cmd clean compile`
     `.\mvnw.cmd test`
     Ensure BUILD SUCCESS.

## Deliverables
Write your comprehensive implementation and test verification report to:
`d:\FA26\MSS\CareConnect\BE\.agents\teamwork\worker_m3\handoff.md`
Notify parent with `send_message`.

## 2026-10-18T16:46:58Z
You are Worker M3.
Your working directory is: d:\FA26\MSS\CareConnect\BE\.agents\teamwork\worker_m3
Your parent is: orchestrator_1 (Conversation ID: c390ebfb-aa07-4694-bdae-bfb529d83f93)
Mission: Execute Milestone 3 refactoring across BookingService and CareCatalogService per blueprints.

