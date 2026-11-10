# Task Dispatch — Worker M2: ProviderService Architecture Refactoring

## 2026-10-18T15:45:00Z

### Mission
Execute comprehensive architecture refactoring across ProviderService per requirements R1, R2, R4, R5, satisfying 100% of the blueprints prepared by Explorers M2.1, M2.2, and M2.3:

1. **Unify Controllers by Domain Aggregate (R1 & R3)**:
   - Consolidate 11 controllers into 4 domain aggregate controllers + ProviderMediaController under `@RequestMapping("/api/v1/...")`:
     * `CareCenterController` (`/api/v1/care-centers/**`)
     * `CaregiverController` (`/api/v1/caregivers/**`)
     * `WorkShiftController` (`/api/v1/work-shifts/**`)
     * `CertificateController` (`/api/v1/certificates/**`)
     * `ProviderMediaController` (`/api/v1/provider-media/**`)
   - Delete the 8 obsolete controller files:
     * `AdminCareCenterController.java`
     * `CenterScheduleController.java`
     * `CaregiverManagementController.java`
     * `CaregiverWorkController.java`
     * `AdminVerificationController.java`
     * `ProviderCatalogController.java`
     * `InternalCaregiverController.java`
     * `InternalCenterController.java`
   - Expose public inter-service lookups on `CareCenterController` and `CaregiverController` with `@PreAuthorize("isAuthenticated()")`:
     * `GET /api/v1/care-centers/{centerId}`
     * `GET /api/v1/caregivers/{caregiverId}`
     * `GET /api/v1/caregivers/by-user/{userId}`
   - Add compatibility getters `public UUID getCenterId() { return id; }` to `CenterResponse.java` and `public UUID getCaregiverId() { return id; }` to `CaregiverResponse.java`.

2. **Interface & Implementation Layer Purity (R2)**:
   - Extract 6 pure interfaces into `mss301.providerservice.service`:
     * `CaregiverService`
     * `CaregiverScheduleService`
     * `CertificateService`
     * `IdentityAccountService`
     * `ShiftRegistrationService`
     * `WorkShiftService`
   - Implement the 6 service classes in `mss301.providerservice.service.Impl`:
     * `CaregiverServiceImpl`
     * `CaregiverScheduleServiceImpl`
     * `CertificateServiceImpl`
     * `IdentityAccountServiceImpl`
     * `ShiftRegistrationServiceImpl`
     * `WorkShiftServiceImpl`
   - Ensure 100% of services in `service/` are interfaces and 100% of implementations are in `service/Impl/`.

3. **Purge 100% of `/internal/**` Artifacts (R4)**:
   - Delete `InternalProviderService.java` and `InternalProviderServiceImpl.java`.
   - Delete `InternalSecurityConfig.java` and `InternalSecretFilter.java`.
   - Delete `InternalProviderMapper.java`, `InternalCaregiverResponse.java`, and `InternalCenterResponse.java`.
   - Delete `src/test/java/mss301/providerservice/InternalProviderApiTests.java`.
   - Remove `internalSecret` property from `ProviderProperties.java`, `application.yaml`, and `src/test/resources/application.yaml`.
   - Clean up `OpenApiConfig.java` to eliminate `INTERNAL_SCHEME`, `INTERNAL_PREFIX`, and `InternalSecretFilter` import.

4. **MapStruct Standardization (R5)**:
   - Convert `ProviderViewMapper.java` into an interface annotated with `@Mapper(config = MapStructConfig.class)`.
   - Implement complete mappings for all 5 methods (`caregiver`, `certificate`, `shift`, `registration`, `schedule`) satisfying `unmappedTargetPolicy = ReportingPolicy.ERROR`.

5. **Cross-Service OpenFeign Client Refactoring (R4)**:
   - Create `FeignAuthForwardingInterceptor.java` in `mss301.providerservice.client`.
   - Update `IdentityClient.java` to call `@GetMapping("/api/v1/users/{userId}")`.
   - Update `MediaClient.java` to call `/api/v1/media/**`.
   - Update `IdentityClientConfig.java` and `MediaClientConfig.java` to register `FeignAuthForwardingInterceptor` as `RequestInterceptor` bean.

6. **Test Suite Updates & Maven Verification**:
   - Update 5 unit test classes (`CaregiverServiceFlowTests`, `CertificateServiceTests`, `WorkShiftServiceTests`, `ShiftRegistrationServiceTests`, `IdentityAccountServiceTests`) to inject/instantiate `*ServiceImpl`.
   - Add `ProviderViewMapperTests.java` and `FeignAuthForwardingInterceptorTests.java`.
   - Run Maven Wrapper build & test:
     `cd d:\FA26\MSS\CareConnect\BE\ProviderService && .\mvnw.cmd clean compile && .\mvnw.cmd test`
   - Verify `BUILD SUCCESS` with 0 failures and 0 errors.

### Authoritative Reference Blueprints
- `d:\FA26\MSS\CareConnect\BE\.agents\teamwork\ORIGINAL_REQUEST.md`
- `d:\FA26\MSS\CareConnect\BE\.agents\teamwork\orchestrator_1\PROJECT.md`
- `d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_1\handoff.md` (Controller Blueprint)
- `d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_2\handoff.md` (Service Layer Blueprint)
- `d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_m2_3\handoff.md` (MapStruct & Feign Blueprint)

### Mandatory Output
Write your comprehensive implementation and verification report to:
`d:\FA26\MSS\CareConnect\BE\.agents\teamwork\worker_m2\handoff.md`
