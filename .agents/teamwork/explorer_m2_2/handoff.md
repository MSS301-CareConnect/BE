# Handoff Report — Explorer M2.2: ProviderService Service Layer Refactoring Blueprint

## 1. Observation

### 1.1. Existing Service Layer in ProviderService (Violation of R2)
Location: `d:\FA26\MSS\CareConnect\BE\ProviderService\src\main\java\mss301\providerservice\service\`

Direct observation reveals that 6 service files in the root `service/` package are declared as `public class` rather than `public interface`:

1. **`CaregiverService.java`** (`mss301.providerservice.service.CaregiverService`, 166 lines):
   - Line 22: `public class CaregiverService {`
   - Fields: `centerRepository`, `caregiverRepository`, `authorization`, `mapper`.
   - Public methods: `create`, `listManaged`, `get`, `myProfile`, `catalog`, `update`, `verify`, `archive`, `findByUser`, `find`.
   - References: Injected into `CaregiverScheduleService`, `CertificateService`, `ShiftRegistrationService`, `CaregiverManagementController`, `CaregiverWorkController`, `AdminVerificationController`, `ProviderCatalogController`, and `CaregiverServiceFlowTests`.

2. **`CaregiverScheduleService.java`** (`mss301.providerservice.service.CaregiverScheduleService`, 105 lines):
   - Line 26: `public class CaregiverScheduleService {`
   - Fields: `scheduleRepository`, `centerRepository`, `caregiverService`, `authorization`, `mapper`.
   - Public methods: `mySchedules`, `centerSchedules`, `updateStatus`.
   - References: Injected into `CenterScheduleController`, `CaregiverWorkController`.

3. **`CertificateService.java`** (`mss301.providerservice.service.CertificateService`, 126 lines):
   - Line 20: `public class CertificateService {`
   - Fields: `certificateRepository`, `caregiverService`, `authorization`, `mapper`.
   - Public methods: `create`, `list`, `verifiedList`, `update`, `verify`, `delete`.
   - References: Injected into `CertificateController`, `AdminVerificationController`, `ProviderCatalogController`, and `CertificateServiceTests`.

4. **`IdentityAccountService.java`** (`mss301.providerservice.service.IdentityAccountService`, 28 lines):
   - Line 14: `public class IdentityAccountService {`
   - Fields: `identityClient`.
   - Public method: `requireActiveRole(UUID userId, String requiredRole)`.
   - References: Injected into `AdminCareCenterController`, `CaregiverManagementController`, and `IdentityAccountServiceTests`.

5. **`ShiftRegistrationService.java`** (`mss301.providerservice.service.ShiftRegistrationService`, 168 lines):
   - Line 21: `public class ShiftRegistrationService {`
   - Fields: `shiftRepository`, `registrationRepository`, `scheduleRepository`, `caregiverService`, `authorization`, `mapper`.
   - Public methods: `register`, `listForShift`, `myRegistrations`, `review`, `cancelOwn`.
   - References: Injected into `WorkShiftController`, `CaregiverWorkController`, and `ShiftRegistrationServiceTests`.

6. **`WorkShiftService.java`** (`mss301.providerservice.service.WorkShiftService`, 158 lines):
   - Line 21: `public class WorkShiftService {`
   - Fields: `centerRepository`, `shiftRepository`, `registrationRepository`, `authorization`, `mapper`.
   - Public methods: `create`, `list`, `get`, `update`, `delete`, `find`, `approvedCount`.
   - References: Injected into `WorkShiftController` and `WorkShiftServiceTests`.

Only 2 services in `ProviderService` currently follow the interface + `service/Impl/` convention:
- `CareCenterService.java` (interface) + `Impl/CareCenterServiceImpl.java` (class)
- `ProviderMediaService.java` (interface) + `Impl/ProviderMediaServiceImpl.java` (class)

In addition, 1 internal service pair exists:
- `InternalProviderService.java` (interface) + `Impl/InternalProviderServiceImpl.java` (class)

### 1.2. Existing `/internal/**` Artifacts in ProviderService (Violation of R4)

Direct investigation identifies the following internal security, service, mapper, DTO, and test files:

1. **Services**:
   - `service/InternalProviderService.java` (Lines 1–17): Tra cứu chỉ đọc cho service khác (`getCaregiver`, `getCaregiverByUser`, `getCenter`).
   - `service/Impl/InternalProviderServiceImpl.java` (Lines 1–59): Hiện thực các method tra cứu.
2. **Controllers**:
   - `controller/InternalCaregiverController.java` (Lines 1–32): `@RequestMapping("/internal/caregivers")`.
   - `controller/InternalCenterController.java` (Lines 1–27): `@RequestMapping("/internal/centers")`.
3. **Security**:
   - `security/InternalSecretFilter.java` (Lines 1–62): `OncePerRequestFilter` inspecting `X-Internal-Secret`.
   - `security/InternalSecurityConfig.java` (Lines 1–51): `@Order(1)` filter chain matching `/internal/**`.
4. **Configuration & Documentation**:
   - `config/ProviderProperties.java` (Lines 17–18): `String internalSecret = "";`.
   - `resources/application.yaml` (Line 72): `internal-secret: ${PROVIDER_INTERNAL_SECRET:}`.
   - `config/swagger/OpenApiConfig.java`:
     - Line 14: `import mss301.providerservice.security.InternalSecretFilter;`
     - Line 29: `private static final String INTERNAL_SCHEME = "internalSecret";`
     - Line 30: `private static final String INTERNAL_PREFIX = "/internal/";`
     - Lines 55–59: `.addSecuritySchemes(INTERNAL_SCHEME, ...)`
     - Lines 89–94: Security requirement override for `/internal/**`.
5. **Mappers & DTOs**:
   - `mapper/InternalProviderMapper.java` (Lines 1–22): MapStruct mapper for internal responses.
   - `dto/response/InternalCaregiverResponse.java` (Lines 1–32)
   - `dto/response/InternalCenterResponse.java` (Lines 1–19)
6. **Tests**:
   - `src/test/java/mss301/providerservice/InternalProviderApiTests.java` (Lines 1–123): Tests `/internal/caregivers` and `/internal/centers`.
7. **Client Interceptor Dependency**:
   - `client/IdentityClientConfig.java` (Line 6: `import mss301.providerservice.security.InternalSecretFilter;`, Line 15: `template.header(InternalSecretFilter.HEADER, secret);`).
   - `client/MediaClientConfig.java` (Line 7: `import mss301.providerservice.security.InternalSecretFilter;`, Line 16: `template.header(InternalSecretFilter.HEADER, secret);`).

---

## 2. Logic Chain

```
Observation 1: 6 service classes in ProviderService are concrete classes directly in `service/` without interfaces.
Observation 2: Controllers and services in ProviderService declare dependencies using interface/class type names: `CaregiverService`, `CaregiverScheduleService`, `CertificateService`, `IdentityAccountService`, `ShiftRegistrationService`, `WorkShiftService`.
Logic Step 1: By extracting pure `interface` definitions into `service/` keeping the exact class name and package `mss301.providerservice.service`, all consuming controllers and services will automatically bind to the interface without changing field types or constructor signatures.
Logic Step 2: Implementation classes must be created in `service/Impl/` with the suffix `ServiceImpl`, annotated with `@Service`, `@RequiredArgsConstructor`, `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)`, and `@Slf4j`, implementing their corresponding interface.
Logic Step 3: Transactional boundaries (`@Transactional`, `@Transactional(readOnly = true)`) and private helper methods belong on the `*ServiceImpl` classes.
Conclusion 1: Extracting interfaces and placing implementations in `service/Impl/` strictly satisfies R2 and achieves 100% pure interface coverage in `service/`.
```

```
Observation 3: Unit tests in `src/test/java/mss301/providerservice/service/` use Mockito annotations:
  - `CaregiverServiceFlowTests`: `@InjectMocks CaregiverService service;`
  - `CertificateServiceTests`: `@InjectMocks CertificateService service;`
  - `ShiftRegistrationServiceTests`: `@InjectMocks ShiftRegistrationService service;`
  - `WorkShiftServiceTests`: `@InjectMocks WorkShiftService service;`
  - `IdentityAccountServiceTests`: `new IdentityAccountService(client);`
Logic Step 4: Mockito cannot instantiate interfaces with `@InjectMocks`. Attempting to `@InjectMocks` an interface causes `MockitoException: Cannot instantiate @InjectMocks field on type interface`.
Logic Step 5: Test files must update their `@InjectMocks` fields to target the concrete `*ServiceImpl` classes in `mss301.providerservice.service.Impl.*` while mock fields (e.g. `@Mock CaregiverService caregiverService`) continue mocking the interface.
Conclusion 2: Test `@InjectMocks` fields must be updated to reference `*ServiceImpl` classes to maintain green tests.
```

```
Observation 4: R4 mandates complete eradication of `/internal/**` endpoints, `InternalSecurityConfig`, `InternalSecretFilter`, and `X-Internal-Secret`.
Observation 5: `IdentityClientConfig.java` and `MediaClientConfig.java` import `mss301.providerservice.security.InternalSecretFilter` and call `InternalSecretFilter.HEADER`.
Logic Step 6: If `InternalSecretFilter.java` is deleted, `IdentityClientConfig.java` and `MediaClientConfig.java` will fail compilation due to unresolved symbol `InternalSecretFilter`.
Logic Step 7: Following the proven pattern from `CareRecipientService` (established in Milestone 1), `ProviderService` must introduce `FeignAuthForwardingInterceptor.java` in `client/` which extracts JWT Bearer token and MDC request ID, and wire it into `IdentityClientConfig` and `MediaClientConfig`.
Conclusion 3: Replacing `InternalSecretFilter` with `FeignAuthForwardingInterceptor` in Feign configs eliminates the compile break and prepares ProviderService for ApiGateway Bearer forwarding (R4).
```

---

## 3. Caveats

1. **Controller Consolidation Scope**: Controller consolidation (11 controllers to 4 aggregate controllers) is being handled by Explorer M2.1 / Refactorer M2. The service layer blueprint here provides 100% backward-compatible public interface contracts that support both current controllers and the consolidated aggregate controllers.
2. **ProviderViewMapper Standardization**: `ProviderViewMapper` is currently a `@Component` class using manual builder calls. Regardless of whether M2.1/Refactorer M2 converts it to MapStruct or retains a facade, all 6 `*ServiceImpl` classes interact with `ProviderViewMapper` through identical method calls (`mapper.caregiver(...)`, `mapper.certificate(...)`, etc.).
3. **Public Exposure of Internal Traversal APIs**: Inter-service lookups previously handled by `/internal/caregivers/**` and `/internal/centers/**` are being relocated to public `/api/v1/caregivers/**` and `/api/v1/care-centers/**` in the controller refactoring (handled in M2.1).
4. **Deleted Internal DTOs and Mappers**: When `InternalProviderService` and `InternalProviderServiceImpl` are deleted, `InternalProviderMapper.java`, `InternalCaregiverResponse.java`, and `InternalCenterResponse.java` become dead code and must be deleted. `InternalProviderApiTests.java` tests `/internal/**` and must also be removed.

---

## 4. Conclusion & Actionable Blueprint

### 4.1. File Action Matrix

| Source File | Destination File | Action | Details |
|---|---|---|---|
| `service/CaregiverService.java` | `service/CaregiverService.java` | **Replace with Interface** | Pure interface with 10 methods |
| New File | `service/Impl/CaregiverServiceImpl.java` | **Create Class** | Implements `CaregiverService` |
| `service/CaregiverScheduleService.java` | `service/CaregiverScheduleService.java` | **Replace with Interface** | Pure interface with 3 methods |
| New File | `service/Impl/CaregiverScheduleServiceImpl.java` | **Create Class** | Implements `CaregiverScheduleService` |
| `service/CertificateService.java` | `service/CertificateService.java` | **Replace with Interface** | Pure interface with 6 methods |
| New File | `service/Impl/CertificateServiceImpl.java` | **Create Class** | Implements `CertificateService` |
| `service/IdentityAccountService.java` | `service/IdentityAccountService.java` | **Replace with Interface** | Pure interface with 1 method |
| New File | `service/Impl/IdentityAccountServiceImpl.java` | **Create Class** | Implements `IdentityAccountService` |
| `service/ShiftRegistrationService.java` | `service/ShiftRegistrationService.java` | **Replace with Interface** | Pure interface with 5 methods |
| New File | `service/Impl/ShiftRegistrationServiceImpl.java` | **Create Class** | Implements `ShiftRegistrationService` |
| `service/WorkShiftService.java` | `service/WorkShiftService.java` | **Replace with Interface** | Pure interface with 7 methods |
| New File | `service/Impl/WorkShiftServiceImpl.java` | **Create Class** | Implements `WorkShiftService` |
| `service/InternalProviderService.java` | — | **DELETE** | Remove internal service interface |
| `service/Impl/InternalProviderServiceImpl.java` | — | **DELETE** | Remove internal service implementation |
| `security/InternalSecretFilter.java` | — | **DELETE** | Remove internal secret filter |
| `security/InternalSecurityConfig.java` | — | **DELETE** | Remove internal security filter chain |
| `mapper/InternalProviderMapper.java` | — | **DELETE** | Remove dead internal mapper |
| `dto/response/InternalCaregiverResponse.java` | — | **DELETE** | Remove dead internal DTO |
| `dto/response/InternalCenterResponse.java` | — | **DELETE** | Remove dead internal DTO |
| `src/test/.../InternalProviderApiTests.java` | — | **DELETE** | Remove obsolete /internal/ tests |
| `config/ProviderProperties.java` | `config/ProviderProperties.java` | **Modify** | Remove `internalSecret` property |
| `resources/application.yaml` | `resources/application.yaml` | **Modify** | Remove `provider.internal-secret` |
| `config/swagger/OpenApiConfig.java` | `config/swagger/OpenApiConfig.java` | **Modify** | Remove `INTERNAL_SCHEME` & `/internal/` config |
| New File | `client/FeignAuthForwardingInterceptor.java` | **Create Class** | JWT Bearer & RequestId forwarding |
| `client/IdentityClientConfig.java` | `client/IdentityClientConfig.java` | **Modify** | Wire `FeignAuthForwardingInterceptor` |
| `client/MediaClientConfig.java` | `client/MediaClientConfig.java` | **Modify** | Wire `FeignAuthForwardingInterceptor` |

---

### 4.2. Exact Code Blueprint: 6 Service Interfaces

#### 1. `mss301.providerservice.service.CaregiverService`
File: `ProviderService/src/main/java/mss301/providerservice/service/CaregiverService.java`

```java
package mss301.providerservice.service;

import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.CaregiverUpdateRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;

import java.util.UUID;

public interface CaregiverService {

    APIResponse<CaregiverResponse> create(UUID actorId, UUID centerId, CaregiverCreateRequest request);

    APIResponse<PageResponse<CaregiverResponse>> listManaged(UUID actorId, UUID centerId,
                                                            VerificationStatus verification,
                                                            CaregiverStatus status, int page, int size);

    APIResponse<CaregiverResponse> get(UUID actorId, UUID caregiverId);

    APIResponse<CaregiverResponse> myProfile(UUID actorId);

    APIResponse<PageResponse<CaregiverResponse>> catalog(UUID centerId, String keyword, int page, int size);

    APIResponse<CaregiverResponse> update(UUID actorId, UUID caregiverId, CaregiverUpdateRequest request);

    APIResponse<CaregiverResponse> verify(UUID actorId, UUID caregiverId, VerificationRequest request);

    APIResponse<Void> archive(UUID actorId, UUID caregiverId, Long version);

    CaregiverProfile findByUser(UUID userId);

    CaregiverProfile find(UUID id);
}
```

#### 2. `mss301.providerservice.service.CaregiverScheduleService`
File: `ProviderService/src/main/java/mss301/providerservice/service/CaregiverScheduleService.java`

```java
package mss301.providerservice.service;

import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;

import java.util.UUID;

public interface CaregiverScheduleService {

    APIResponse<PageResponse<ScheduleResponse>> mySchedules(UUID actorId, int page, int size);

    APIResponse<PageResponse<ScheduleResponse>> centerSchedules(UUID actorId, UUID centerId, int page, int size);

    APIResponse<ScheduleResponse> updateStatus(UUID actorId, UUID id, ScheduleStatusRequest request);
}
```

#### 3. `mss301.providerservice.service.CertificateService`
File: `ProviderService/src/main/java/mss301/providerservice/service/CertificateService.java`

```java
package mss301.providerservice.service;

import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificateResponse;

import java.util.List;
import java.util.UUID;

public interface CertificateService {

    APIResponse<CertificateResponse> create(UUID actorId, UUID caregiverId, CertificateRequest request);

    APIResponse<List<CertificateResponse>> list(UUID actorId, UUID caregiverId);

    APIResponse<List<CertificateResponse>> verifiedList(UUID caregiverId);

    APIResponse<CertificateResponse> update(UUID actorId, UUID id, CertificateRequest request);

    APIResponse<CertificateResponse> verify(UUID actorId, UUID id, VerificationRequest request);

    APIResponse<Void> delete(UUID actorId, UUID id, Long version);
}
```

#### 4. `mss301.providerservice.service.IdentityAccountService`
File: `ProviderService/src/main/java/mss301/providerservice/service/IdentityAccountService.java`

```java
package mss301.providerservice.service;

import mss301.providerservice.client.dto.IdentityUserResponse;

import java.util.UUID;

public interface IdentityAccountService {

    /** Gọi trước transaction ghi Provider DB. */
    IdentityUserResponse requireActiveRole(UUID userId, String requiredRole);
}
```

#### 5. `mss301.providerservice.service.ShiftRegistrationService`
File: `ProviderService/src/main/java/mss301/providerservice/service/ShiftRegistrationService.java`

```java
package mss301.providerservice.service;

import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;

import java.util.UUID;

public interface ShiftRegistrationService {

    APIResponse<ShiftRegistrationResponse> register(UUID actorId, UUID shiftId, ShiftRegistrationRequest request);

    APIResponse<PageResponse<ShiftRegistrationResponse>> listForShift(UUID actorId, UUID shiftId, int page, int size);

    APIResponse<PageResponse<ShiftRegistrationResponse>> myRegistrations(UUID actorId, int page, int size);

    APIResponse<ShiftRegistrationResponse> review(UUID actorId, UUID registrationId, RegistrationReviewRequest request);

    APIResponse<Void> cancelOwn(UUID actorId, UUID registrationId, Long version);
}
```

#### 6. `mss301.providerservice.service.WorkShiftService`
File: `ProviderService/src/main/java/mss301/providerservice/service/WorkShiftService.java`

```java
package mss301.providerservice.service;

import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.ShiftStatus;

import java.time.LocalDate;
import java.util.UUID;

public interface WorkShiftService {

    APIResponse<WorkShiftResponse> create(UUID actorId, UUID centerId, WorkShiftRequest request);

    APIResponse<PageResponse<WorkShiftResponse>> list(UUID centerId, ShiftStatus status,
                                                     LocalDate fromDate, LocalDate toDate,
                                                     int page, int size);

    APIResponse<WorkShiftResponse> get(UUID id);

    APIResponse<WorkShiftResponse> update(UUID actorId, UUID id, WorkShiftRequest request);

    APIResponse<Void> delete(UUID actorId, UUID id, Long version);

    WorkShift find(UUID id);

    long approvedCount(UUID shiftId);
}
```

---

### 4.3. Exact Code Blueprint: 6 Service Implementation Classes

#### 1. `mss301.providerservice.service.Impl.CaregiverServiceImpl`
File: `ProviderService/src/main/java/mss301/providerservice/service/Impl/CaregiverServiceImpl.java`

```java
package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.CaregiverUpdateRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverServiceImpl implements CaregiverService {

    static int MAX_PAGE_SIZE = 100;

    CareCenterRepository centerRepository;
    CaregiverProfileRepository caregiverRepository;
    ProviderAuthorization authorization;
    ProviderViewMapper mapper;

    @Override
    @Transactional
    public APIResponse<CaregiverResponse> create(UUID actorId, UUID centerId, CaregiverCreateRequest request) {
        CareCenter center = centerRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        authorization.requireCenterManagement(center, actorId);
        if (center.getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
        if (caregiverRepository.existsByUserIdAndDeletedAtIsNull(request.getUserId())) {
            throw new AppException(ErrorCode.CAREGIVER_USER_EXISTED);
        }
        CaregiverProfile caregiver = CaregiverProfile.builder()
                .userId(request.getUserId())
                .center(center)
                .fullName(request.getFullName().trim())
                .gender(request.getGender())
                .dateOfBirth(request.getDateOfBirth())
                .experienceYears(request.getExperienceYears())
                .bio(normalize(request.getBio()))
                .ratingAverage(BigDecimal.ZERO)
                .ratingCount(0)
                .verificationStatus(VerificationStatus.PENDING)
                .caregiverStatus(CaregiverStatus.INACTIVE)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
        return APIResponse.success("Tạo hồ sơ người chăm sóc thành công", mapper.caregiver(caregiverRepository.save(caregiver)));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CaregiverResponse>> listManaged(UUID actorId, UUID centerId,
                                                                    VerificationStatus verification,
                                                                    CaregiverStatus status, int page, int size) {
        validatePage(page, size);
        CareCenter center = centerRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        authorization.requireCenterManagement(center, actorId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CaregiverProfile> result;
        if (verification != null) {
            result = caregiverRepository.findByCenterIdAndVerificationStatusAndDeletedAtIsNull(centerId, verification, pageable);
        } else if (status != null) {
            result = caregiverRepository.findByCenterIdAndCaregiverStatusAndDeletedAtIsNull(centerId, status, pageable);
        } else {
            result = caregiverRepository.findByCenterIdAndDeletedAtIsNull(centerId, pageable);
        }
        return APIResponse.success("Lấy danh sách hồ sơ người chăm sóc thành công",
                PageResponse.of(result, result.stream().map(mapper::caregiver).toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverResponse> get(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = find(caregiverId);
        authorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        return APIResponse.success("Lấy hồ sơ người chăm sóc thành công", mapper.caregiver(caregiver));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverResponse> myProfile(UUID actorId) {
        return APIResponse.success("Lấy hồ sơ của tôi thành công", mapper.caregiver(findByUser(actorId)));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CaregiverResponse>> catalog(UUID centerId, String keyword, int page, int size) {
        validatePage(page, size);
        String normalizedKeyword = normalize(keyword);
        Page<CaregiverProfile> result = caregiverRepository.catalog(centerId, normalizedKeyword,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "ratingAverage")
                        .and(Sort.by(Sort.Direction.DESC, "experienceYears"))));
        return APIResponse.success("Lấy danh sách caregiver đang hoạt động thành công",
                PageResponse.of(result, result.stream().map(mapper::caregiver).toList()));
    }

    @Override
    @Transactional
    public APIResponse<CaregiverResponse> update(UUID actorId, UUID caregiverId, CaregiverUpdateRequest request) {
        CaregiverProfile caregiver = find(caregiverId);
        authorization.requireCenterManagement(caregiver.getCenter(), actorId);
        checkVersion(caregiver, request.getVersion());
        caregiver.setFullName(request.getFullName().trim());
        caregiver.setGender(request.getGender());
        caregiver.setDateOfBirth(request.getDateOfBirth());
        caregiver.setExperienceYears(request.getExperienceYears());
        caregiver.setBio(normalize(request.getBio()));
        caregiver.setCaregiverStatus(request.getCaregiverStatus());
        caregiver.setUpdatedBy(actorId);
        return APIResponse.success("Cập nhật hồ sơ người chăm sóc thành công",
                mapper.caregiver(caregiverRepository.save(caregiver)));
    }

    @Override
    @Transactional
    public APIResponse<CaregiverResponse> verify(UUID actorId, UUID caregiverId, VerificationRequest request) {
        CaregiverProfile caregiver = find(caregiverId);
        checkVersion(caregiver, request.getVersion());
        if (request.getStatus() == VerificationStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getStatus() == VerificationStatus.REJECTED && isBlank(request.getReason())) {
            throw new AppException(ErrorCode.VERIFICATION_REASON_REQUIRED);
        }
        caregiver.setVerificationStatus(request.getStatus());
        caregiver.setVerificationReason(normalize(request.getReason()));
        caregiver.setVerifiedBy(actorId);
        caregiver.setVerifiedAt(Instant.now());
        caregiver.setUpdatedBy(actorId);
        caregiver.setCaregiverStatus(request.getStatus() == VerificationStatus.VERIFIED
                ? CaregiverStatus.ACTIVE : CaregiverStatus.INACTIVE);
        return APIResponse.success("Xác minh hồ sơ người chăm sóc thành công",
                mapper.caregiver(caregiverRepository.save(caregiver)));
    }

    @Override
    @Transactional
    public APIResponse<Void> archive(UUID actorId, UUID caregiverId, Long version) {
        CaregiverProfile caregiver = find(caregiverId);
        authorization.requireCenterManagement(caregiver.getCenter(), actorId);
        checkVersion(caregiver, version);
        caregiver.setCaregiverStatus(CaregiverStatus.ARCHIVED);
        caregiver.setDeletedAt(Instant.now());
        caregiver.setUpdatedBy(actorId);
        caregiverRepository.save(caregiver);
        return APIResponse.success("Lưu trữ hồ sơ người chăm sóc thành công");
    }

    @Override
    public CaregiverProfile findByUser(UUID userId) {
        return caregiverRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
    }

    @Override
    public CaregiverProfile find(UUID id) {
        return caregiverRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
    }

    private void checkVersion(CaregiverProfile caregiver, Long version) {
        if (version == null || !version.equals(caregiver.getVersion())) {
            throw new AppException(ErrorCode.CAREGIVER_VERSION_CONFLICT);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

---

#### 2. `mss301.providerservice.service.Impl.CaregiverScheduleServiceImpl`
File: `ProviderService/src/main/java/mss301/providerservice/service/Impl/CaregiverScheduleServiceImpl.java`

```java
package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverScheduleService;
import mss301.providerservice.service.CaregiverService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverScheduleServiceImpl implements CaregiverScheduleService {

    static int MAX_PAGE_SIZE = 100;

    CaregiverScheduleRepository scheduleRepository;
    CareCenterRepository centerRepository;
    CaregiverService caregiverService;
    ProviderAuthorization authorization;
    ProviderViewMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ScheduleResponse>> mySchedules(UUID actorId, int page, int size) {
        validatePage(page, size);
        CaregiverProfile caregiver = caregiverService.findByUser(actorId);
        Page<CaregiverSchedule> result = scheduleRepository.findByCaregiverIdAndDeletedAtIsNull(
                caregiver.getId(), PageRequest.of(page, size, Sort.by("workShift.workDate").ascending()));
        return APIResponse.success("Lấy lịch làm việc của tôi thành công",
                PageResponse.of(result, result.stream().map(mapper::schedule).toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ScheduleResponse>> centerSchedules(UUID actorId, UUID centerId, int page, int size) {
        validatePage(page, size);
        CareCenter center = centerRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        authorization.requireCenterManagement(center, actorId);
        Page<CaregiverSchedule> result = scheduleRepository.findByWorkShiftCenterIdAndDeletedAtIsNull(
                centerId, PageRequest.of(page, size, Sort.by("workShift.workDate").ascending()));
        return APIResponse.success("Lấy lịch người chăm sóc của trung tâm thành công",
                PageResponse.of(result, result.stream().map(mapper::schedule).toList()));
    }

    @Override
    @Transactional
    public APIResponse<ScheduleResponse> updateStatus(UUID actorId, UUID id, ScheduleStatusRequest request) {
        CaregiverSchedule schedule = scheduleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_FOUND));
        authorization.requireCaregiverSelfOrCenterManagement(schedule.getCaregiver(), actorId);
        if (request.getVersion() == null || !request.getVersion().equals(schedule.getVersion())) {
            throw new AppException(ErrorCode.SCHEDULE_VERSION_CONFLICT);
        }
        if (!allowedTransition(schedule.getScheduleStatus(), request.getStatus())) {
            throw new AppException(ErrorCode.SCHEDULE_STATUS_INVALID);
        }
        if (request.getStatus() == ScheduleStatus.CANCELLED
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new AppException(ErrorCode.SCHEDULE_CANCELLATION_REASON_REQUIRED);
        }
        schedule.setScheduleStatus(request.getStatus());
        if (request.getStatus() == ScheduleStatus.CANCELLED) {
            schedule.setCancellationReason(request.getReason().trim());
            schedule.setCancelledAt(Instant.now());
            schedule.getRegistration().setRegistrationStatus(RegistrationStatus.CANCELLED);
            reopenShiftWhenPossible(schedule.getWorkShift());
        }
        return APIResponse.success("Cập nhật trạng thái lịch thành công",
                mapper.schedule(scheduleRepository.save(schedule)));
    }

    private void reopenShiftWhenPossible(WorkShift shift) {
        Instant now = Instant.now();
        boolean shiftInFuture = LocalDateTime.of(shift.getWorkDate(), shift.getStartTime())
                .toInstant(ZoneOffset.UTC).isAfter(now);
        boolean registrationStillAllowed = shift.getRegistrationDeadline() == null
                || now.isBefore(shift.getRegistrationDeadline());
        if (shift.getShiftStatus() == ShiftStatus.CLOSED && shiftInFuture && registrationStillAllowed) {
            shift.setShiftStatus(ShiftStatus.OPEN);
        }
    }

    private boolean allowedTransition(ScheduleStatus from, ScheduleStatus to) {
        return (from == ScheduleStatus.SCHEDULED
                && (to == ScheduleStatus.IN_PROGRESS || to == ScheduleStatus.CANCELLED))
                || (from == ScheduleStatus.IN_PROGRESS
                && (to == ScheduleStatus.COMPLETED || to == ScheduleStatus.CANCELLED));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }
}
```

---

#### 3. `mss301.providerservice.service.Impl.CertificateServiceImpl`
File: `ProviderService/src/main/java/mss301/providerservice/service/Impl/CertificateServiceImpl.java`

```java
package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.CertificateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CertificateServiceImpl implements CertificateService {

    CertificateRepository certificateRepository;
    CaregiverService caregiverService;
    ProviderAuthorization authorization;
    ProviderViewMapper mapper;

    @Override
    @Transactional
    public APIResponse<CertificateResponse> create(UUID actorId, UUID caregiverId, CertificateRequest request) {
        CaregiverProfile caregiver = caregiverService.find(caregiverId);
        authorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        validateDates(request);
        Certificate certificate = Certificate.builder()
                .caregiver(caregiver)
                .certificateName(request.getCertificateName().trim())
                .issuedBy(request.getIssuedBy().trim())
                .credentialNumber(normalize(request.getCredentialNumber()))
                .issuedDate(request.getIssuedDate())
                .expiryDate(request.getExpiryDate())
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        return APIResponse.success("Tạo chứng chỉ thành công", mapper.certificate(certificateRepository.save(certificate)));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CertificateResponse>> list(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = caregiverService.find(caregiverId);
        authorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        return APIResponse.success("Lấy danh sách chứng chỉ thành công",
                certificateRepository.findByCaregiverIdAndDeletedAtIsNullOrderByIssuedDateDesc(caregiverId)
                        .stream().map(mapper::certificate).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CertificateResponse>> verifiedList(UUID caregiverId) {
        CaregiverProfile caregiver = caregiverService.find(caregiverId);
        if (caregiver.getVerificationStatus() != VerificationStatus.VERIFIED
                || caregiver.getCaregiverStatus() != CaregiverStatus.ACTIVE) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_FOUND);
        }
        return APIResponse.success("Lấy chứng chỉ đã xác minh thành công",
                certificateRepository.findByCaregiverIdAndVerificationStatusAndDeletedAtIsNullOrderByIssuedDateDesc(
                        caregiverId, VerificationStatus.VERIFIED).stream().map(mapper::certificate).toList());
    }

    @Override
    @Transactional
    public APIResponse<CertificateResponse> update(UUID actorId, UUID id, CertificateRequest request) {
        Certificate certificate = find(id);
        authorization.requireCaregiverSelfOrCenterManagement(certificate.getCaregiver(), actorId);
        checkVersion(certificate, request.getVersion());
        validateDates(request);
        certificate.setCertificateName(request.getCertificateName().trim());
        certificate.setIssuedBy(request.getIssuedBy().trim());
        certificate.setCredentialNumber(normalize(request.getCredentialNumber()));
        certificate.setIssuedDate(request.getIssuedDate());
        certificate.setExpiryDate(request.getExpiryDate());
        certificate.setVerificationStatus(VerificationStatus.PENDING);
        certificate.setVerificationReason(null);
        certificate.setVerifiedAt(null);
        certificate.setVerifiedBy(null);
        return APIResponse.success("Cập nhật chứng chỉ thành công",
                mapper.certificate(certificateRepository.save(certificate)));
    }

    @Override
    @Transactional
    public APIResponse<CertificateResponse> verify(UUID actorId, UUID id, VerificationRequest request) {
        Certificate certificate = find(id);
        checkVersion(certificate, request.getVersion());
        if (request.getStatus() == VerificationStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getStatus() == VerificationStatus.REJECTED && isBlank(request.getReason())) {
            throw new AppException(ErrorCode.VERIFICATION_REASON_REQUIRED);
        }
        certificate.setVerificationStatus(request.getStatus());
        certificate.setVerificationReason(normalize(request.getReason()));
        certificate.setVerifiedBy(actorId);
        certificate.setVerifiedAt(Instant.now());
        return APIResponse.success("Xác minh chứng chỉ thành công",
                mapper.certificate(certificateRepository.save(certificate)));
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        Certificate certificate = find(id);
        authorization.requireCaregiverSelfOrCenterManagement(certificate.getCaregiver(), actorId);
        checkVersion(certificate, version);
        certificate.setDeletedAt(Instant.now());
        certificateRepository.save(certificate);
        return APIResponse.success("Xóa chứng chỉ thành công");
    }

    private Certificate find(UUID id) {
        return certificateRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));
    }

    private void validateDates(CertificateRequest request) {
        if (request.getIssuedDate() != null && request.getExpiryDate() != null
                && request.getExpiryDate().isBefore(request.getIssuedDate())) {
            throw new AppException(ErrorCode.CERTIFICATE_DATE_INVALID);
        }
    }

    private void checkVersion(Certificate value, Long version) {
        if (version == null || !version.equals(value.getVersion())) {
            throw new AppException(ErrorCode.CERTIFICATE_VERSION_CONFLICT);
        }
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

---

#### 4. `mss301.providerservice.service.Impl.IdentityAccountServiceImpl`
File: `ProviderService/src/main/java/mss301/providerservice/service/Impl/IdentityAccountServiceImpl.java`

```java
package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.IdentityClient;
import mss301.providerservice.client.dto.IdentityUserResponse;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.service.IdentityAccountService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class IdentityAccountServiceImpl implements IdentityAccountService {

    IdentityClient identityClient;

    /** Gọi trước transaction ghi Provider DB. */
    @Override
    public IdentityUserResponse requireActiveRole(UUID userId, String requiredRole) {
        APIResponse<IdentityUserResponse> response = identityClient.get(userId);
        IdentityUserResponse user = response == null ? null : response.getResult();
        if (user == null) {
            throw new AppException(ErrorCode.IDENTITY_USER_NOT_FOUND);
        }
        boolean active = "ACTIVE".equals(user.getStatus());
        boolean hasRole = user.getRoles() != null && user.getRoles().contains(requiredRole);
        if (!active || !hasRole) {
            throw new AppException(ErrorCode.IDENTITY_USER_INVALID);
        }
        return user;
    }
}
```

---

#### 5. `mss301.providerservice.service.Impl.ShiftRegistrationServiceImpl`
File: `ProviderService/src/main/java/mss301/providerservice/service/Impl/ShiftRegistrationServiceImpl.java`

```java
package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.ShiftRegistrationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ShiftRegistrationServiceImpl implements ShiftRegistrationService {

    static int MAX_PAGE_SIZE = 100;
    static Set<ScheduleStatus> ACTIVE_SCHEDULES = EnumSet.of(ScheduleStatus.SCHEDULED, ScheduleStatus.IN_PROGRESS);

    WorkShiftRepository shiftRepository;
    ShiftRegistrationRepository registrationRepository;
    CaregiverScheduleRepository scheduleRepository;
    CaregiverService caregiverService;
    ProviderAuthorization authorization;
    ProviderViewMapper mapper;

    @Override
    @Transactional
    public APIResponse<ShiftRegistrationResponse> register(UUID actorId, UUID shiftId,
                                                            ShiftRegistrationRequest request) {
        CaregiverProfile caregiver = caregiverService.findByUser(actorId);
        if (caregiver.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_VERIFIED);
        }
        if (caregiver.getCaregiverStatus() != CaregiverStatus.ACTIVE) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_ACTIVE);
        }
        WorkShift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        if (!caregiver.getCenter().getId().equals(shift.getCenter().getId())) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
        if (shift.getShiftStatus() != ShiftStatus.OPEN) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOT_OPEN);
        }
        if (shift.getRegistrationDeadline() != null && !Instant.now().isBefore(shift.getRegistrationDeadline())) {
            throw new AppException(ErrorCode.REGISTRATION_DEADLINE_PASSED);
        }
        if (registrationRepository.existsByWorkShiftIdAndCaregiverId(shiftId, caregiver.getId())) {
            throw new AppException(ErrorCode.REGISTRATION_EXISTED);
        }
        if (approvedCount(shiftId) >= shift.getCapacity()) {
            throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        }
        ShiftRegistration registration = ShiftRegistration.builder()
                .workShift(shift)
                .caregiver(caregiver)
                .registrationStatus(RegistrationStatus.PENDING)
                .caregiverNote(normalize(request.getCaregiverNote()))
                .build();
        return APIResponse.success("Đăng ký ca làm việc thành công",
                mapper.registration(registrationRepository.save(registration)));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listForShift(UUID actorId, UUID shiftId,
                                                                              int page, int size) {
        validatePage(page, size);
        WorkShift shift = shiftRepository.findByIdAndDeletedAtIsNull(shiftId)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        authorization.requireCenterManagement(shift.getCenter(), actorId);
        Page<ShiftRegistration> result = registrationRepository.findByWorkShiftIdAndDeletedAtIsNull(
                shiftId, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));
        return APIResponse.success("Lấy danh sách đăng ký ca thành công",
                PageResponse.of(result, result.stream().map(mapper::registration).toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ShiftRegistrationResponse>> myRegistrations(UUID actorId, int page, int size) {
        validatePage(page, size);
        CaregiverProfile caregiver = caregiverService.findByUser(actorId);
        Page<ShiftRegistration> result = registrationRepository.findByCaregiverIdAndDeletedAtIsNull(
                caregiver.getId(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return APIResponse.success("Lấy đăng ký ca của tôi thành công",
                PageResponse.of(result, result.stream().map(mapper::registration).toList()));
    }

    @Override
    @Transactional
    public APIResponse<ShiftRegistrationResponse> review(UUID actorId, UUID registrationId,
                                                          RegistrationReviewRequest request) {
        ShiftRegistration registration = registrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
        WorkShift shift = shiftRepository.findByIdForUpdate(registration.getWorkShift().getId())
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        authorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(registration, request.getVersion());
        if (registration.getRegistrationStatus() != RegistrationStatus.PENDING) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_PENDING);
        }
        if (request.getStatus() != RegistrationStatus.APPROVED
                && request.getStatus() != RegistrationStatus.REJECTED) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getStatus() == RegistrationStatus.REJECTED && isBlank(request.getReason())) {
            throw new AppException(ErrorCode.REGISTRATION_REJECTION_REASON_REQUIRED);
        }
        if (request.getStatus() == RegistrationStatus.APPROVED) {
            approve(actorId, registration, shift, request.getScheduleNote());
        } else {
            registration.setRegistrationStatus(RegistrationStatus.REJECTED);
            registration.setRejectionReason(request.getReason().trim());
        }
        registration.setReviewedBy(actorId);
        registration.setReviewedAt(Instant.now());
        return APIResponse.success("Xử lý đăng ký ca thành công",
                mapper.registration(registrationRepository.save(registration)));
    }

    @Override
    @Transactional
    public APIResponse<Void> cancelOwn(UUID actorId, UUID registrationId, Long version) {
        CaregiverProfile caregiver = caregiverService.findByUser(actorId);
        ShiftRegistration registration = registrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
        if (!caregiver.getId().equals(registration.getCaregiver().getId())) {
            throw new AppException(ErrorCode.CAREGIVER_ACCESS_DENIED);
        }
        checkVersion(registration, version);
        if (registration.getRegistrationStatus() != RegistrationStatus.PENDING) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_PENDING);
        }
        registration.setRegistrationStatus(RegistrationStatus.CANCELLED);
        registrationRepository.save(registration);
        return APIResponse.success("Hủy đăng ký ca thành công");
    }

    private void approve(UUID actorId, ShiftRegistration registration, WorkShift shift, String note) {
        if (shift.getShiftStatus() != ShiftStatus.OPEN && shift.getShiftStatus() != ShiftStatus.CLOSED) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOT_OPEN);
        }
        if (approvedCount(shift.getId()) >= shift.getCapacity()) {
            throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        }
        CaregiverProfile caregiver = registration.getCaregiver();
        boolean overlap = scheduleRepository.hasOverlap(caregiver.getId(), shift.getWorkDate(),
                shift.getStartTime(), shift.getEndTime(), ACTIVE_SCHEDULES);
        if (overlap) {
            throw new AppException(ErrorCode.SCHEDULE_CONFLICT);
        }
        registration.setRegistrationStatus(RegistrationStatus.APPROVED);
        CaregiverSchedule schedule = CaregiverSchedule.builder()
                .registration(registration)
                .caregiver(caregiver)
                .workShift(shift)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .assignedBy(actorId)
                .assignedAt(Instant.now())
                .note(normalize(note))
                .build();
        scheduleRepository.save(schedule);
        if (approvedCount(shift.getId()) + 1 >= shift.getCapacity()) {
            shift.setShiftStatus(ShiftStatus.CLOSED);
            shiftRepository.save(shift);
        }
    }

    private long approvedCount(UUID shiftId) {
        return registrationRepository.countByWorkShiftIdAndRegistrationStatus(shiftId, RegistrationStatus.APPROVED);
    }

    private void checkVersion(ShiftRegistration value, Long version) {
        if (version == null || !version.equals(value.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
```

---

#### 6. `mss301.providerservice.service.Impl.WorkShiftServiceImpl`
File: `ProviderService/src/main/java/mss301/providerservice/service/Impl/WorkShiftServiceImpl.java`

```java
package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.WorkShiftService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WorkShiftServiceImpl implements WorkShiftService {

    static int MAX_PAGE_SIZE = 100;

    CareCenterRepository centerRepository;
    WorkShiftRepository shiftRepository;
    ShiftRegistrationRepository registrationRepository;
    ProviderAuthorization authorization;
    ProviderViewMapper mapper;

    @Override
    @Transactional
    public APIResponse<WorkShiftResponse> create(UUID actorId, UUID centerId, WorkShiftRequest request) {
        CareCenter center = centerRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        authorization.requireCenterManagement(center, actorId);
        if (center.getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
        validate(request, false);
        WorkShift shift = WorkShift.builder()
                .center(center)
                .workDate(request.getWorkDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .shiftType(request.getShiftType().trim().toUpperCase(Locale.ROOT))
                .shiftStatus(request.getShiftStatus())
                .capacity(request.getCapacity())
                .registrationDeadline(request.getRegistrationDeadline())
                .note(normalize(request.getNote()))
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
        WorkShift saved = shiftRepository.save(shift);
        return APIResponse.success("Tạo ca làm việc thành công", mapper.shift(saved, 0));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<WorkShiftResponse>> list(UUID centerId, ShiftStatus status,
                                                             LocalDate fromDate, LocalDate toDate,
                                                             int page, int size) {
        validatePage(page, size);
        Page<WorkShift> result = shiftRepository.search(centerId, status, fromDate, toDate,
                PageRequest.of(page, size, Sort.by("workDate").ascending().and(Sort.by("startTime").ascending())));
        List<WorkShiftResponse> items = result.stream().map(this::toResponse).toList();
        return APIResponse.success("Lấy danh sách ca làm việc thành công", PageResponse.of(result, items));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WorkShiftResponse> get(UUID id) {
        return APIResponse.success("Lấy ca làm việc thành công", toResponse(find(id)));
    }

    @Override
    @Transactional
    public APIResponse<WorkShiftResponse> update(UUID actorId, UUID id, WorkShiftRequest request) {
        WorkShift shift = find(id);
        authorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(shift, request.getVersion());
        long approved = approvedCount(id);
        validateStatusTransition(shift, request.getShiftStatus(), approved);
        if (approved > 0 && scheduleFieldsChanged(shift, request)) {
            throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
        }
        validate(request, true);
        if (request.getCapacity() < approved) {
            throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        }
        shift.setWorkDate(request.getWorkDate());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setShiftType(request.getShiftType().trim().toUpperCase(Locale.ROOT));
        shift.setShiftStatus(request.getShiftStatus());
        shift.setCapacity(request.getCapacity());
        shift.setRegistrationDeadline(request.getRegistrationDeadline());
        shift.setNote(normalize(request.getNote()));
        shift.setUpdatedBy(actorId);
        WorkShift saved = shiftRepository.save(shift);
        return APIResponse.success("Cập nhật ca làm việc thành công", mapper.shift(saved, approved));
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        WorkShift shift = find(id);
        authorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(shift, version);
        if (approvedCount(id) > 0) {
            throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
        }
        shift.setShiftStatus(ShiftStatus.CANCELLED);
        shift.setDeletedAt(Instant.now());
        shift.setUpdatedBy(actorId);
        shiftRepository.save(shift);
        return APIResponse.success("Hủy ca làm việc thành công");
    }

    @Override
    public WorkShift find(UUID id) {
        return shiftRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
    }

    @Override
    public long approvedCount(UUID shiftId) {
        return registrationRepository.countByWorkShiftIdAndRegistrationStatus(shiftId, RegistrationStatus.APPROVED);
    }

    private WorkShiftResponse toResponse(WorkShift shift) {
        return mapper.shift(shift, approvedCount(shift.getId()));
    }

    private void validate(WorkShiftRequest request, boolean update) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new AppException(ErrorCode.WORK_SHIFT_TIME_INVALID);
        }
        LocalDateTime shiftStart = LocalDateTime.of(request.getWorkDate(), request.getStartTime());
        if (!update && !shiftStart.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new AppException(ErrorCode.WORK_SHIFT_DATE_INVALID);
        }
        if (request.getRegistrationDeadline() != null
                && !request.getRegistrationDeadline().isBefore(shiftStart.toInstant(ZoneOffset.UTC))) {
            throw new AppException(ErrorCode.WORK_SHIFT_DATE_INVALID);
        }
        if (!update && request.getShiftStatus() != ShiftStatus.DRAFT && request.getShiftStatus() != ShiftStatus.OPEN) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }

    private boolean scheduleFieldsChanged(WorkShift shift, WorkShiftRequest request) {
        return !shift.getWorkDate().equals(request.getWorkDate())
                || !shift.getStartTime().equals(request.getStartTime())
                || !shift.getEndTime().equals(request.getEndTime());
    }

    private void validateStatusTransition(WorkShift shift, ShiftStatus target, long approved) {
        ShiftStatus current = shift.getShiftStatus();
        if ((current == ShiftStatus.CANCELLED || current == ShiftStatus.COMPLETED) && target != current) {
            throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
        }
        if (target == ShiftStatus.CANCELLED && approved > 0) {
            throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
        }
        if (target == ShiftStatus.COMPLETED) {
            Instant end = LocalDateTime.of(shift.getWorkDate(), shift.getEndTime()).toInstant(ZoneOffset.UTC);
            if (end.isAfter(Instant.now())) {
                throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
            }
        }
    }

    private void checkVersion(WorkShift value, Long version) {
        if (version == null || !version.equals(value.getVersion())) {
            throw new AppException(ErrorCode.WORK_SHIFT_VERSION_CONFLICT);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```

---

### 4.4. Deletion of `/internal/**` Artifacts & Security Adjustments

#### 1. Files to Delete:
- `ProviderService/src/main/java/mss301/providerservice/service/InternalProviderService.java`
- `ProviderService/src/main/java/mss301/providerservice/service/Impl/InternalProviderServiceImpl.java`
- `ProviderService/src/main/java/mss301/providerservice/security/InternalSecurityConfig.java`
- `ProviderService/src/main/java/mss301/providerservice/security/InternalSecretFilter.java`
- `ProviderService/src/main/java/mss301/providerservice/mapper/InternalProviderMapper.java`
- `ProviderService/src/main/java/mss301/providerservice/dto/response/InternalCaregiverResponse.java`
- `ProviderService/src/main/java/mss301/providerservice/dto/response/InternalCenterResponse.java`
- `ProviderService/src/test/java/mss301/providerservice/InternalProviderApiTests.java`

#### 2. Diff for `ProviderProperties.java`
File: `ProviderService/src/main/java/mss301/providerservice/config/ProviderProperties.java`

```diff
--- a/ProviderService/src/main/java/mss301/providerservice/config/ProviderProperties.java
+++ b/ProviderService/src/main/java/mss301/providerservice/config/ProviderProperties.java
@@ -14,8 +14,6 @@
 @ConfigurationProperties("provider")
 @FieldDefaults(level = AccessLevel.PRIVATE)
 public class ProviderProperties {
-    @ToString.Exclude
-    String internalSecret = "";
     String openApiServerUrl = "";
 
     @Valid
```

#### 3. Diff for `application.yaml`
File: `ProviderService/src/main/resources/application.yaml`

```diff
--- a/ProviderService/src/main/resources/application.yaml
+++ b/ProviderService/src/main/resources/application.yaml
@@ -69,7 +69,6 @@
     correlation: "[%X{requestId:-}] "
 
 provider:
-  internal-secret: ${PROVIDER_INTERNAL_SECRET:}
   open-api-server-url: ${PROVIDER_OPENAPI_SERVER_URL:}
 
   jwt:
```

#### 4. Diff for `OpenApiConfig.java`
File: `ProviderService/src/main/java/mss301/providerservice/config/swagger/OpenApiConfig.java`

```diff
--- a/ProviderService/src/main/java/mss301/providerservice/config/swagger/OpenApiConfig.java
+++ b/ProviderService/src/main/java/mss301/providerservice/config/swagger/OpenApiConfig.java
@@ -11,7 +11,6 @@
 import io.swagger.v3.oas.models.servers.Server;
 import mss301.providerservice.config.ProviderProperties;
 import mss301.providerservice.security.CurrentUserId;
-import mss301.providerservice.security.InternalSecretFilter;
 import mss301.providerservice.security.ReadOnlyCookieCsrfTokenRepository;
 import org.springdoc.core.customizers.OpenApiCustomizer;
 import org.springdoc.core.utils.SpringDocUtils;
@@ -26,8 +25,6 @@
 
     private static final String BEARER_SCHEME = "bearerAuth";
     private static final String CSRF_SCHEME = "csrf";
-    private static final String INTERNAL_SCHEME = "internalSecret";
-    private static final String INTERNAL_PREFIX = "/internal/";
     private static final String CSRF_DESCRIPTION = "Chỉ cần khi gọi bằng cookie access_token (web qua gateway): "
             + "dán token lấy từ GET /auth/csrf của IdentityService. Gọi bằng Bearer thuần không cần.";
     private static final Set<PathItem.HttpMethod> WRITE_METHODS = Set.of(
@@ -52,11 +49,7 @@
                 .addSecuritySchemes(CSRF_SCHEME, new SecurityScheme()
                         .type(SecurityScheme.Type.APIKEY)
                         .in(SecurityScheme.In.HEADER)
                         .name(ReadOnlyCookieCsrfTokenRepository.CSRF_HEADER)
-                        .description(CSRF_DESCRIPTION))
-                .addSecuritySchemes(INTERNAL_SCHEME, new SecurityScheme()
-                        .type(SecurityScheme.Type.APIKEY)
-                        .in(SecurityScheme.In.HEADER)
-                        .name(InternalSecretFilter.HEADER)
-                        .description("Chỉ cho /internal/** (service-to-service): giá trị PROVIDER_INTERNAL_SECRET."));
+                        .description(CSRF_DESCRIPTION));
         for (String code : DOCUMENTED_RESPONSES) {
             components.addResponses(code, new ApiResponse().description("HTTP " + code + "; business endpoints return APIResponse"));
         }
@@ -87,11 +80,6 @@
     @Bean
     public OpenApiCustomizer securityRequirements() {
         return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
-            if (path.startsWith(INTERNAL_PREFIX)) {
-                pathItem.readOperations().forEach(operation -> operation.setSecurity(
-                        List.of(new SecurityRequirement().addList(INTERNAL_SCHEME))));
-                return;
-            }
             pathItem.readOperationsMap().forEach((method, operation) -> {
                 if (WRITE_METHODS.contains(method)) {
                     addCsrfRequirement(operation);
```

---

### 4.5. Feign Client Authorization Forwarding (Resolving `InternalSecretFilter` Breakage)

When `InternalSecretFilter.java` is deleted, `IdentityClientConfig.java` and `MediaClientConfig.java` must be decoupled from `InternalSecretFilter.HEADER`. Following the standard implemented in `CareRecipientService`:

#### New File: `FeignAuthForwardingInterceptor.java`
File: `ProviderService/src/main/java/mss301/providerservice/client/FeignAuthForwardingInterceptor.java`

```java
package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import mss301.providerservice.config.RequestIdFilter;
import mss301.providerservice.security.CookieUtils;
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

#### Updated `IdentityClientConfig.java`
File: `ProviderService/src/main/java/mss301/providerservice/client/IdentityClientConfig.java`

```java
package mss301.providerservice.client;

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

#### Updated `MediaClientConfig.java`
File: `ProviderService/src/main/java/mss301/providerservice/client/MediaClientConfig.java`

```java
package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class MediaClientConfig {

    @Bean
    public RequestInterceptor mediaRequestInterceptor() {
        return new FeignAuthForwardingInterceptor();
    }

    @Bean
    public ErrorDecoder mediaErrorDecoder() {
        return new MediaClientErrorDecoder();
    }
}
```

---

### 4.6. Test Adjustments for Interface-Impl Extraction

#### 1. `CaregiverServiceFlowTests.java`
File: `ProviderService/src/test/java/mss301/providerservice/service/CaregiverServiceFlowTests.java`

```diff
--- a/ProviderService/src/test/java/mss301/providerservice/service/CaregiverServiceFlowTests.java
+++ b/ProviderService/src/test/java/mss301/providerservice/service/CaregiverServiceFlowTests.java
@@ -6,6 +6,7 @@
 import mss301.providerservice.mapper.ProviderViewMapper;
 import mss301.providerservice.repository.*;
 import mss301.providerservice.security.ProviderAuthorization;
+import mss301.providerservice.service.Impl.CaregiverServiceImpl;
 import org.junit.jupiter.api.Test;
 import org.junit.jupiter.api.extension.ExtendWith;
 import org.mockito.*;
@@ -22,7 +23,7 @@
     @Mock CareCenterRepository centerRepository;
     @Mock CaregiverProfileRepository caregiverRepository;
     @Mock ProviderAuthorization authorization;
     @Mock ProviderViewMapper mapper;
-    @InjectMocks CaregiverService service;
+    @InjectMocks CaregiverServiceImpl service;
```

#### 2. `CertificateServiceTests.java`
File: `ProviderService/src/test/java/mss301/providerservice/service/CertificateServiceTests.java`

```diff
--- a/ProviderService/src/test/java/mss301/providerservice/service/CertificateServiceTests.java
+++ b/ProviderService/src/test/java/mss301/providerservice/service/CertificateServiceTests.java
@@ -6,6 +6,7 @@
 import mss301.providerservice.mapper.ProviderViewMapper;
 import mss301.providerservice.repository.CertificateRepository;
 import mss301.providerservice.security.ProviderAuthorization;
+import mss301.providerservice.service.Impl.CertificateServiceImpl;
 import org.junit.jupiter.api.Test;
 import org.junit.jupiter.api.extension.ExtendWith;
 import org.mockito.*;
@@ -23,7 +24,7 @@
     @Mock CaregiverService caregiverService;
     @Mock ProviderAuthorization authorization;
     @Mock ProviderViewMapper mapper;
-    @InjectMocks CertificateService service;
+    @InjectMocks CertificateServiceImpl service;
```

#### 3. `IdentityAccountServiceTests.java`
File: `ProviderService/src/test/java/mss301/providerservice/service/IdentityAccountServiceTests.java`

```diff
--- a/ProviderService/src/test/java/mss301/providerservice/service/IdentityAccountServiceTests.java
+++ b/ProviderService/src/test/java/mss301/providerservice/service/IdentityAccountServiceTests.java
@@ -4,6 +4,7 @@
 import mss301.providerservice.client.dto.IdentityUserResponse;
 import mss301.providerservice.dto.response.APIResponse;
 import mss301.providerservice.exception.*;
+import mss301.providerservice.service.Impl.IdentityAccountServiceImpl;
 import org.junit.jupiter.api.Test;
 
 import java.util.*;
@@ -13,7 +14,7 @@
 
 class IdentityAccountServiceTests {
     IdentityClient client = mock(IdentityClient.class);
-    IdentityAccountService service = new IdentityAccountService(client);
+    IdentityAccountService service = new IdentityAccountServiceImpl(client);
```

#### 4. `ShiftRegistrationServiceTests.java`
File: `ProviderService/src/test/java/mss301/providerservice/service/ShiftRegistrationServiceTests.java`

```diff
--- a/ProviderService/src/test/java/mss301/providerservice/service/ShiftRegistrationServiceTests.java
+++ b/ProviderService/src/test/java/mss301/providerservice/service/ShiftRegistrationServiceTests.java
@@ -7,6 +7,7 @@
 import mss301.providerservice.mapper.ProviderViewMapper;
 import mss301.providerservice.repository.*;
 import mss301.providerservice.security.ProviderAuthorization;
+import mss301.providerservice.service.Impl.ShiftRegistrationServiceImpl;
 import org.junit.jupiter.api.Test;
 import org.junit.jupiter.api.extension.ExtendWith;
 import org.mockito.*;
@@ -26,7 +27,7 @@
     @Mock CaregiverService caregiverService;
     @Mock ProviderAuthorization authorization;
     @Mock ProviderViewMapper mapper;
-    @InjectMocks ShiftRegistrationService service;
+    @InjectMocks ShiftRegistrationServiceImpl service;
```

#### 5. `WorkShiftServiceTests.java`
File: `ProviderService/src/test/java/mss301/providerservice/service/WorkShiftServiceTests.java`

```diff
--- a/ProviderService/src/test/java/mss301/providerservice/service/WorkShiftServiceTests.java
+++ b/ProviderService/src/test/java/mss301/providerservice/service/WorkShiftServiceTests.java
@@ -7,6 +7,7 @@
 import mss301.providerservice.mapper.ProviderViewMapper;
 import mss301.providerservice.repository.*;
 import mss301.providerservice.security.ProviderAuthorization;
+import mss301.providerservice.service.Impl.WorkShiftServiceImpl;
 import org.junit.jupiter.api.Test;
 import org.junit.jupiter.api.extension.ExtendWith;
 import org.mockito.*;
@@ -25,7 +26,7 @@
     @Mock ShiftRegistrationRepository registrationRepository;
     @Mock ProviderAuthorization authorization;
     @Mock ProviderViewMapper mapper;
-    @InjectMocks WorkShiftService service;
+    @InjectMocks WorkShiftServiceImpl service;
```

---

### 4.7. Target State of `ProviderService` Service Layer

Following implementation of this blueprint, the service layer of `ProviderService` achieves:

- **Root `service/` package** (100% pure interfaces, 0 classes):
  1. `CareCenterService.java` (`public interface`)
  2. `CaregiverScheduleService.java` (`public interface`)
  3. `CaregiverService.java` (`public interface`)
  4. `CertificateService.java` (`public interface`)
  5. `IdentityAccountService.java` (`public interface`)
  6. `ProviderMediaService.java` (`public interface`)
  7. `ShiftRegistrationService.java` (`public interface`)
  8. `WorkShiftService.java` (`public interface`)
- **Subpackage `service/Impl/`** (100% concrete classes ending in `ServiceImpl`, 0 interfaces):
  1. `CareCenterServiceImpl.java` (`public class ... implements CareCenterService`)
  2. `CaregiverScheduleServiceImpl.java` (`public class ... implements CaregiverScheduleService`)
  3. `CaregiverServiceImpl.java` (`public class ... implements CaregiverService`)
  4. `CertificateServiceImpl.java` (`public class ... implements CertificateService`)
  5. `IdentityAccountServiceImpl.java` (`public class ... implements IdentityAccountService`)
  6. `ProviderMediaServiceImpl.java` (`public class ... implements ProviderMediaService`)
  7. `ShiftRegistrationServiceImpl.java` (`public class ... implements ShiftRegistrationService`)
  8. `WorkShiftServiceImpl.java` (`public class ... implements WorkShiftService`)

---

## 5. Verification Method

To independently verify this refactoring blueprint:

1. **Verify Pure Interfaces in `service/`**:
   Execute PowerShell in `d:\FA26\MSS\CareConnect\BE`:
   ```powershell
   Get-ChildItem -Path ProviderService\src\main\java\mss301\providerservice\service\*.java | ForEach-Object {
       $content = Get-Content $_.FullName -Raw
       if ($content -match "public\s+class\s+") {
           Write-Error "VIOLATION: Class found in service/ root: $($_.Name)"
       } else {
           Write-Host "PASS: Pure interface: $($_.Name)"
       }
   }
   ```
   **Expected result**: Exactly 8 files, all interfaces, 0 classes.

2. **Verify Implementations in `service/Impl/`**:
   ```powershell
   Get-ChildItem -Path ProviderService\src\main\java\mss301\providerservice\service\Impl\*.java | ForEach-Object {
       $content = Get-Content $_.FullName -Raw
       if ($_.Name -notmatch "ServiceImpl\.java$") {
           Write-Error "VIOLATION: Impl class naming convention: $($_.Name)"
       }
       if ($content -notmatch "@Service") {
           Write-Error "VIOLATION: Missing @Service: $($_.Name)"
       }
   }
   ```
   **Expected result**: Exactly 8 files, all named `*ServiceImpl.java` with `@Service`.

3. **Verify Elimination of `/internal/**` Artifacts**:
   ```powershell
   rg -n "InternalSecretFilter|InternalSecurityConfig|X-Internal-Secret|InternalProviderService" ProviderService
   ```
   **Expected result**: Zero matches across `ProviderService`.

4. **Verify Compilation and Tests**:
   ```powershell
   cd d:\FA26\MSS\CareConnect\BE\ProviderService
   .\mvnw.cmd clean test-compile
   .\mvnw.cmd test
   ```
   **Expected result**: `BUILD SUCCESS` with zero compilation errors and passing unit/flow tests.
