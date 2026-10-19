# Survey Report: CareCatalogService, ApiGateway, and Cross-Service Communication (R1, R2, R4, R5)

**Agent**: Explorer Survey 3  
**Working Directory**: `d:\FA26\MSS\CareConnect\BE\.agents\teamwork\explorer_survey_3`  
**Parent**: `orchestrator_1` (Conversation ID: `c390ebfb-aa07-4694-bdae-bfb529d83f93`)  
**Date**: 2026-10-18  

---

## 1. Observation

### 1.1 CareCatalogService Codebase State
- **Root Directory**: `d:\FA26\MSS\CareConnect\BE\CareCatalogService`
- **Application Class**: `src/main/java/mss301/carecatalogservice/CareCatalogServiceApplication.java`
  - Port configured: `${CARE_CATALOG_SERVER_PORT:8083}` in `src/main/resources/application.yaml:49`.
  - `@EnableFeignClients` is **NOT** present.
- **Controllers Observed**:
  1. `src/main/java/mss301/carecatalogservice/controller/CatalogItemController.java`:
     - Line 19: `@RequestMapping("/care-services")` — **Lacks `/api/v1/` prefix**.
     - Endpoints: `POST /care-services`, `PUT /care-services/{id}`, `GET /care-services`, `GET /care-services/{id}`, `DELETE /care-services/{id}`.
     - Role separation: None. Single controller handles Admin operations and public GET endpoints.
  2. `src/main/java/mss301/carecatalogservice/controller/CenterOfferingController.java`:
     - Line 20: `@RequestMapping("/center-services")` — **Lacks `/api/v1/` prefix**.
     - Endpoints: `POST /center-services`, `PUT /center-services/{id}`, `GET /center-services/center/{centerId}`, `GET /center-services/{id}`, `DELETE /center-services/{id}`.
  3. `src/main/java/mss301/carecatalogservice/controller/PricePlanController.java`:
     - Line 20: `@RequestMapping("/price-plans")` — **Lacks `/api/v1/` prefix**.
     - Endpoints: `POST /price-plans`, `PUT /price-plans/{id}`, `GET /price-plans/center-offering/{centerOfferingId}`, `GET /price-plans/{id}`, `DELETE /price-plans/{id}`.
  4. `src/main/java/mss301/carecatalogservice/controller/InternalCatalogController.java`:
     - Line 20: `@RequestMapping("/internal/center-services")`
     - Line 28: `@PostMapping("/quote")` — Handles internal quote requests for `BookingService`.
- **Security & Internal Configuration in CareCatalogService**:
  - `src/main/java/mss301/carecatalogservice/security/InternalSecurityConfig.java`:
    - Line 25: `public static final String INTERNAL_PATH = "/internal/**";`
    - Line 38: Filter chain with `InternalSecretFilter` checking header `X-Internal-Secret`.
  - `src/main/java/mss301/carecatalogservice/security/InternalSecretFilter.java`: Validates `care-catalog.internal-secret`.
  - `src/main/java/mss301/carecatalogservice/config/CareCatalogProperties.java`:
    - Line 20: `String internalSecret = "";`
    - Lines 45-53: Static inner class `Identity` with `url` and `internalSecret` (currently unused placeholder).
- **Entities Observed**:
  - `src/main/java/mss301/carecatalogservice/entity/BaseEntity.java`: `@Getter @Setter @MappedSuperclass`
  - `src/main/java/mss301/carecatalogservice/entity/CatalogItem.java`: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (table `care_services`). **Does NOT use `@Data`**.
  - `src/main/java/mss301/carecatalogservice/entity/CenterOffering.java`: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (table `center_services`). **Does NOT use `@Data`**.
  - `src/main/java/mss301/carecatalogservice/entity/PricePlan.java`: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (table `price_plans`). **Does NOT use `@Data`**.
- **Repositories Observed**:
  - `src/main/java/mss301/carecatalogservice/repository/CatalogItemRepository.java`: `public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID>`
  - `src/main/java/mss301/carecatalogservice/repository/CenterOfferingRepository.java`: `public interface CenterOfferingRepository extends JpaRepository<CenterOffering, UUID>`
  - `src/main/java/mss301/carecatalogservice/repository/PricePlanRepository.java`: `public interface PricePlanRepository extends JpaRepository<PricePlan, UUID>`
  - All repositories are pure interfaces.
- **Services Observed**:
  - `src/main/java/mss301/carecatalogservice/service/CatalogItemService.java` (interface) -> `Impl/CatalogItemServiceImpl.java` (class)
  - `src/main/java/mss301/carecatalogservice/service/CenterOfferingService.java` (interface) -> `Impl/CenterOfferingServiceImpl.java` (class)
  - `src/main/java/mss301/carecatalogservice/service/PricePlanService.java` (interface) -> `Impl/PricePlanServiceImpl.java` (class)
  - `src/main/java/mss301/carecatalogservice/service/CatalogQuoteService.java` (interface) -> `Impl/CatalogQuoteServiceImpl.java` (class)
  - All services strictly follow the interface in `service/` and implementation in `service/Impl/` pattern.
- **Mappers Observed**:
  - `MapStructConfig.java`: Configured with `componentModel = MappingConstants.ComponentModel.SPRING`, `unmappedTargetPolicy = ReportingPolicy.ERROR`, `builder = @Builder(disableBuilder = true)`.
  - `CatalogItemMapper.java`, `CenterOfferingMapper.java`, `PricePlanMapper.java`: All use `config = MapStructConfig.class`.
- **OpenFeign in CareCatalogService**:
  - No `client/` package exists; no `@FeignClient` interfaces exist.
- **Maven Build Verification**:
  - Ran `.\mvnw.cmd compile` in `CareCatalogService`: **BUILD SUCCESS** (57 source files compiled).

---

### 1.2 ApiGateway State
- **Root Directory**: `d:\FA26\MSS\CareConnect\BE\ApiGateway`
- **Application Class**: `src/main/java/mss301/apigateway/ApiGatewayApplication.java` (Spring Cloud Gateway Server MVC, port 8080).
- **Gateway Configuration**:
  - `src/main/java/mss301/apigateway/config/GatewayPaths.java`:
    - Defines `API_PREFIX = "/api/v1"` and `API_PREFIX_SEGMENTS = 2`.
    - Lines 11-13: `IDENTITY_ROUTES = { "/api/v1/auth/**", "/api/v1/users/**", "/api/v1/roles/**", "/api/v1/permissions/**" }`.
    - Lines 15-17: `CARE_RECIPIENT_ROUTES = { "/api/v1/care-recipients/**", "/api/v1/admin/care-recipients/**", "/api/v1/caregiver/care-recipients/**" }`.
    - Lines 20-23: `BOOKING_ROUTES = { "/api/v1/bookings/**", "/api/v1/center/bookings/**", "/api/v1/admin/bookings/**", "/api/v1/caregiver/occurrences/**" }`.
    - Lines 27-32: `IDENTITY_TOKEN_FLOW_ENDPOINTS = { "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/csrf", "/api/v1/auth/mobile/login", "/api/v1/auth/mobile/refresh", "/api/v1/auth/mobile/logout", "/api/v1/users/register", "/api/v1/users/register/verify", "/api/v1/users/register/resend-otp", "/api/v1/users/forgot-password", "/api/v1/users/reset-password" }`.
    - Lines 35-37: Swagger docs routes only for `identity`, `care-recipient`, `booking`.
    - **MISSING**: Completely missing routes for `ProviderService`, `CareCatalogService`, and `MediaService`.
  - `src/main/java/mss301/apigateway/config/RouteConfig.java`:
    - Only 3 service route beans: `identityRoutes()`, `careRecipientRoutes()`, `bookingRoutes()`, plus `apiDocsRoutes()`.
    - Every route currently applies `.before(stripPrefix(GatewayPaths.API_PREFIX_SEGMENTS))`.
  - `src/main/java/mss301/apigateway/config/GatewayProperties.java`:
    - Only contains `identityUrl`, `careRecipientUrl`, `bookingUrl`, `allowedOrigins`, `jwt`.
    - Missing properties: `providerUrl`, `careCatalogUrl`, `mediaUrl`.
  - `src/main/resources/application.yaml`:
    - Contains env mappings: `GATEWAY_IDENTITY_URL`, `GATEWAY_CARE_RECIPIENT_URL`, `GATEWAY_BOOKING_URL`, `GATEWAY_ALLOWED_ORIGINS`.
    - Missing env mappings: `GATEWAY_PROVIDER_URL`, `GATEWAY_CARE_CATALOG_URL`, `GATEWAY_MEDIA_URL`.
  - `src/main/java/mss301/apigateway/filter/InternalPathBlockFilter.java`:
    - Lines 34-62: Servlet filter `@Order(Ordered.HIGHEST_PRECEDENCE + 1)` matching `(^|/)internal(/|;|$)` and returning 404 `PATH_NOT_FOUND`.
  - `src/main/java/mss301/apigateway/filter/GatewayRequestHeaders.java`:
    - Strips client-supplied `x-user-*`, `X-Internal-Secret`, and `Origin`.
    - Appends `X-User-Id` and `X-User-Roles` from verified JWT.
    - Preserves `Authorization`, `Cookie`, `X-XSRF-TOKEN`, and `X-Request-Id`.
- **Maven Build Verification**:
  - Ran `.\mvnw.cmd compile` in `ApiGateway`: **BUILD SUCCESS**.

---

### 1.3 MediaService State
- **Root Directory**: `d:\FA26\MSS\CareConnect\BE\MediaService`
- **Application Class**: `src/main/java/mss301/mediaservice/MediaServiceApplication.java`
  - Port configured: `${MEDIA_SERVER_PORT:8086}` in `application.yaml:2`.
  - S3 / Cloudflare R2 integration via AWS SDK 2.55.14 (`software.amazon.awssdk:s3`).
- **Controllers Observed**:
  - `src/main/java/mss301/mediaservice/controller/InternalMediaController.java`:
    - Line 27: `@RequestMapping("/internal/media")`
    - Endpoints: `POST /internal/media` (upload multipart), `GET /internal/media/{mediaFileId}`, `GET /internal/media/{mediaFileId}/download-url`, `DELETE /internal/media/{mediaFileId}`.
    - **No public controller exists!** `InternalMediaController` is the only controller in `MediaService`.
- **Security & Dependencies in MediaService**:
  - `src/main/java/mss301/mediaservice/security/InternalSecurityConfig.java`: Chain `@Order(1)` matching `/internal/**` with `InternalSecretFilter`.
  - `src/main/java/mss301/mediaservice/security/SecurityConfig.java`: Line 31 has `anyRequest().denyAll()` on the default chain!
  - `pom.xml`: Lacks `spring-boot-starter-security-oauth2-resource-server`!
  - `src/main/java/mss301/mediaservice/config/MediaProperties.java`: Lacks `jwt.issuer` and `jwt.jwks-uri`.
- **Maven Build Verification**:
  - Ran `.\mvnw.cmd compile` in `MediaService`: **BUILD SUCCESS** (28 source files compiled).

---

### 1.4 Cross-Service OpenFeign Clients Across ALL Services
Across the entire repository, exactly 7 `@FeignClient` interfaces exist across 3 consuming services (`CareRecipientService`, `ProviderService`, `BookingService`):

| Consuming Service | Client Class | Feign Client Name & Target URL Property | Current Called Endpoint(s) | Current Authentication Mechanism |
|---|---|---|---|---|
| **CareRecipientService** | `client/IdentityClient.java` | `name = "identity"`, `url = "${care-recipient.identity.url}"` | `GET /internal/users/{userId}`<br>`GET /internal/users/by-email`<br>`POST /internal/users/batch` | `IdentityClientConfig`: attaches `X-Internal-Secret` from `care-recipient.identity.internal-secret` |
| **CareRecipientService** | `client/BookingClient.java` | `name = "booking"`, `url = "${care-recipient.booking.url}"` | `GET /internal/bookings/caregiver-access` | `BookingClientConfig`: attaches `X-Internal-Secret` from `care-recipient.booking.internal-secret` |
| **ProviderService** | `client/IdentityClient.java` | `name = "identity"`, `url = "${provider.identity.url}"` | `GET /internal/users/{userId}` | `IdentityClientConfig`: attaches `X-Internal-Secret` from `provider.identity.internal-secret` |
| **ProviderService** | `client/MediaClient.java` | `name = "media"`, `url = "${provider.media.url}"` | `POST /internal/media`<br>`GET /internal/media/{mediaFileId}`<br>`GET /internal/media/{mediaFileId}/download-url`<br>`DELETE /internal/media/{mediaFileId}` | `MediaClientConfig`: attaches `X-Internal-Secret` from `provider.media.internal-secret` |
| **BookingService** | `client/IdentityClient.java` | `name = "identity"`, `url = "${booking.identity.url}"` | `POST /internal/users/batch` | `InternalClientHeaders.interceptor`: attaches `X-Internal-Secret` from `booking.identity.internal-secret` |
| **BookingService** | `client/CareRecipientClient.java` | `name = "care-recipient"`, `url = "${booking.care-recipient.url}"` | `GET /internal/care-recipients/{recipientId}/access`<br>`GET /internal/care-recipients/{recipientId}/snapshot` | `InternalClientHeaders.interceptor`: attaches `X-Internal-Secret` from `booking.care-recipient.internal-secret` |
| **BookingService** | `client/ProviderClient.java` | `name = "provider"`, `url = "${booking.provider.url}"` | `GET /internal/centers/{centerId}`<br>`GET /internal/caregivers/{caregiverId}` | `InternalClientHeaders.interceptor`: attaches `X-Internal-Secret` from `booking.provider.internal-secret` |
| **BookingService** | `client/CareCatalogClient.java` | `name = "care-catalog"`, `url = "${booking.care-catalog.url}"` | `POST /internal/center-services/quote` | `InternalClientHeaders.interceptor`: attaches `X-Internal-Secret` from `booking.care-catalog.internal-secret` |

---

## 2. Logic Chain

### 2.1 CareCatalogService Refactoring Logic
1. **API Versioning (R3)**:
   - *Observation*: `CatalogItemController` (`/care-services`), `CenterOfferingController` (`/center-services`), `PricePlanController` (`/price-plans`) lack `/api/v1/` prefix.
   - *Inference*: Per R3, every controller must have `/api/v1/` prefix. Therefore, update `@RequestMapping` to `/api/v1/care-services`, `/api/v1/center-services`, and `/api/v1/price-plans`.
2. **Decommissioning `/internal` & Secret Removal (R4)**:
   - *Observation*: `InternalCatalogController.java` (`POST /internal/center-services/quote`), `InternalSecurityConfig.java`, and `InternalSecretFilter.java` exist in `CareCatalogService`.
   - *Inference*: Per R4, remove `InternalCatalogController.java`, `InternalSecurityConfig.java`, `InternalSecretFilter.java`, and `CareCatalogProperties.internalSecret`.
   - *Quote Endpoint Migration*: The quote calculation logic in `CatalogQuoteService` / `CatalogQuoteServiceImpl` remains necessary for `BookingService`. The endpoint must be migrated to a public domain endpoint: `@PostMapping("/quote")` inside `CenterOfferingController` (i.e. `POST /api/v1/center-services/quote`) or `CatalogItemController`. Authenticated users with valid JWT (forwarded by Feign from `BookingService`) will access this endpoint.
3. **Entity and Mapper Compliance (R5)**:
   - *Observation*: `CatalogItem`, `CenterOffering`, `PricePlan`, and `BaseEntity` strictly use `@Getter @Setter` and avoid `@Data`. Mappers use MapStruct 1.6.3 with `@MapperConfig(MapStructConfig.class)`.
   - *Inference*: No entity Lombok changes or mapper architectural overhauls are needed in `CareCatalogService`.

### 2.2 ApiGateway Routing & Prefix Stripping Architecture (R4)
1. **The Critical Prefix-Stripping Architectural Conflict**:
   - *Observation*: `RouteConfig.java` in `ApiGateway` currently uses `.before(stripPrefix(GatewayPaths.API_PREFIX_SEGMENTS))` on each route. This strips `/api/v1` so that requests to `/api/v1/users` were forwarded as `/users`.
   - *Observation*: Per R3, all backend controllers across all 6 services must explicitly adopt `@RequestMapping("/api/v1/...")`.
   - *Inference*: If ApiGateway retains `stripPrefix(2)`, an incoming request to `http://localhost:8080/api/v1/users/me` would be stripped to `/me` (or `/users/me` if path was `/api/v1/users/me`), causing a 404 Mismatch against upstream controllers annotated with `@RequestMapping("/api/v1/...")`!
   - *Conclusion*: In `RouteConfig.java`, **`stripPrefix(GatewayPaths.API_PREFIX_SEGMENTS)` MUST BE REMOVED from all routes** so that the `/api/v1/...` prefix is preserved during forwarding. This ensures a clean, uniform URL structure where client, gateway, and microservice controllers all speak `/api/v1/...`.
2. **Missing Routes & RouteConfig Consolidation**:
   - *Observation*: `ApiGateway` only routes `IdentityService`, `CareRecipientService`, and `BookingService`. `ProviderService`, `CareCatalogService`, and `MediaService` are completely absent from `GatewayPaths`, `RouteConfig`, and `GatewayProperties`.
   - *Inference*: To support all 6 services via ApiGateway:
     - `GatewayPaths`: Add `PROVIDER_ROUTES`, `CARE_CATALOG_ROUTES`, `MEDIA_ROUTES`.
     - `GatewayProperties`: Add `providerUrl`, `careCatalogUrl`, `mediaUrl`.
     - `application.yaml`: Add `${GATEWAY_PROVIDER_URL}`, `${GATEWAY_CARE_CATALOG_URL}`, `${GATEWAY_MEDIA_URL}`.
     - `RouteConfig`: Add `providerRoutes()`, `careCatalogRoutes()`, `mediaRoutes()`, and update `apiDocsRoutes()` to aggregate Swagger from all 6 services.
3. **Internal Filter Removal (R4)**:
   - *Observation*: `InternalPathBlockFilter.java` blocks `/internal/**` at the gateway.
   - *Inference*: Per R4, delete `InternalPathBlockFilter.java`. In addition, update `ApiGatewayTests.java` which currently asserts that `InternalPathBlockFilter` blocks `/internal` paths.
4. **Token Flow & Public Endpoints (R3)**:
   - *Observation*: `GatewayPaths.IDENTITY_TOKEN_FLOW_ENDPOINTS` currently lists `/api/v1/users/register`, `/api/v1/users/forgot-password`, etc.
   - *Inference*: Update these to `/api/v1/auth/register`, `/api/v1/auth/forgot-password`, etc., per R3.

### 2.3 MediaService Evolution to Full Microservice (R4)
1. **Current Vulnerability / Architecture Defect**:
   - *Observation*: `MediaService` currently only exposes `InternalMediaController` (`/internal/media/**`) protected by `X-Internal-Secret`. Its main `SecurityConfig` has `anyRequest().denyAll()`. It lacks `spring-boot-starter-security-oauth2-resource-server`.
   - *Inference*: When `/internal/**` and `X-Internal-Secret` are removed, `MediaService` will be unreachable unless:
     - `spring-boot-starter-security-oauth2-resource-server` is added to `MediaService/pom.xml`.
     - Standard JWT resource server security is added (`JwtConfig.java`, `JwtAuthorityConverter.java`, `SecurityErrorHandler.java`).
     - `InternalMediaController` is renamed/refactored into `MediaController` with `@RequestMapping("/api/v1/media")`.
     - ApiGateway routes `/api/v1/media/**` to `MediaService`.

### 2.4 Cross-Service Feign Clients & JWT Forwarding (R4)
1. **Target URL Redirection**:
   - *Observation*: All 7 Feign clients currently point directly to microservice ports (e.g. `${care-recipient.identity.url:http://localhost:8081}`).
   - *Inference*: Per R4, all Feign calls must be routed through ApiGateway (`http://localhost:8080`).
   - *Implementation*: Change the Feign URL properties in `application.yaml` / `.env.example` to point to ApiGateway: e.g. `${gateway.url:http://localhost:8080}` or per-service config pointing to gateway port `8080`.
2. **Endpoint Mapping Updates**:
   - *Observation*: All 7 Feign clients call `/internal/**` paths.
   - *Inference*: Update Feign client interfaces to call the corresponding public `/api/v1/...` paths (detailed in table below).
3. **Authorization Header Forwarding via RequestInterceptor**:
   - *Observation*: Existing client configs use custom interceptors attaching `X-Internal-Secret`.
   - *Inference*: Replace all `X-Internal-Secret` interceptors with a unified `FeignAuthForwardingInterceptor` that extracts the Bearer token from the incoming caller's context (`SecurityContextHolder` / `RequestContextHolder`) and attaches `Authorization: Bearer <token>` + `X-Request-Id`.

---

## 3. Recommended Implementation Specifications

### 3.1 Mapping Table: Internal APIs to Public `/api/v1/...` APIs

| Service | Old Internal Path & Method | Target Public Path & Method | Controller Target Class | Access Permission |
|---|---|---|---|---|
| **IdentityService** | `GET /internal/users/{userId}` | `GET /api/v1/users/{id}` | `UserController` | `@PreAuthorize("isAuthenticated()")` |
| **IdentityService** | `GET /internal/users/by-email?email=...` | `GET /api/v1/users/by-email?email=...` | `UserController` | `@PreAuthorize("isAuthenticated()")` |
| **IdentityService** | `POST /internal/users/batch` | `POST /api/v1/users/batch` | `UserController` | `@PreAuthorize("isAuthenticated()")` |
| **CareRecipientService** | `GET /internal/care-recipients/{id}/snapshot` | `GET /api/v1/care-recipients/{id}/snapshot` | `CareRecipientController` | `@PreAuthorize("isAuthenticated()")` |
| **CareRecipientService** | `GET /internal/care-recipients/{id}/access?userId=...&minLevel=...` | `GET /api/v1/care-recipients/{id}/access?userId=...&minLevel=...` | `CareRecipientController` | `@PreAuthorize("isAuthenticated()")` |
| **CareRecipientService** | `POST /internal/care-recipients/batch` | `POST /api/v1/care-recipients/batch` | `CareRecipientController` | `@PreAuthorize("isAuthenticated()")` |
| **ProviderService** | `GET /internal/centers/{centerId}` | `GET /api/v1/care-centers/{centerId}` | `CareCenterController` | `@PreAuthorize("isAuthenticated()")` |
| **ProviderService** | `GET /internal/caregivers/{caregiverId}` | `GET /api/v1/caregivers/{caregiverId}` | `CaregiverController` | `@PreAuthorize("isAuthenticated()")` |
| **ProviderService** | `GET /internal/caregivers/by-user/{userId}` | `GET /api/v1/caregivers/by-user/{userId}` | `CaregiverController` | `@PreAuthorize("isAuthenticated()")` |
| **CareCatalogService** | `POST /internal/center-services/quote` | `POST /api/v1/center-services/quote` | `CenterOfferingController` | `@PreAuthorize("isAuthenticated()")` |
| **BookingService** | `GET /internal/bookings/caregiver-access?caregiverUserId=...&recipientId=...` | `GET /api/v1/bookings/caregiver-access?caregiverUserId=...&recipientId=...` | `BookingController` | `@PreAuthorize("isAuthenticated()")` |
| **BookingService** | `POST /internal/bookings/{id}/deposit-paid` | `POST /api/v1/bookings/{id}/deposit-paid` | `BookingController` | `@PreAuthorize("hasRole('ADMIN')")` |
| **BookingService** | `GET /internal/bookings/occurrences/{id}` | `GET /api/v1/booking-occurrences/{id}` | `BookingOccurrenceController` | `@PreAuthorize("isAuthenticated()")` |
| **MediaService** | `POST /internal/media` | `POST /api/v1/media` | `MediaController` | `@PreAuthorize("isAuthenticated()")` |
| **MediaService** | `GET /internal/media/{mediaFileId}` | `GET /api/v1/media/{mediaFileId}` | `MediaController` | `@PreAuthorize("isAuthenticated()")` |
| **MediaService** | `GET /internal/media/{mediaFileId}/download-url` | `GET /api/v1/media/{mediaFileId}/download-url` | `MediaController` | `@PreAuthorize("isAuthenticated()")` |
| **MediaService** | `DELETE /internal/media/{mediaFileId}` | `DELETE /api/v1/media/{mediaFileId}` | `MediaController` | `@PreAuthorize("isAuthenticated()")` |

---

### 3.2 Standard Feign RequestInterceptor Template
For each consuming service (`CareRecipientService`, `ProviderService`, `BookingService`), implement a standard `FeignAuthForwardingInterceptor`:

```java
package mss301.<service>.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import mss301.<service>.config.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class FeignAuthForwardingInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 1. Forward Authorization: Bearer <token>
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            template.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuth.getToken().getTokenValue());
        } else if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && !authHeader.isBlank()) {
                template.header(HttpHeaders.AUTHORIZATION, authHeader);
            }
        }

        // 2. Forward X-Request-Id from MDC
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            template.header(RequestIdFilter.HEADER, requestId);
        }
    }
}
```

---

### 3.3 ApiGateway Configuration Blueprint for All 6 Services

#### `GatewayPaths.java`
```java
package mss301.apigateway.config;

public final class GatewayPaths {
    public static final String API_PREFIX = "/api/v1";

    public static final String[] IDENTITY_ROUTES = {
            "/api/v1/auth/**", "/api/v1/users/**", "/api/v1/roles/**", "/api/v1/permissions/**"
    };

    public static final String[] CARE_RECIPIENT_ROUTES = {
            "/api/v1/care-recipients/**"
    };

    public static final String[] BOOKING_ROUTES = {
            "/api/v1/bookings/**", "/api/v1/booking-occurrences/**"
    };

    public static final String[] PROVIDER_ROUTES = {
            "/api/v1/care-centers/**", "/api/v1/caregivers/**", "/api/v1/work-shifts/**",
            "/api/v1/certificates/**", "/api/v1/provider-media/**", "/api/v1/catalog/caregivers/**"
    };

    public static final String[] CARE_CATALOG_ROUTES = {
            "/api/v1/care-services/**", "/api/v1/center-services/**", "/api/v1/price-plans/**"
    };

    public static final String[] MEDIA_ROUTES = {
            "/api/v1/media/**"
    };

    public static final String[] IDENTITY_TOKEN_FLOW_ENDPOINTS = {
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/csrf",
            "/api/v1/auth/mobile/login", "/api/v1/auth/mobile/refresh", "/api/v1/auth/mobile/logout",
            "/api/v1/auth/register", "/api/v1/auth/register/verify", "/api/v1/auth/register/resend-otp",
            "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password"
    };

    public static final String IDENTITY_API_DOCS = "/api-docs/identity";
    public static final String CARE_RECIPIENT_API_DOCS = "/api-docs/care-recipient";
    public static final String BOOKING_API_DOCS = "/api-docs/booking";
    public static final String PROVIDER_API_DOCS = "/api-docs/provider";
    public static final String CARE_CATALOG_API_DOCS = "/api-docs/care-catalog";
    public static final String MEDIA_API_DOCS = "/api-docs/media";

    public static final String[] DOCUMENTATION_ENDPOINTS = {
            "/api-docs/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health/**"
    };

    private GatewayPaths() {}
}
```

#### `RouteConfig.java`
```java
package mss301.apigateway.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.apigateway.filter.GatewayRequestHeaders;
import mss301.apigateway.filter.RequestIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.removeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteConfig {

    static String OPENAPI_PATH = "/v3/api-docs";
    GatewayProperties gatewayProperties;

    @Bean
    public RouterFunction<ServerResponse> identityRoutes() {
        return route("identity")
                .route(path(GatewayPaths.IDENTITY_ROUTES), http())
                .before(uri(gatewayProperties.getIdentityUrl()))
                .before(GatewayRequestHeaders.sanitize())
                .after(removeResponseHeader(RequestIdFilter.HEADER))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> careRecipientRoutes() {
        return route("care-recipient")
                .route(path(GatewayPaths.CARE_RECIPIENT_ROUTES), http())
                .before(uri(gatewayProperties.getCareRecipientUrl()))
                .before(GatewayRequestHeaders.sanitize())
                .after(removeResponseHeader(RequestIdFilter.HEADER))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> bookingRoutes() {
        return route("booking")
                .route(path(GatewayPaths.BOOKING_ROUTES), http())
                .before(uri(gatewayProperties.getBookingUrl()))
                .before(GatewayRequestHeaders.sanitize())
                .after(removeResponseHeader(RequestIdFilter.HEADER))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> providerRoutes() {
        return route("provider")
                .route(path(GatewayPaths.PROVIDER_ROUTES), http())
                .before(uri(gatewayProperties.getProviderUrl()))
                .before(GatewayRequestHeaders.sanitize())
                .after(removeResponseHeader(RequestIdFilter.HEADER))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> careCatalogRoutes() {
        return route("care-catalog")
                .route(path(GatewayPaths.CARE_CATALOG_ROUTES), http())
                .before(uri(gatewayProperties.getCareCatalogUrl()))
                .before(GatewayRequestHeaders.sanitize())
                .after(removeResponseHeader(RequestIdFilter.HEADER))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mediaRoutes() {
        return route("media")
                .route(path(GatewayPaths.MEDIA_ROUTES), http())
                .before(uri(gatewayProperties.getMediaUrl()))
                .before(GatewayRequestHeaders.sanitize())
                .after(removeResponseHeader(RequestIdFilter.HEADER))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> apiDocsRoutes() {
        RouterFunction<ServerResponse> identityDocs = route("identity-api-docs")
                .route(path(GatewayPaths.IDENTITY_API_DOCS), http())
                .before(uri(gatewayProperties.getIdentityUrl()))
                .before(setPath(OPENAPI_PATH))
                .before(GatewayRequestHeaders.sanitize())
                .build();
        RouterFunction<ServerResponse> careRecipientDocs = route("care-recipient-api-docs")
                .route(path(GatewayPaths.CARE_RECIPIENT_API_DOCS), http())
                .before(uri(gatewayProperties.getCareRecipientUrl()))
                .before(setPath(OPENAPI_PATH))
                .before(GatewayRequestHeaders.sanitize())
                .build();
        RouterFunction<ServerResponse> bookingDocs = route("booking-api-docs")
                .route(path(GatewayPaths.BOOKING_API_DOCS), http())
                .before(uri(gatewayProperties.getBookingUrl()))
                .before(setPath(OPENAPI_PATH))
                .before(GatewayRequestHeaders.sanitize())
                .build();
        RouterFunction<ServerResponse> providerDocs = route("provider-api-docs")
                .route(path(GatewayPaths.PROVIDER_API_DOCS), http())
                .before(uri(gatewayProperties.getProviderUrl()))
                .before(setPath(OPENAPI_PATH))
                .before(GatewayRequestHeaders.sanitize())
                .build();
        RouterFunction<ServerResponse> careCatalogDocs = route("care-catalog-api-docs")
                .route(path(GatewayPaths.CARE_CATALOG_API_DOCS), http())
                .before(uri(gatewayProperties.getCareCatalogUrl()))
                .before(setPath(OPENAPI_PATH))
                .before(GatewayRequestHeaders.sanitize())
                .build();
        RouterFunction<ServerResponse> mediaDocs = route("media-api-docs")
                .route(path(GatewayPaths.MEDIA_API_DOCS), http())
                .before(uri(gatewayProperties.getMediaUrl()))
                .before(setPath(OPENAPI_PATH))
                .before(GatewayRequestHeaders.sanitize())
                .build();

        return identityDocs.and(careRecipientDocs).and(bookingDocs)
                .and(providerDocs).and(careCatalogDocs).and(mediaDocs);
    }
}
```

---

## 4. Caveats

1. **Gateway stripPrefix Removal Dependency**:
   - Removing `.before(stripPrefix(GatewayPaths.API_PREFIX_SEGMENTS))` at ApiGateway is directly contingent on upstream services updating their `@RequestMapping` to include `/api/v1`. If gateway is updated before upstream services add `/api/v1`, requests will 404. Both must be updated synchronously during the refactoring milestone.
2. **MediaService OAuth2 Dependency**:
   - `MediaService` currently lacks `spring-boot-starter-security-oauth2-resource-server` and has no JWKS URI or issuer configuration. When removing `InternalSecurityConfig`, `MediaService` must be furnished with standard resource server dependencies and configuration.
3. **Public Unauthenticated Browsing of Catalog / Caregivers**:
   - In ApiGateway's `SecurityConfig.java`, `.anyRequest().authenticated()` blocks all unauthenticated requests except `IDENTITY_TOKEN_FLOW_ENDPOINTS` and Swagger. If the business requirement allows guest users to view care services (`GET /api/v1/care-services/**`) or caregiver catalogs (`GET /api/v1/catalog/caregivers/**`) without logging in, those specific paths must be added to gateway's public endpoints.
4. **ApiGateway Test Assertions**:
   - `ApiGatewayTests.java` contains unit tests asserting that `/internal` paths are rejected by `InternalPathBlockFilter`. Once the filter is removed per R4, those test cases must be removed or adapted.

---

## 5. Conclusion

1. **CareCatalogService**:
   - Controllers need `/api/v1/` prefix addition.
   - `InternalCatalogController`, `InternalSecurityConfig`, and `InternalSecretFilter` must be removed.
   - `POST /internal/center-services/quote` must be relocated to `POST /api/v1/center-services/quote` under `CenterOfferingController`.
   - Entity and MapStruct mapper implementations are already 100% compliant with R5 (using `@Getter @Setter`, no `@Data`).
   - Clean compilation verified.
2. **ApiGateway**:
   - Must route all 6 services (`IdentityService`, `CareRecipientService`, `BookingService`, `ProviderService`, `CareCatalogService`, and `MediaService`).
   - Must remove `stripPrefix(2)` so `/api/v1` reaches upstream controllers directly.
   - Must delete `InternalPathBlockFilter.java`.
   - Must update `IDENTITY_TOKEN_FLOW_ENDPOINTS` to `/api/v1/auth/**`.
3. **MediaService**:
   - Is an active, functional service on port 8086 with R2 integration, but solely equipped with `/internal/media` and `denyAll()` on default chain.
   - Requires conversion to a standard resource server with `spring-boot-starter-security-oauth2-resource-server` and `MediaController` (`/api/v1/media/**`).
4. **Cross-Service OpenFeign Communication**:
   - Exactly 7 Feign clients exist in `CareRecipientService` (2), `ProviderService` (2), and `BookingService` (4).
   - Target URLs must be pointed to ApiGateway (`http://localhost:8080`).
   - Paths must be converted from `/internal/**` to `/api/v1/**`.
   - All client configurations must replace `X-Internal-Secret` with `FeignAuthForwardingInterceptor` forwarding caller context Bearer JWT and `X-Request-Id`.

---

## 6. Verification Method

1. **Verify Maven Compilation**:
   ```powershell
   cd d:\FA26\MSS\CareConnect\BE\CareCatalogService; .\mvnw.cmd compile
   cd d:\FA26\MSS\CareConnect\BE\ApiGateway; .\mvnw.cmd compile
   cd d:\FA26\MSS\CareConnect\BE\MediaService; .\mvnw.cmd compile
   ```
2. **Verify Route and Feign Search**:
   ```powershell
   # Ensure no @FeignClient references /internal
   rg "@FeignClient" d:\FA26\MSS\CareConnect\BE
   rg "\"/internal" d:\FA26\MSS\CareConnect\BE
   ```
3. **Invalidation Conditions**:
   - Upstream controllers fail to add `/api/v1` while ApiGateway drops `stripPrefix` -> causes HTTP 404 on all routed calls.
   - Feign client fails to inject `Authorization: Bearer <token>` -> causes HTTP 401 UNAUTHENTICATED on inter-service calls.
   - `MediaService` fails to configure JWKS decoder -> causes HTTP 401 on media operations.
