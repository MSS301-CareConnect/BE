# Handoff Report — Challenger M2.2: ProviderService Contract & Feign Interceptor Verification

**Verdict**: **REQUEST_CHANGES** (Actionable fix: add `@JsonIgnoreProperties(ignoreUnknown = true)` to ProviderService client DTOs)

---

## 1. Observation

### 1.1. DTO Serialization & Backward Compatibility
- **`CenterResponse.java`** (`src/main/java/mss301/providerservice/dto/response/CenterResponse.java`):
  Lines 37-39 define:
  ```java
  public UUID getCenterId() {
      return id;
  }
  ```
  Jackson serialization produces `"centerId"` alongside `"id"`. The fields `centerName`, `centerStatus` (enum `CenterStatus`), and `managerUserId` match `mss301.bookingservice.client.dto.ProviderCenterResponse`.
- **`CaregiverResponse.java`** (`src/main/java/mss301/providerservice/dto/response/CaregiverResponse.java`):
  Line 18 defines:
  ```java
  CenterStatus centerStatus;
  ```
  Lines 37-39 define:
  ```java
  public UUID getCaregiverId() {
      return id;
  }
  ```
  Jackson serialization produces `"caregiverId"` alongside `"id"`. The fields `userId`, `centerId`, `centerStatus`, `fullName`, `verificationStatus`, and `caregiverStatus` match `mss301.bookingservice.client.dto.ProviderCaregiverResponse`.
- **Public REST Endpoints in ProviderService**:
  - `GET /api/v1/care-centers/{centerId}` in `CareCenterController.java` (lines 61-66) returns `APIResponse<CenterResponse>` with `@PreAuthorize("isAuthenticated()")`.
  - `GET /api/v1/caregivers/{caregiverId}` in `CaregiverController.java` (lines 28-33) returns `APIResponse<CaregiverResponse>` with `@PreAuthorize("isAuthenticated()")`.
  - `GET /api/v1/caregivers/by-user/{userId}` in `CaregiverController.java` (lines 35-40) returns `APIResponse<CaregiverResponse>` with `@PreAuthorize("isAuthenticated()")`.

### 1.2. Feign Client Token & Correlation Propagation
- **`FeignAuthForwardingInterceptor.java`** (`src/main/java/mss301/providerservice/client/FeignAuthForwardingInterceptor.java`):
  Lines 19-36 implement three-tier token extraction:
  1. `SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwtAuth`: forwards `Authorization: Bearer <tokenValue>`.
  2. Fallback to `RequestContextHolder.getRequestAttributes()` -> `HttpServletRequest.getHeader("Authorization")`: forwards raw header.
  3. Fallback to `CookieUtils.read(req, CookieUtils.ACCESS)`: forwards `Authorization: Bearer <cookieToken>`.
  Lines 38-41 forward `X-Request-Id` from `MDC.get(RequestIdFilter.MDC_KEY)`.
- **Client Registrations**:
  - `IdentityClientConfig.java` (lines 9-12): registers `FeignAuthForwardingInterceptor`.
  - `MediaClientConfig.java` (lines 9-12): registers `FeignAuthForwardingInterceptor`.
- **`FeignAuthForwardingInterceptorTests.java`**: 6 unit tests covering:
  - SecurityContext JWT bearer forwarding.
  - Request header forwarding fallback.
  - Cookie token forwarding fallback.
  - Non-JWT Authentication fallback to request cookie.
  - MDC `X-Request-Id` forwarding.
  - Unauthenticated / outside-request safety (no exceptions thrown).

### 1.3. MapStruct View Mapper Accuracy
- **`ProviderViewMapper.java`** (`src/main/java/mss301/providerservice/mapper/ProviderViewMapper.java`):
  Declared as an interface annotated with `@Mapper(config = MapStructConfig.class)`.
  Explicit mappings map nested entities (`center.id` -> `centerId`, `center.centerName` -> `centerName`, `center.centerStatus` -> `centerStatus`, `workShift.id` -> `workShiftId`, `approvedCount`).
- **`ProviderViewMapperTests.java`**: 5 unit tests validating:
  - `caregiverMappingFlattensCenterProperties` (all center and caregiver properties).
  - `certificateMappingFlattensCaregiverId`.
  - `shiftMappingCombinesWorkShiftAndApprovedCount`.
  - `registrationMappingFlattensShiftAndCaregiver`.
  - `scheduleMappingFlattensShiftCenterAndCaregiver`.

### 1.4. Maven Test Suite Execution
- Executed command:
  ```powershell
  cd d:\FA26\MSS\CareConnect\BE\ProviderService
  .\mvnw.cmd test
  ```
- Output:
  ```
  [INFO] Results:
  [INFO] 
  [INFO] Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
  [INFO] 
  [INFO] ------------------------------------------------------------------------
  [INFO] BUILD SUCCESS
  [INFO] ------------------------------------------------------------------------
  [INFO] Total time:  47.606 s
  ```
  All 23 tests across 10 test classes passed with 0 failures, 0 errors, and 0 skipped.

### 1.5. Empirical Vulnerability / Bug Identified: Feign Client DTO Deserialization Crash
- **Configuration**: `ProviderService/src/main/resources/application.yaml` lines 25-27:
  ```yaml
  spring:
    jackson:
      deserialization:
        fail-on-unknown-properties: true
  ```
- **Observed DTO in ProviderService**:
  `src/main/java/mss301/providerservice/client/dto/IdentityUserResponse.java`:
  ```java
  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public class IdentityUserResponse {
      UUID id;
      String email;
      String fullName;
      String phoneNumber;
      String status;
      Set<String> roles;
  }
  ```
  Notice: No `@JsonIgnoreProperties(ignoreUnknown = true)` annotation!
- **Observed Upstream Response from IdentityService**:
  `IdentityService/src/main/java/mss301/identityservice/dto/response/UserResponse.java` returned by `UserController.java` (`GET /api/v1/users/{id}`):
  Contains additional fields: `centerId`, `permissions`, and `createdAt`.
- **Empirical Failure Reproduction**:
  When Jackson deserializes `UserResponse` with `centerId`, `permissions`, `createdAt` into `IdentityUserResponse` under `fail-on-unknown-properties: true`, it immediately throws:
  `com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException: Unrecognized field "centerId" (class mss301.providerservice.client.dto.IdentityUserResponse), not marked as ignorable`.
- **Secondary Client DTO**:
  `src/main/java/mss301/providerservice/client/dto/MediaClientResponse.java` is similarly missing `@JsonIgnoreProperties(ignoreUnknown = true)` while upstream `MediaService/src/main/java/mss301/mediaservice/dto/response/MediaResponse.java` returns 7 extra fields (`mediaPurpose`, `mediaStatus`, `uploadedBy`, `sourceService`, `referenceType`, `referenceId`, `createdAt`).

---

## 2. Logic Chain

1. **DTO Compatibility Verification**:
   - `CenterResponse` has getter `getCenterId()`. Under Jackson serialization, this creates the JSON key `"centerId": <UUID>`, which directly fulfills `ProviderCenterResponse.getCenterId()`.
   - `CaregiverResponse` has getter `getCaregiverId()` and field `CenterStatus centerStatus`. Under Jackson serialization, this creates `"caregiverId": <UUID>` and `"centerStatus": "ACTIVE"`, fulfilling `ProviderCaregiverResponse.getCaregiverId()` and `getCenterStatus()`.
   - Furthermore, `CareCenterController` and `CaregiverController` expose authenticated GET endpoints (`/api/v1/care-centers/{centerId}`, `/api/v1/caregivers/{caregiverId}`, `/api/v1/caregivers/by-user/{userId}`) returning `APIResponse<CenterResponse>` and `APIResponse<CaregiverResponse>`, whose envelope matches `RemoteApiResponse<T>` in BookingService.
2. **Feign Authentication Interceptor Verification**:
   - `FeignAuthForwardingInterceptor` prioritizes `JwtAuthenticationToken` from `SecurityContextHolder`, correctly extracts the raw JWT token string, and prepends `Bearer `.
   - If security context has no JWT, it falls back to the HTTP `Authorization` header, and if that is absent, it reads the `access_token` cookie from `CookieUtils.ACCESS`.
   - In all cases, it extracts `X-Request-Id` from the MDC thread context (`RequestIdFilter.MDC_KEY`) and forwards it on `RequestIdFilter.HEADER`.
   - Both `IdentityClient` and `MediaClient` are configured via their respective configuration classes to attach this interceptor. All 6 unit tests pass.
3. **MapStruct View Mapper Verification**:
   - `ProviderViewMapper` compiles as a MapStruct interface satisfying `ReportingPolicy.ERROR` via `MapStructConfig.class`.
   - `ProviderViewMapperTests` executes 5 unit tests validating that entity relationships across `CaregiverProfile`, `CareCenter`, `Certificate`, `WorkShift`, `ShiftRegistration`, and `CaregiverSchedule` flatten into their respective response DTOs with complete attribute fidelity.
4. **Vulnerability Logic Chain**:
   - In M1 and M2, `InternalUserController` was deleted and `IdentityClient` was re-pointed to `@GetMapping("/api/v1/users/{userId}")`.
   - IdentityService's `/api/v1/users/{userId}` endpoint returns `UserResponse`, which contains `centerId`, `permissions`, and `createdAt`.
   - ProviderService's `IdentityUserResponse` does not declare these fields and does not annotate `@JsonIgnoreProperties(ignoreUnknown = true)`.
   - Because `ProviderService` has `spring.jackson.deserialization.fail-on-unknown-properties: true`, any runtime invocation of `IdentityAccountService.requireActiveRole(...)` (such as during `CareCenterController.create` or `CareCenterController.update`) will result in a fatal `UnrecognizedPropertyException` during response body deserialization.
   - Contrast this with `CareRecipientService` (`mss301.carerecipientservice.client.dto.IdentityUserResponse`) and `BookingService` (`mss301.bookingservice.client.dto.IdentityUserResponse`), which both correctly declare `@JsonIgnoreProperties(ignoreUnknown = true)`.

---

## 3. Caveats

1. **No Live Gateway Wire Traffic**: Current verification was conducted via unit tests, MapStruct compiler verification, and Jackson deserialization harnesses; live network routing through ApiGateway will be verified in Milestone 4 / E2E.
2. **BookingService Client Endpoint Update**: BookingService's `ProviderClient` currently targets `/internal/centers/{centerId}` and `/internal/caregivers/{caregiverId}`. In Milestone 3 / 4, BookingService must update these URL paths to `/api/v1/care-centers/{centerId}` and `/api/v1/caregivers/{caregiverId}`.

---

## 4. Conclusion

**Verdict: REQUEST_CHANGES**

While items 1-4 of the dispatch assignment pass with flying colors (DTO compatibility confirmed, Feign interceptor verified with 6/6 tests, MapStruct mapper verified with 5/5 tests, and test suite executes with 23/23 tests passing), adversarial empirical testing uncovered a high-risk bug in ProviderService's Feign client deserialization:
- `IdentityUserResponse.java` and `MediaClientResponse.java` lack `@JsonIgnoreProperties(ignoreUnknown = true)`.
- Under `spring.jackson.deserialization.fail-on-unknown-properties: true`, calling `IdentityClient.get(userId)` or `MediaClient` will crash with `UnrecognizedPropertyException` when parsing the actual JSON returned by `IdentityService` and `MediaService`.

### Required Actions Before Gate Approval:
1. In `ProviderService/src/main/java/mss301/providerservice/client/dto/IdentityUserResponse.java`:
   Add `@JsonIgnoreProperties(ignoreUnknown = true)`.
2. In `ProviderService/src/main/java/mss301/providerservice/client/dto/MediaClientResponse.java`:
   Add `@JsonIgnoreProperties(ignoreUnknown = true)`.
3. In `ProviderService/src/main/java/mss301/providerservice/client/dto/DownloadUrlClientResponse.java`:
   Add `@JsonIgnoreProperties(ignoreUnknown = true)` (for consistency and forward compatibility).

---

## 5. Verification Method

To independently verify these observations:

1. **Verify DTO Compatibility Getters**:
   ```powershell
   Select-String -Path "ProviderService\src\main\java\mss301\providerservice\dto\response\CenterResponse.java" -Pattern "getCenterId"
   Select-String -Path "ProviderService\src\main\java\mss301\providerservice\dto\response\CaregiverResponse.java" -Pattern "getCaregiverId|centerStatus"
   ```

2. **Verify Feign Interceptor & MapStruct Tests**:
   ```powershell
   cd ProviderService
   .\mvnw.cmd test -Dtest=FeignAuthForwardingInterceptorTests,ProviderViewMapperTests
   ```

3. **Verify Full Test Suite**:
   ```powershell
   cd ProviderService
   .\mvnw.cmd test
   # Expected output: Tests run: 23, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS
   ```

4. **Verify Unknown Properties Bug**:
   Inspect `IdentityUserResponse.java` vs `CareRecipientService`'s `IdentityUserResponse.java`:
   ```powershell
   Select-String -Path "CareRecipientService\src\main\java\mss301\carerecipientservice\client\dto\IdentityUserResponse.java" -Pattern "JsonIgnoreProperties"
   # Shows @JsonIgnoreProperties(ignoreUnknown = true)
   Select-String -Path "ProviderService\src\main\java\mss301\providerservice\client\dto\IdentityUserResponse.java" -Pattern "JsonIgnoreProperties"
   # Output: 0 matches -> Vulnerable to UnrecognizedPropertyException
   ```
