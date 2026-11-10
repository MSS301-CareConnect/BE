# Project: CareConnect Microservices Architecture Refactoring

## Architecture
CareConnect is a microservices-based home care platform comprising:
1. `IdentityService` (port 8081): Authentication, user identity, credentials, roles & permissions.
2. `CareRecipientService` (port 8082): Care recipient profiles, emergency contacts, medications, managers.
3. `ProviderService` (port 8084): Care centers, caregivers, work shifts, schedules, certificates.
4. `BookingService` (port 8085): Bookings, occurrences, check-ins, status transitions.
5. `CareCatalogService` (port 8083): Catalog items, center offerings, price plans, quotes.
6. `MediaService` (port 8086): Media file storage (R2/S3) and download URLs.
7. `ApiGateway` (port 8080): Unified reverse proxy, JWT verification, header sanitization, routing for all 6 services.

### Core Architectural Principles
- **Domain Aggregate Structure (R1)**:
  - Entity: `[Domain]` (`@Getter @Setter`, strictly no `@Data`)
  - Repository: `[Domain]Repository` (`interface` in `repository/`)
  - Service: `[Domain]Service` (`interface` in `service/`)
  - ServiceImpl: `[Domain]ServiceImpl` (`class` in `service/Impl/`)
  - Controller: `[Domain]Controller` (`class` in `controller/`, unified endpoints with method-level `@PreAuthorize`)
- **Pure Interface & Impl Layers (R2)**:
  - 100% of root services in `service/` are pure interfaces.
  - 100% of implementations reside in `service/Impl/` ending in `ServiceImpl`.
- **API Versioning (R3)**:
  - 100% of public endpoints prefixed with `/api/v1/`.
  - Auth, registration, OTP, password reset unified under `/api/v1/auth/**` in `AuthenticationController`.
  - Profile & administration under `/api/v1/users/**` in `UserController`.
- **Zero `/internal/**` & ApiGateway Routing with Feign JWT Forwarding (R4)**:
  - All inter-service calls route via ApiGateway (`http://localhost:8080/api/v1/...`).
  - OpenFeign clients use `FeignAuthForwardingInterceptor` to forward `Authorization: Bearer <token>` and `X-Request-Id`.
  - ApiGateway forwards `/api/v1/...` directly without stripping prefix (`stripPrefix` removed).
  - All `*Internal*.java`, `InternalSecretFilter`, `InternalSecurityConfig`, `X-Internal-Secret` deleted.
- **Entity & MapStruct Standardization (R5)**:
  - `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)` on entities.
  - MapStruct (`@Mapper(config = MapStructConfig.class)`) for DTO-entity conversions.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Identity: Auth vs User Separation | Move register, verify, resend-otp, forgot-password, reset-password from AccountController to AuthenticationController (/api/v1/auth/**) | M1 | R3 |
| 2 | Identity: UserController & UserService | Rename AccountController -> UserController (/api/v1/users/**), AccountService -> UserService / UserServiceImpl | M1 | R1, R2, R3 |
| 3 | Identity: Remove Internal & Expose User APIs | Delete InternalUserController/Service/Config/Filter; expose user profile & batch lookup on UserController (/api/v1/users/**) | M1 | R4 |
| 4 | Identity: Versioning on Role & Permission | Add /api/v1 prefix to RoleController (/api/v1/roles/**) and PermissionController (/api/v1/permissions/**); sync SecurityConfig | M1 | R3 |
| 5 | CareRecipient: Unified Controller | Merge AdminCareRecipientController, CaregiverCareRecipientController, and CareRecipientController into single CareRecipientController (/api/v1/care-recipients/**) | M1 | R1 |
| 6 | CareRecipient: Merge Fragmented Services | Merge CaregiverRecipientService, CaregiverAssignmentService, InternalCareRecipientService into CareRecipientService / CareRecipientServiceImpl | M1 | R1, R2 |
| 7 | CareRecipient: Remove Internal & Sub-resource Versioning | Delete InternalCareRecipientController/Service/Config/Filter; add /api/v1 prefix to EmergencyContact, Medication, RecipientManager controllers | M1 | R3, R4 |
| 8 | Provider: 11 Controllers to 4 Aggregate Controllers | Merge 11 controllers into CareCenterController, CaregiverController, WorkShiftController, CertificateController + ProviderMediaController (/api/v1/**) | M2 | R1, R3 |
| 9 | Provider: Extract 6 Root Services to Interface + Impl | Convert CaregiverService, CaregiverScheduleService, CertificateService, IdentityAccountService, ShiftRegistrationService, WorkShiftService to interface in service/ + class in service/Impl/ | M2 | R2 |
| 10 | Provider: Remove Internal Artifacts | Delete InternalCaregiverController, InternalCenterController, InternalProviderService/Impl, InternalSecurityConfig, InternalSecretFilter | M2 | R4 |
| 11 | Provider: MapStruct Standardization | Standardize ProviderViewMapper to use MapStruct mappers | M2 | R5 |
| 12 | Booking: Unified BookingController | Consolidate role-separated endpoints (/bookings, /center/bookings, /admin/bookings) into single BookingController (/api/v1/bookings/**) | M3 | R1, R3 |
| 13 | Booking: BookingOccurrenceController & Service | Refactor CaregiverOccurrenceController to BookingOccurrenceController (/api/v1/booking-occurrences/**); rename service to BookingOccurrenceService / Impl | M3 | R1, R2, R3 |
| 14 | Booking: Remove Internal Artifacts | Delete InternalBookingController, InternalBookingService/Impl, InternalSecurityConfig, InternalSecretFilter, InternalClientHeaders; expose caregiver-access check on BookingController | M3 | R4 |
| 15 | CareCatalog: Versioning & Quote Relocation | Add /api/v1 prefix to CatalogItemController, CenterOfferingController, PricePlanController; relocate quote endpoint to CenterOfferingController (/api/v1/center-services/quote) | M3 | R1, R3, R4 |
| 16 | CareCatalog: Remove Internal Artifacts | Delete InternalCatalogController, InternalSecurityConfig, InternalSecretFilter | M3 | R4 |
| 17 | MediaService: OAuth2 Resource Server & Public MediaController | Add oauth2-resource-server, JWT config, refactor InternalMediaController to MediaController (/api/v1/media/**), delete InternalSecurityConfig & InternalSecretFilter | M4 | R4 |
| 18 | ApiGateway: 6 Service Routes & stripPrefix Removal | Add routes for all 6 services in GatewayPaths, GatewayProperties, RouteConfig; remove stripPrefix(2); delete InternalPathBlockFilter; update auth token endpoints | M4 | R4 |
| 19 | Cross-Service Feign & JWT Auth Forwarding | Update all 7 Feign clients across CareRecipient, Provider, Booking to call ApiGateway (http://localhost:8080) on /api/v1/** with FeignAuthForwardingInterceptor | M4 | R4 |
| 20 | AGENTS.md Architectural Documentation Update | Update AGENTS.md with all new rules (R1-R6): aggregate pattern, interface-impl, ApiGateway Feign JWT forward, JPA Buddy MapStruct, /api/v1 versioning | M5 | R6 |
| 21 | Clean Compilation & Final Verification | Full mvn clean compile verification across all 6 services + ApiGateway; Forensic Audit verification | M5 | Acceptance Criteria |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M0 | Survey & Planning | Architectural survey across 6 services, Feign mapping, Gateway analysis | none | DONE |
| M1 | Identity & CareRecipient Refactoring | R1, R2, R3, R4, R5 for IdentityService & CareRecipientService | M0 | DONE |
| M2 | ProviderService Refactoring | 11-to-4 Controller unification, 6 Service interface/Impl conversions, /internal deletion, MapStruct | M1 | **DONE** (Gate Passed, 27 tests pass) |
| M3 | Booking & CareCatalog Refactoring | BookingController & BookingOccurrenceController unification, Catalog /api/v1 & quote relocation, /internal deletion | M1, M2 | IN_PROGRESS |
| M4 | MediaService, ApiGateway & Feign Auth Forwarding | MediaService OAuth2/MediaController, ApiGateway 6 routes + stripPrefix removal, 7 Feign clients to Gateway + Bearer forwarding | M1, M2, M3 | PLANNED |
| M5 | Documentation & Final Clean Compilation | AGENTS.md update with all rules, full 6-service + Gateway Maven clean compile, Forensic Audit & Gate | M4 | PLANNED |

---

## Interface Contracts

### Inter-Service Public API Endpoints (Replacing /internal/**)
1. **IdentityService (`/api/v1/users/**`)**:
   - `GET /api/v1/users/{id}`: Single user lookup (accessible to authenticated users).
   - `GET /api/v1/users/by-email?email={email}`: User lookup by email.
   - `POST /api/v1/users/batch`: Batch user lookup by IDs (`UserBatchRequest`).
2. **CareRecipientService (`/api/v1/care-recipients/**`)**:
   - `GET /api/v1/care-recipients/{id}/snapshot`: Care recipient snapshot for bookings.
   - `GET /api/v1/care-recipients/{id}/access?userId={userId}&minLevel={level}`: Check user access level.
   - `POST /api/v1/care-recipients/batch`: Batch recipient lookup.
3. **ProviderService (`/api/v1/care-centers/**` & `/api/v1/caregivers/**`)**:
   - `GET /api/v1/care-centers/{centerId}`: Center details.
   - `GET /api/v1/caregivers/{caregiverId}`: Caregiver profile.
   - `GET /api/v1/caregivers/by-user/{userId}`: Caregiver profile by user ID.
4. **CareCatalogService (`/api/v1/center-services/**`)**:
   - `POST /api/v1/center-services/quote`: Service quote calculation for booking creation.
5. **BookingService (`/api/v1/bookings/**` & `/api/v1/booking-occurrences/**`)**:
   - `GET /api/v1/bookings/caregiver-access?caregiverUserId={id}&recipientId={id}`: Verify caregiver assignment.
   - `POST /api/v1/bookings/{id}/deposit-paid`: Payment deposit confirmation.
   - `GET /api/v1/booking-occurrences/{id}`: Occurrence detail.
6. **MediaService (`/api/v1/media/**`)**:
   - `POST /api/v1/media`: Upload media file.
   - `GET /api/v1/media/{mediaFileId}`: Get media file metadata.
   - `GET /api/v1/media/{mediaFileId}/download-url`: Get presigned download URL.
   - `DELETE /api/v1/media/{mediaFileId}`: Delete media file.

### Feign Client Authentication Contract
Every Feign client uses `FeignAuthForwardingInterceptor`:
- Header: `Authorization: Bearer <JWT_TOKEN>` (extracted from SecurityContext / RequestContext).
- Header: `X-Request-Id: <MDC_REQUEST_ID>`.
- Target: `http://localhost:8080` (ApiGateway).

---

## Code Layout
Each service adheres strictly to:
```
mss301.<service>/
├── controller/        # Unified domain controllers (@RequestMapping("/api/v1/<aggregate>"))
├── service/           # 100% pure interfaces
│   └── Impl/          # 100% implementation classes (*ServiceImpl)
├── repository/        # 100% pure interfaces (*Repository)
├── entity/            # BaseEntity + JPA entities (@Getter @Setter, NO @Data)
├── mapper/            # MapStruct mappers (@Mapper(config = MapStructConfig.class))
├── client/            # OpenFeign clients calling ApiGateway with FeignAuthForwardingInterceptor
├── dto/
│   ├── request/
│   └── response/
├── config/
├── security/
└── exception/
```
