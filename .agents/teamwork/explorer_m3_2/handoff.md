# Handoff Report: CareCatalogService Refactoring Blueprint (Feature 15 & 16)

**Agent**: Explorer M3.2 (`explorer_m3_2`)  
**Parent**: orchestrator_1 (`c390ebfb-aa07-4694-bdae-bfb529d83f93`)  
**Milestone**: M3 (CareCatalogService Refactoring)  
**Date**: 2026-10-18T16:35:00Z  

---

## 1. Observation

### 1.1 Existing Controller Structure & Endpoint Analysis
Inspected all controllers in `CareCatalogService/src/main/java/mss301/carecatalogservice/controller/`:

1. **`CatalogItemController.java`** (`src/main/java/mss301/carecatalogservice/controller/CatalogItemController.java`):
   - Currently annotated with `@RequestMapping("/care-services")` at line 19.
   - Endpoints:
     - Line 26: `POST /care-services` -> `@PreAuthorize("hasRole('ADMIN')")`
     - Line 32: `PUT /care-services/{id}` -> `@PreAuthorize("hasRole('ADMIN')")`
     - Line 39: `GET /care-services` -> **No `@PreAuthorize`** (relies on security filter chain `.anyRequest().authenticated()`)
     - Line 44: `GET /care-services/{id}` -> **No `@PreAuthorize`**
     - Line 49: `DELETE /care-services/{id}` -> `@PreAuthorize("hasRole('ADMIN')")`
   - Note: The entity is `CatalogItem`, repository is `CatalogItemRepository`, service is `CatalogItemService`. DISPATCH specifies standardizing to `/api/v1/catalog-items/**` while maintaining optional backward compatibility for `/api/v1/care-services/**`.

2. **`CenterOfferingController.java`** (`src/main/java/mss301/carecatalogservice/controller/CenterOfferingController.java`):
   - Currently annotated with `@RequestMapping("/center-services")` at line 20.
   - Endpoints:
     - Line 27: `POST /center-services` -> `@PreAuthorize("hasRole('CENTER_MANAGER')")`
     - Line 34: `PUT /center-services/{id}` -> `@PreAuthorize("hasRole('CENTER_MANAGER')")`
     - Line 42: `GET /center-services/center/{centerId}` -> **No `@PreAuthorize`**
     - Line 47: `GET /center-services/{id}` -> **No `@PreAuthorize`**
     - Line 52: `DELETE /center-services/{id}` -> `@PreAuthorize("hasRole('CENTER_MANAGER')")`

3. **`PricePlanController.java`** (`src/main/java/mss301/carecatalogservice/controller/PricePlanController.java`):
   - Currently annotated with `@RequestMapping("/price-plans")` at line 20.
   - Endpoints:
     - Line 27: `POST /price-plans` -> `@PreAuthorize("hasRole('CENTER_MANAGER')")`
     - Line 34: `PUT /price-plans/{id}` -> `@PreAuthorize("hasRole('CENTER_MANAGER')")`
     - Line 42: `GET /price-plans/center-offering/{centerOfferingId}` & `/center-service/{centerOfferingId}` -> **No `@PreAuthorize`**
     - Line 47: `GET /price-plans/{id}` -> **No `@PreAuthorize`**
     - Line 52: `DELETE /price-plans/{id}` -> `@PreAuthorize("hasRole('CENTER_MANAGER')")`

4. **`InternalCatalogController.java`** (`src/main/java/mss301/carecatalogservice/controller/InternalCatalogController.java`):
   - Lines 19-32:
     ```java
     @RestController
     @RequestMapping("/internal/center-services")
     @RequiredArgsConstructor
     @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
     @Tag(name = "Internal - Catalog", description = "Báo giá dịch vụ nội bộ cho BookingService. Bảo vệ bởi X-Internal-Secret.")
     public class InternalCatalogController {
         CatalogQuoteService catalogQuoteService;

         @PostMapping("/quote")
         public APIResponse<List<InternalServiceQuoteResponse>> quote(@Valid @RequestBody InternalServiceQuoteRequest request) {
             return APIResponse.success("Báo giá dịch vụ thành công", catalogQuoteService.quote(request));
         }
     }
     ```
   - Consumed by `BookingService`'s `CareCatalogClient.java`:
     `@PostMapping("/internal/center-services/quote")`
     `RemoteApiResponse<List<CareCatalogQuoteResponse>> quote(@RequestBody CareCatalogQuoteRequest request);`

### 1.2 Internal Security & Filter Infrastructure
Inspected security and properties files:
1. **`InternalSecurityConfig.java`** (`src/main/java/mss301/carecatalogservice/security/InternalSecurityConfig.java`):
   - Lines 20-50: Configures dedicated `SecurityFilterChain` with `@Order(1)` matching `/internal/**`, using `InternalSecretFilter` with `careCatalogProperties.getInternalSecret()`.
2. **`InternalSecretFilter.java`** (`src/main/java/mss301/carecatalogservice/security/InternalSecretFilter.java`):
   - Lines 23-57: OncePerRequestFilter checking `X-Internal-Secret` against configured secret.
3. **`CareCatalogProperties.java`** (`src/main/java/mss301/carecatalogservice/config/CareCatalogProperties.java`):
   - Lines 19-20: `@ToString.Exclude String internalSecret = "";`
   - Lines 50-52: `@ToString.Exclude String internalSecret = "";` under `Identity` nested class.
4. **`application.yaml`** (`src/main/resources/application.yaml`):
   - Line 73: `internal-secret: ${CARE_CATALOG_INTERNAL_SECRET:}`
   - Line 82: `internal-secret: ${CARE_CATALOG_IDENTITY_INTERNAL_SECRET:}`
5. **`.env.example`** (`CareCatalogService/.env.example`):
   - Lines 25, 31: `CARE_CATALOG_IDENTITY_INTERNAL_SECRET=`, `CARE_CATALOG_INTERNAL_SECRET=`.
6. **`OpenApiConfig.java`** (`src/main/java/mss301/carecatalogservice/config/swagger/OpenApiConfig.java`):
   - Line 15: `import mss301.carecatalogservice.security.InternalSecretFilter;`
   - Line 30: `private static final String INTERNAL_SCHEME = "internalSecret";`
   - Line 31: `private static final String INTERNAL_PREFIX = "/internal/";`
   - Lines 56-60: registers `INTERNAL_SCHEME` security scheme in Swagger Components.
   - Lines 90-95: applies `INTERNAL_SCHEME` to any path starting with `/internal/`.

### 1.3 Service Layer & Domain Aggregate Purity (R2)
Inspected `service/` and `service/Impl/`:
- `CatalogItemService.java`: `public interface CatalogItemService`
- `CatalogItemServiceImpl.java`: `public class CatalogItemServiceImpl implements CatalogItemService` in `service/Impl/`
- `CenterOfferingService.java`: `public interface CenterOfferingService`
- `CenterOfferingServiceImpl.java`: `public class CenterOfferingServiceImpl implements CenterOfferingService` in `service/Impl/`
- `PricePlanService.java`: `public interface PricePlanService`
- `PricePlanServiceImpl.java`: `public class PricePlanServiceImpl implements PricePlanService` in `service/Impl/`
- `CatalogQuoteService.java`: `public interface CatalogQuoteService`
- `CatalogQuoteServiceImpl.java`: `public class CatalogQuoteServiceImpl implements CatalogQuoteService` in `service/Impl/`
**Result**: 100% of services are interfaces; 100% of implementations are in `service/Impl/`. No violation of R2.

### 1.4 Entities & MapStruct Mappers (R5)
Inspected entities and mappers:
- `BaseEntity.java`: `@Getter @Setter @NoArgsConstructor(access = AccessLevel.PROTECTED) @AllArgsConstructor(access = AccessLevel.PROTECTED) @FieldDefaults(level = AccessLevel.PRIVATE) @MappedSuperclass`
- `CatalogItem.java`: `@Entity @Table(name = "care_services") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE) ... extends BaseEntity`
- `CenterOffering.java`: `@Entity @Table(name = "center_services") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE) ... extends BaseEntity`
- `PricePlan.java`: `@Entity @Table(name = "price_plans") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE) ... extends BaseEntity`
**Result**: 0 entities use `@Data`. All 3 domain entities use `@Getter @Setter` and extend `BaseEntity`. 100% compliant with R5.
- Mappers (`CatalogItemMapper.java`, `CenterOfferingMapper.java`, `PricePlanMapper.java`):
  All use `@Mapper(config = MapStructConfig.class)` where `MapStructConfig` sets `unmappedTargetPolicy = ReportingPolicy.ERROR`. All unmapped targets are explicitly handled with `@IgnoreServerManagedFields` or `@Mapping(..., ignore = true)`. 100% compliant with R5.

### 1.5 Verification Baseline
- Executed `.\mvnw.cmd clean test-compile` -> BUILD SUCCESS in 14.1s.
- Executed `.\mvnw.cmd test` -> Tests run: 4, Failures: 0, Errors: 0, Skipped: 1 (in 10.1s).
  - `CatalogQuoteServiceTests`: 3 passed, 0 failures.
  - `CareCatalogServiceApplicationTests`: 1 skipped (requires live DB).

---

## 2. Logic Chain

1. **R1 & R3 Controller Versioning & Method-Level Security**:
   - `CatalogItemController`: Currently handles catalog item domain aggregate. Prefixing `@RequestMapping({"/api/v1/catalog-items", "/api/v1/care-services"})` aligns with REST resource conventions (`catalog-items`) while preserving `/care-services` backwards compatibility. Adding `@PreAuthorize("isAuthenticated()")` to `listAll()` and `getById(UUID)` ensures explicit declarative access control at the method level.
   - `CenterOfferingController`: Handles center offerings aggregate. Prefixing `@RequestMapping("/api/v1/center-services")` satisfies R3. Adding `@PreAuthorize("isAuthenticated()")` to `listByCenter(UUID)` and `getById(UUID)` satisfies method-level security.
   - `PricePlanController`: Handles pricing plan aggregate. Prefixing `@RequestMapping("/api/v1/price-plans")` satisfies R3. Adding `@PreAuthorize("isAuthenticated()")` to `listByCenterOffering(UUID)` and `getById(UUID)` satisfies method-level security.

2. **R4 Relocate Quote Calculation to CenterOfferingController**:
   - `InternalCatalogController.quote(...)` is an internal service endpoint previously called by `BookingService`.
   - Per R4, `/internal/**` must be eliminated and all inter-service communication routed through `ApiGateway` with JWT forwarding.
   - Center services (`CenterOffering`) is the domain aggregate that prices and offers care services on behalf of centers. Relocating `quote` to `CenterOfferingController` as `POST /api/v1/center-services/quote` (`@PreAuthorize("isAuthenticated()")`) naturally consolidates offering/pricing calculation under the `CenterOffering` aggregate.
   - `CenterOfferingService` exposes `List<ServiceQuoteResponse> quote(ServiceQuoteRequest request)`. `CenterOfferingServiceImpl` delegates directly to `CatalogQuoteService.quote(request)`. This keeps `CenterOfferingServiceImpl` clean, conforms to interface segregation, and keeps the extensive calculation logic in `CatalogQuoteService` and its dedicated unit test suite (`CatalogQuoteServiceTests`) intact.

3. **R4 Eradication of `*Internal*` Artifacts**:
   - Deleting `InternalCatalogController.java`, `InternalSecurityConfig.java`, and `InternalSecretFilter.java` removes all internal controllers, security filters, and filter configurations.
   - Renaming `InternalServiceQuoteRequest.java` -> `ServiceQuoteRequest.java` and `InternalServiceQuoteResponse.java` -> `ServiceQuoteResponse.java` eliminates all `*Internal*.java` filenames from the codebase, satisfying the acceptance criterion: `100% các file *Internal*.java đã bị xóa bỏ hoàn toàn`.
   - Removing `internalSecret` properties from `CareCatalogProperties.java`, `application.yaml`, and `.env.example` cleans up obsolete configuration.
   - Removing `INTERNAL_SCHEME` and `InternalSecretFilter` references from `OpenApiConfig.java` prevents compilation failure and ensures Swagger documentation accurately reflects the unified public API.

---

## 3. Caveats

1. **Consumer Service Alignment (BookingService)**:
   - `BookingService`'s `CareCatalogClient.java` currently targets `@PostMapping("/internal/center-services/quote")`.
   - In M4 (ApiGateway & Cross-Service Feign), `BookingService` will be updated to point to `http://localhost:8080/api/v1/center-services/quote` with `FeignAuthForwardingInterceptor`. During M3, CareCatalogService implements the new endpoint `POST /api/v1/center-services/quote`.
2. **Path Aliasing for CatalogItemController**:
   - `CatalogItemController` was mapped to `/care-services`. DISPATCH requested `/api/v1/catalog-items/**`. Mapping both `{"/api/v1/catalog-items", "/api/v1/care-services"}` guarantees zero breakage for any existing callers while fulfilling the exact dispatch requirement.
3. **Identity Properties Block**:
   - `CareCatalogProperties.Identity` contained `internalSecret` (line 52). While `CareCatalogService` does not actively call Identity via Feign, removing `internalSecret` from `Identity` is included for complete internal secret purging.

---

## 4. Conclusion & Complete Implementation Blueprint

### 4.1 File Inventory for Changes
| Action | File Path | Scope |
|---|---|---|
| **DELETE** | `src/main/java/mss301/carecatalogservice/controller/InternalCatalogController.java` | Entire file |
| **DELETE** | `src/main/java/mss301/carecatalogservice/security/InternalSecurityConfig.java` | Entire file |
| **DELETE** | `src/main/java/mss301/carecatalogservice/security/InternalSecretFilter.java` | Entire file |
| **RENAME & EDIT** | `src/main/java/mss301/carecatalogservice/dto/request/InternalServiceQuoteRequest.java` -> `ServiceQuoteRequest.java` | Rename class & file |
| **RENAME & EDIT** | `src/main/java/mss301/carecatalogservice/dto/response/InternalServiceQuoteResponse.java` -> `ServiceQuoteResponse.java` | Rename class & file |
| **MODIFY** | `src/main/java/mss301/carecatalogservice/controller/CatalogItemController.java` | Prefix `/api/v1/catalog-items`, add `@PreAuthorize("isAuthenticated()")` |
| **MODIFY** | `src/main/java/mss301/carecatalogservice/controller/CenterOfferingController.java` | Prefix `/api/v1/center-services`, add quote endpoint, add `@PreAuthorize("isAuthenticated()")` |
| **MODIFY** | `src/main/java/mss301/carecatalogservice/controller/PricePlanController.java` | Prefix `/api/v1/price-plans`, add `@PreAuthorize("isAuthenticated()")` |
| **MODIFY** | `src/main/java/mss301/carecatalogservice/service/CenterOfferingService.java` | Add `quote` method |
| **MODIFY** | `src/main/java/mss301/carecatalogservice/service/Impl/CenterOfferingServiceImpl.java` | Inject `CatalogQuoteService` and implement `quote` delegate |
| **MODIFY** | `src/main/java/mss301/carecatalogservice/service/CatalogQuoteService.java` | Update signature to `ServiceQuoteRequest`/`Response` |
| **MODIFY** | `src/main/java/mss301/carecatalogservice/service/Impl/CatalogQuoteServiceImpl.java` | Update signature and references to `ServiceQuoteRequest`/`Response` |
| **MODIFY** | `src/main/java/mss301/carecatalogservice/config/CareCatalogProperties.java` | Remove `internalSecret` properties |
| **MODIFY** | `src/main/resources/application.yaml` | Remove `internal-secret` keys |
| **MODIFY** | `CareCatalogService/.env.example` | Remove `CARE_CATALOG_INTERNAL_SECRET` |
| **MODIFY** | `src/main/java/mss301/carecatalogservice/config/swagger/OpenApiConfig.java` | Remove `InternalSecretFilter` & `INTERNAL_SCHEME` |
| **MODIFY** | `src/test/java/mss301/carecatalogservice/service/CatalogQuoteServiceTests.java` | Update imports and types to `ServiceQuoteRequest`/`Response` |

---

### 4.2 Exact Line-by-Line Code Replacements

#### 1. `CatalogItemController.java`
**Target**: `CareCatalogService/src/main/java/mss301/carecatalogservice/controller/CatalogItemController.java`
```java
package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.CatalogItemRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CatalogItemResponse;
import mss301.carecatalogservice.service.CatalogItemService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/catalog-items", "/api/v1/care-services"})
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Catalog item", description = "Danh mục dịch vụ chuẩn của nền tảng. Chỉ ADMIN được tạo/sửa/xóa.")
public class CatalogItemController {
    CatalogItemService catalogItemService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CatalogItemResponse> create(@Valid @RequestBody CatalogItemRequest request) {
        return catalogItemService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<CatalogItemResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody CatalogItemRequest request) {
        return catalogItemService.update(id, request);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<CatalogItemResponse>> listAll() {
        return catalogItemService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CatalogItemResponse> getById(@PathVariable UUID id) {
        return catalogItemService.getById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<Void> delete(@PathVariable UUID id) {
        return catalogItemService.delete(id);
    }
}
```

#### 2. `CenterOfferingController.java`
**Target**: `CareCatalogService/src/main/java/mss301/carecatalogservice/controller/CenterOfferingController.java`
```java
package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;
import mss301.carecatalogservice.security.CurrentCenterId;
import mss301.carecatalogservice.service.CenterOfferingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/center-services")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Center offering", description = "Dịch vụ mà trung tâm cung cấp (dựa trên danh mục chuẩn).")
public class CenterOfferingController {
    CenterOfferingService centerOfferingService;

    @PostMapping
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CenterOfferingResponse> create(@CurrentCenterId UUID centerId,
                                                      @Valid @RequestBody CenterOfferingRequest request) {
        return centerOfferingService.create(centerId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CenterOfferingResponse> update(@CurrentCenterId UUID centerId,
                                                      @PathVariable UUID id,
                                                      @Valid @RequestBody CenterOfferingRequest request) {
        return centerOfferingService.update(centerId, id, request);
    }

    @GetMapping("/center/{centerId}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<CenterOfferingResponse>> listByCenter(@PathVariable UUID centerId) {
        return centerOfferingService.listByCenter(centerId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<CenterOfferingResponse> getById(@PathVariable UUID id) {
        return centerOfferingService.getById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<Void> delete(@CurrentCenterId UUID centerId, @PathVariable UUID id) {
        return centerOfferingService.delete(centerId, id);
    }

    @PostMapping("/quote")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Báo giá dịch vụ trung tâm cho BookingService hoặc khách hàng đặt lịch")
    public APIResponse<List<ServiceQuoteResponse>> quote(@Valid @RequestBody ServiceQuoteRequest request) {
        return APIResponse.success("Báo giá dịch vụ thành công", centerOfferingService.quote(request));
    }
}
```

#### 3. `PricePlanController.java`
**Target**: `CareCatalogService/src/main/java/mss301/carecatalogservice/controller/PricePlanController.java`
```java
package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.security.CurrentCenterId;
import mss301.carecatalogservice.service.PricePlanService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/price-plans")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Price plan", description = "Bảng giá theo giờ / ngày / ca của dịch vụ trung tâm.")
public class PricePlanController {
    PricePlanService pricePlanService;

    @PostMapping
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PricePlanResponse> create(@CurrentCenterId UUID centerId,
                                                 @Valid @RequestBody PricePlanRequest request) {
        return pricePlanService.create(centerId, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PricePlanResponse> update(@CurrentCenterId UUID centerId,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody PricePlanRequest request) {
        return pricePlanService.update(centerId, id, request);
    }

    @GetMapping({"/center-offering/{centerOfferingId}", "/center-service/{centerOfferingId}"})
    @PreAuthorize("isAuthenticated()")
    public APIResponse<List<PricePlanResponse>> listByCenterOffering(@PathVariable UUID centerOfferingId) {
        return pricePlanService.listByCenterOffering(centerOfferingId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<PricePlanResponse> getById(@PathVariable UUID id) {
        return pricePlanService.getById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<Void> delete(@CurrentCenterId UUID centerId,
                                    @PathVariable UUID id) {
        return pricePlanService.delete(centerId, id);
    }
}
```

#### 4. `CenterOfferingService.java`
**Target**: `CareCatalogService/src/main/java/mss301/carecatalogservice/service/CenterOfferingService.java`
```java
package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;

import java.util.List;
import java.util.UUID;

// Dịch vụ mà trung tâm đăng ký cung cấp, dựa trên danh mục chuẩn (CatalogItem).
public interface CenterOfferingService {
    APIResponse<CenterOfferingResponse> create(UUID centerId, CenterOfferingRequest request);

    APIResponse<CenterOfferingResponse> update(UUID centerId, UUID id, CenterOfferingRequest request);

    APIResponse<List<CenterOfferingResponse>> listByCenter(UUID centerId);

    APIResponse<CenterOfferingResponse> getById(UUID id);

    APIResponse<Void> delete(UUID centerId, UUID id);

    List<ServiceQuoteResponse> quote(ServiceQuoteRequest request);
}
```

#### 5. `CenterOfferingServiceImpl.java`
**Target**: `CareCatalogService/src/main/java/mss301/carecatalogservice/service/Impl/CenterOfferingServiceImpl.java`
Add dependency and method:
```java
    CenterOfferingRepository centerOfferingRepository;
    CatalogItemRepository catalogItemRepository;
    CenterOfferingMapper centerOfferingMapper;
    CatalogQuoteService catalogQuoteService;
```
Add implementation:
```java
    @Override
    @Transactional(readOnly = true)
    public List<ServiceQuoteResponse> quote(ServiceQuoteRequest request) {
        return catalogQuoteService.quote(request);
    }
```

#### 6. Rename & Update DTOs
**Target 1**: `CareCatalogService/src/main/java/mss301/carecatalogservice/dto/request/ServiceQuoteRequest.java`  
(Delete `InternalServiceQuoteRequest.java`, write `ServiceQuoteRequest.java`)
```java
package mss301.carecatalogservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceQuoteRequest {

    @NotNull(message = "CENTER_ID_REQUIRED")
    UUID centerId;

    @NotEmpty(message = "SERVICES_REQUIRED")
    List<@Valid @NotNull(message = "SERVICES_REQUIRED") Item> items;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Item {
        @NotNull(message = "CARE_SERVICE_ID_REQUIRED")
        UUID careServiceId;

        UUID centerServiceId;

        UUID pricePlanId;
    }
}
```

**Target 2**: `CareCatalogService/src/main/java/mss301/carecatalogservice/dto/response/ServiceQuoteResponse.java`  
(Delete `InternalServiceQuoteResponse.java`, write `ServiceQuoteResponse.java`)
```java
package mss301.carecatalogservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.PricingUnit;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceQuoteResponse {

    UUID careServiceId;

    UUID centerServiceId;

    UUID pricePlanId;

    String serviceName;

    PricingUnit pricingUnit;

    Integer durationMinutes;

    BigDecimal basePrice;

    boolean active;
}
```

#### 7. `CatalogQuoteService.java`
**Target**: `CareCatalogService/src/main/java/mss301/carecatalogservice/service/CatalogQuoteService.java`
```java
package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;

import java.util.List;

public interface CatalogQuoteService {
    List<ServiceQuoteResponse> quote(ServiceQuoteRequest request);
}
```

#### 8. `CatalogQuoteServiceImpl.java`
**Target**: `CareCatalogService/src/main/java/mss301/carecatalogservice/service/Impl/CatalogQuoteServiceImpl.java`
```java
package mss301.carecatalogservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;
import mss301.carecatalogservice.entity.CatalogItem;
import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.enums.ServiceStatus;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.repository.CatalogItemRepository;
import mss301.carecatalogservice.repository.CenterOfferingRepository;
import mss301.carecatalogservice.repository.PricePlanRepository;
import mss301.carecatalogservice.service.CatalogQuoteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CatalogQuoteServiceImpl implements CatalogQuoteService {

    CatalogItemRepository catalogItemRepository;
    CenterOfferingRepository centerOfferingRepository;
    PricePlanRepository pricePlanRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ServiceQuoteResponse> quote(ServiceQuoteRequest request) {
        UUID centerId = request.getCenterId();
        List<ServiceQuoteResponse> quotes = new ArrayList<>();

        for (ServiceQuoteRequest.Item item : request.getItems()) {
            CatalogItem catalogItem = catalogItemRepository.findByIdAndDeletedAtIsNull(item.getCareServiceId())
                    .orElseThrow(() -> new AppException(ErrorCode.CARE_SERVICE_NOT_FOUND));

            if (catalogItem.getStatus() != ServiceStatus.ACTIVE) {
                throw new AppException(ErrorCode.SERVICE_NOT_ACTIVE);
            }

            CenterOffering centerOffering;
            if (item.getCenterServiceId() != null) {
                centerOffering = centerOfferingRepository.findByIdAndCenterIdAndDeletedAtIsNull(item.getCenterServiceId(), centerId)
                        .orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
                if (!centerOffering.getCatalogItem().getId().equals(catalogItem.getId())) {
                    throw new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND);
                }
            } else {
                centerOffering = centerOfferingRepository.findByCenterIdAndCatalogItemIdAndDeletedAtIsNull(centerId, catalogItem.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.CENTER_SERVICE_NOT_FOUND));
            }

            if (centerOffering.getStatus() != ServiceStatus.ACTIVE) {
                throw new AppException(ErrorCode.SERVICE_NOT_ACTIVE);
            }

            PricePlan pricePlan;
            if (item.getPricePlanId() != null) {
                pricePlan = pricePlanRepository.findByIdAndCenterOfferingIdAndDeletedAtIsNull(item.getPricePlanId(), centerOffering.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND));
                if (!Boolean.TRUE.equals(pricePlan.getIsActive())) {
                    throw new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND);
                }
            } else {
                List<PricePlan> activePlans = pricePlanRepository.findByCenterOfferingIdAndIsActiveTrueAndDeletedAtIsNull(centerOffering.getId());
                if (activePlans.isEmpty()) {
                    throw new AppException(ErrorCode.PRICE_PLAN_NOT_FOUND);
                }
                pricePlan = activePlans.getFirst();
            }

            quotes.add(ServiceQuoteResponse.builder()
                    .careServiceId(catalogItem.getId())
                    .centerServiceId(centerOffering.getId())
                    .pricePlanId(pricePlan.getId())
                    .serviceName(catalogItem.getName())
                    .pricingUnit(pricePlan.getPricingUnit())
                    .durationMinutes(pricePlan.getDurationMinutes())
                    .basePrice(pricePlan.getBasePrice())
                    .active(true)
                    .build());
        }

        return quotes;
    }
}
```

#### 9. `CareCatalogProperties.java`
**Target**: `CareCatalogService/src/main/java/mss301/carecatalogservice/config/CareCatalogProperties.java`
```java
package mss301.carecatalogservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// care-catalog.* trong application.yaml — URL/issuer bắt buộc cấu hình qua env, app fail khi khởi động nếu thiếu.
@Data
@Validated
@ConfigurationProperties("care-catalog")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareCatalogProperties {
    // URL server cho OpenAPI khi đứng sau gateway (vd /api/v1). Để trống = mặc định của springdoc.
    String openApiServerUrl = "";

    @Valid
    Jwt jwt = new Jwt();

    @Valid
    Identity identity = new Identity();

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Jwt {
        // Phải khớp chính xác claim iss do IdentityService phát (IDENTITY_JWT_ISSUER).
        @NotBlank
        String issuer;

        // JWKS của IdentityService, gọi trực tiếp (không qua gateway).
        @NotBlank
        String jwksUri;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Identity {
        // Base URL IdentityService cho Feign (không có service discovery).
        @NotBlank
        String url;
    }
}
```

#### 10. `OpenApiConfig.java`
**Target**: `CareCatalogService/src/main/java/mss301/carecatalogservice/config/swagger/OpenApiConfig.java`
```java
package mss301.carecatalogservice.config.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import mss301.carecatalogservice.config.CareCatalogProperties;
import mss301.carecatalogservice.security.CurrentCenterId;
import mss301.carecatalogservice.security.CurrentUserId;
import mss301.carecatalogservice.security.ReadOnlyCookieCsrfTokenRepository;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String CSRF_SCHEME = "csrf";
    private static final String CSRF_DESCRIPTION = "Chỉ cần khi gọi bằng cookie access_token (web qua gateway): "
            + "dán token lấy từ GET /auth/csrf của IdentityService. Gọi bằng Bearer thuần không cần.";
    private static final Set<PathItem.HttpMethod> WRITE_METHODS = Set.of(
            PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE);
    private static final List<String> DOCUMENTED_RESPONSES = List.of("200", "400", "401", "403", "404", "409", "500", "503");
    private static final List<String> GLOBAL_ERROR_RESPONSES = List.of("400", "401", "403", "404", "500", "503");

    static {
        // userId và centerId của người gọi lấy từ token, không phải tham số client gửi → ẩn khỏi Swagger.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class, CurrentCenterId.class);
    }

    @Bean
    public OpenAPI careCatalogOpenApi(CareCatalogProperties careCatalogProperties) {
        Components components = new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(CSRF_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ReadOnlyCookieCsrfTokenRepository.CSRF_HEADER)
                        .description(CSRF_DESCRIPTION));
        for (String code : DOCUMENTED_RESPONSES) {
            components.addResponses(code, new ApiResponse().description("HTTP " + code + "; business endpoints return APIResponse"));
        }
        OpenAPI openApi = new OpenAPI()
                .info(new Info().title("CareConnect CareCatalogService").version("0.0.1"))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
        // Đứng sau gateway (cắt prefix /api/v1): "Try it out" phải gọi qua prefix của gateway; vẫn giữ "/" để gọi trực tiếp.
        String gatewayServerUrl = careCatalogProperties.getOpenApiServerUrl();
        if (gatewayServerUrl != null && !gatewayServerUrl.isBlank()) {
            openApi.servers(List.of(
                    new Server().url(gatewayServerUrl).description("Qua API gateway"),
                    new Server().url("/").description("Gọi trực tiếp CareCatalogService")));
        }
        return openApi;
    }

    @Bean
    public OpenApiCustomizer globalResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            for (String code : GLOBAL_ERROR_RESPONSES) {
                operation.getResponses().putIfAbsent(code, new ApiResponse().$ref("#/components/responses/" + code));
            }
        }));
    }

    // Request ghi: bearer + csrf (csrf chỉ bắt buộc khi dùng cookie).
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

    private void addCsrfRequirement(Operation operation) {
        SecurityRequirement requirement = new SecurityRequirement()
                .addList(BEARER_SCHEME)
                .addList(CSRF_SCHEME);
        operation.setSecurity(List.of(requirement));
    }
}
```

#### 11. `application.yaml`
**Target**: `CareCatalogService/src/main/resources/application.yaml`
In lines 72-83:
```yaml
care-catalog:
  open-api-server-url: ${CARE_CATALOG_OPENAPI_SERVER_URL:}

  jwt:
    issuer: ${CARE_CATALOG_JWT_ISSUER}
    jwks-uri: ${CARE_CATALOG_JWT_JWKS_URI}

  identity:
    url: ${CARE_CATALOG_IDENTITY_URL}
```
(`internal-secret` keys removed).

#### 12. `.env.example`
**Target**: `CareCatalogService/.env.example`
Remove:
- `CARE_CATALOG_IDENTITY_INTERNAL_SECRET=` (line 25)
- Lines 29-31 (`CARE_CATALOG_INTERNAL_SECRET=`)

#### 13. `CatalogQuoteServiceTests.java`
**Target**: `CareCatalogService/src/test/java/mss301/carecatalogservice/service/CatalogQuoteServiceTests.java`
Update imports:
```java
import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;
```
Replace all references to `InternalServiceQuoteRequest` and `InternalServiceQuoteResponse` with `ServiceQuoteRequest` and `ServiceQuoteResponse`.
Example:
```java
        ServiceQuoteRequest request = ServiceQuoteRequest.builder()
                .centerId(centerId)
                .items(List.of(ServiceQuoteRequest.Item.builder()
                        .careServiceId(careServiceId)
                        .centerServiceId(centerServiceId)
                        .pricePlanId(pricePlanId)
                        .build()))
                .build();

        List<ServiceQuoteResponse> result = catalogQuoteService.quote(request);

        assertThat(result).hasSize(1);
        ServiceQuoteResponse quote = result.getFirst();
```

---

## 5. Verification Method

### 5.1 Verification Commands
The implementer will run the following commands sequentially:

1. **Compilation Verification**:
   ```powershell
   cd d:\FA26\MSS\CareConnect\BE\CareCatalogService
   .\mvnw.cmd clean compile
   ```
   **Expected result**: BUILD SUCCESS. 0 compilation errors.

2. **Test Suite Verification**:
   ```powershell
   cd d:\FA26\MSS\CareConnect\BE\CareCatalogService
   .\mvnw.cmd clean test
   ```
   **Expected result**: BUILD SUCCESS. `Tests run: 4, Failures: 0, Errors: 0, Skipped: 1`. All 3 tests in `CatalogQuoteServiceTests` pass.

3. **Purge Verification (Zero Internal Artifacts)**:
   ```powershell
   Get-ChildItem -Recurse -Filter "*Internal*" "d:\FA26\MSS\CareConnect\BE\CareCatalogService\src"
   ```
   **Expected result**: 0 files returned.

4. **Endpoint Prefix Verification**:
   Check annotations on controllers:
   - `CatalogItemController`: `@RequestMapping({"/api/v1/catalog-items", "/api/v1/care-services"})`
   - `CenterOfferingController`: `@RequestMapping("/api/v1/center-services")`
   - `PricePlanController`: `@RequestMapping("/api/v1/price-plans")`

### 5.2 Invalidation Conditions
This blueprint would require adjustments if:
- ApiGateway requires a different path structure than `/api/v1/center-services/quote` for inter-service quoting.
- Gateway routing strips the `/api/v1` prefix before forwarding (under R4, `stripPrefix` is removed across the gateway, so `/api/v1/` is preserved upstream).
