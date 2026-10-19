# Task Dispatch — Explorer M3.1: BookingService Refactoring Blueprint

## 2026-10-18T16:26:00Z

### Mission
Develop the exact, line-by-line implementation blueprint for BookingService refactoring per requirements R1, R2, R3, R4, R5:
1. **Controller Consolidation (R1 & R3)**:
   - Identify all controllers in `BookingService/src/main/java/mss301/bookingservice/controller/` (`BookingController`, `CaregiverOccurrenceController`, any other controllers).
   - Plan unification of role-separated booking endpoints into `BookingController` under `@RequestMapping("/api/v1/bookings")`.
   - Plan refactoring of `CaregiverOccurrenceController` to `BookingOccurrenceController` under `@RequestMapping("/api/v1/booking-occurrences")`.
   - Specify method-level `@PreAuthorize` annotations for each role (`CUSTOMER`, `CAREGIVER`, `CENTER_MANAGER`, `ADMIN`, `isAuthenticated()`).
2. **Service Layer Interface-Impl Standardization (R2)**:
   - Inspect all services in `service/` and `service/Impl/`.
   - Ensure `CaregiverOccurrenceService` is renamed to `BookingOccurrenceService` (interface) and `BookingOccurrenceServiceImpl` (class in `service/Impl/`).
   - Confirm 100% pure interface coverage in `service/` and 100% class coverage in `service/Impl/`.
3. **Internal Security & Artifact Purge (R4)**:
   - Identify all `/internal/**` files: `InternalBookingController.java`, `InternalBookingService.java`, `InternalBookingServiceImpl.java`, `InternalSecurityConfig.java`, `InternalSecretFilter.java`, `InternalClientHeaders.java`.
   - Plan relocation of `/internal/bookings/caregiver-access` to a public authenticated endpoint on `BookingController`: `GET /api/v1/bookings/{id}/caregiver-access`.
   - Plan removal of `internalSecret` from `BookingProperties.java`, `application.yaml`, and `OpenApiConfig.java`.
4. **Feign Clients & Auth Forwarding (R4)**:
   - Plan implementation of `FeignAuthForwardingInterceptor` in `mss301.bookingservice.client`.
   - Check all Feign clients: `ProviderClient`, `RecipientClient`, `CatalogClient`.
   - Update target endpoint paths to `/api/v1/**` (matching our refactored `CareRecipientService` and `ProviderService` endpoints, e.g. `GET /api/v1/care-centers/{id}`, `GET /api/v1/caregivers/{id}`, `GET /api/v1/care-recipients/{id}/access`, `POST /api/v1/center-services/quote`).
   - Ensure `@JsonIgnoreProperties(ignoreUnknown = true)` on all client DTOs.
5. **Test Suite & Verification Strategy**:
   - List existing tests in `BookingService/src/test/` and identify necessary updates to mock classes / endpoint URLs.
   - Specify exact Maven Wrapper compilation and test commands (`mvnw.cmd clean compile && mvnw.cmd clean test`).

Write your blueprint to:
`d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m3_1\handoff.md`
Send a message via send_message to your parent (c390ebfb-aa07-4694-bdae-bfb529d83f93) when complete.
