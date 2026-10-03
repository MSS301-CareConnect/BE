# PLAN — CareRecipientService + ApiGateway + Internal API IdentityService

> Trạng thái: **HOÀN THÀNH bước 1–4 (2026-10-03)**, chưa commit (chờ review). Ngày khảo sát: 2026-10-03.
> Quyết định đã chốt §5; nợ kỹ thuật §6; kết quả kiểm thử + e2e §7; điểm lệch so với plan §8.
> Nguồn quy ước: `AGENTS.md` (gốc repo). Khi yêu cầu task mâu thuẫn với `AGENTS.md`, plan này **đề xuất theo
> `AGENTS.md`** và liệt kê mâu thuẫn ở §5 để chốt.

---

## 1. Hiện trạng (kết quả khảo sát)

### 1.1 Repo & build
- **Multi-project trong một git repo** (`BE/`), không có parent pom/aggregator: mỗi service là Maven project độc lập
  có `mvnw` riêng, parent `spring-boot-starter-parent`, import BOM `spring-cloud-dependencies`.
- `IdentityService/` — hoàn chỉnh. `ApiGateway/`, `CareRecipientService/` — **skeleton Spring Initializr chưa
  commit** (chỉ có `*Application.java`, `application.properties`, test context-load, `.gitignore`, `HELP.md`).
- `.gitignore` chung ở gốc (ignore `.env`, `keys/`, `target/`…). Theo `AGENTS.md` service mới không cần
  `.gitignore` riêng.
- FE (`../FE-careconnect`): Vite proxy `/api` → `API_PROXY_TARGET` và **cắt prefix `/api`** (FE gọi
  `/api/auth/login` → backend `/auth/login`).

### 1.2 Phiên bản
| | Giá trị |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 (cả 3 project) |
| Spring Cloud | BOM `2025.1.3` (gateway 5.0.3, circuitbreaker 5.0.3 — đã có trong `~/.m2`) |
| Skeleton gateway | `spring-cloud-starter-gateway-server-webmvc` (**Gateway Server MVC**, servlet — không phải bản WebFlux) + springdoc webmvc-ui 3.1.0 |
| Skeleton care-recipient | JPA, Validation, WebMVC, springdoc 3.1.0, OpenFeign, PostgreSQL, Lombok (chưa có MapStruct, Security) |

### 1.3 Kiến trúc package & quy ước (IdentityService)
- **Layered**, base package `mss301.<service>`: `config/` (+`swagger/`), `security/` (chỉ cấu hình/adapter),
  `controller/`, `dto/request`, `dto/response`, `entity/`, `enums/`, `exception/`, `mapper/`, `repository/`,
  `service/` + `service/Impl/`, `util/`, `validation/`, `client/` (khi cần).
- Lombok: service/controller `@RequiredArgsConstructor` + `@FieldDefaults(PRIVATE, makeFinal = true)` + `@Slf4j`;
  field đặt tên đầy đủ (`userRepository`). DTO `@Data @Builder @AllArgsConstructor @NoArgsConstructor
  @FieldDefaults(PRIVATE)`. Entity `@Getter @Setter @Builder…` extends `BaseEntity` (UUID `@UuidGenerator`,
  `createdAt/updatedAt/deletedAt` kiểu `Instant` ↔ `timestamptz`), `@AttributeOverride` đổi tên cột PK.
- **MapStruct 1.6.3** (`componentModel = "spring"`, `unmappedTargetPolicy = ERROR`), một mapper / domain, mọi
  entity↔DTO qua mapper (kể cả entity liên kết, audit).
- Controller mỏng: `return xxxService.method(...)`; service đứng sau controller trả `APIResponse<T>`;
  `@CurrentUserId UUID actorId` (argument resolver) cho người gọi; `@PreAuthorize` theo role + permission.
- Enum lưu `@Enumerated(STRING)` + CHECK constraint trong SQL.

### 1.4 Response & lỗi
- `APIResponse<T>` `{code, message, result}`, `@JsonInclude(NON_NULL)`, `APIResponse.success(msg[, result])`.
- `AppException(ErrorCode)`; `ErrorCode` enum `(code, message tiếng Việt, HttpStatus)`; dải Identity
  `1000–1999` + `401/403/404/9999` dùng chung.
- `GlobalExceptionHandler`: AppException, validation (`message` = key `ErrorCode`, key lạ → `INVALID_KEY`),
  body không đọc được/type mismatch → `INVALID_REQUEST`, `DataIntegrityViolation` theo tên constraint,
  `DataAccessException` → 503, còn lại → 9999.
- `SecurityErrorHandler` (entry point + access denied + failure handler) ghi `APIResponse` bằng `ObjectMapper`
  (`tools.jackson` — Jackson 3): 401 / 403 / 403 `CSRF_INVALID` 1111 / 503 `AUTH_DEPENDENCY_UNAVAILABLE` 1105.
- `spring.jackson.deserialization.fail-on-unknown-properties: true`.
- Danh sách phân trang hiện trả `List<T>` (không có tổng số) — `GET /users?page&size`.

### 1.5 JWT
- **RS256**, private key PEM PKCS8 chỉ ở IdentityService (`IDENTITY_JWT_PRIVATE_KEY=file:./keys/private.pem`),
  public key công bố **JWKS** `GET /.well-known/jwks.json` (`kid = IDENTITY_JWT_KEY_ID`).
- Claim: `sub` = userId UUID, `iss`, `aud` = **một** giá trị `IDENTITY_JWT_AUDIENCE` (`careconnect-identity`), `iat`,
  `exp`, `jti`, `token_type` (`access`/`refresh`), `authz_version`; access thêm `roles[]`, `permissions[]`,
  `center_id` (nếu có). **Không có claim email.**
- Endpoint: `POST /auth/login|refresh|logout` (web, cookie `access_token`/`refresh_token` HttpOnly path `/` +
  CSRF `XSRF-TOKEN`/`X-XSRF-TOKEN`), `POST /auth/mobile/login|refresh|logout` (body/bearer), `POST /auth/logout-all`,
  `GET /auth/csrf`. **Không có introspect.** Refresh `jti` + blacklist trên Redis của Identity.

### 1.6 Spring Security (Identity)
- OAuth2 Resource Server (`JwtDecoder` RS256 + validator iss/aud/exp/iat/jti/sub UUID/authz_version; `@Primary`
  decoder chỉ nhận `token_type=access`). Bearer resolver đọc header **hoặc** cookie `access_token`, bỏ qua token ở
  các endpoint token-flow.
- `IdentityAuthenticationConverter`: kiểm blacklist + user ACTIVE + `authz_version`, nạp quyền **hiện hành** từ DB →
  `ROLE_<code>` + mã permission. `@EnableMethodSecurity`.
- CSRF cookie-based, bắt buộc cho request ghi có cookie; bearer thuần / `/auth/mobile/*` miễn. CORS allowlist
  `IDENTITY_ALLOWED_ORIGINS` + credentials, cấu hình trong `SecurityConfig`.
- Permission catalog: `user:read`, `user:manage`, `manager:create`, `caregiver:create`, `role:manage`.

### 1.7 DB / migration / profile
- PostgreSQL 17, **không Flyway/Liquibase**: `schema/identity.sql` áp dụng tay cho DB mới, change script ở
  `schema/changes/`; `ddl-auto: validate`, `sql.init.mode: never`, `hibernate.jdbc.time_zone: UTC`, `timestamptz`.
- **Một file `application.yaml`, không có profile**; mọi giá trị đọc từ env (`IDENTITY_*`), `.env` nạp bằng
  `scripts/Start-Dev.ps1`. Redis 7 (refresh/blacklist/OTP).

### 1.8 Hạ tầng
- **Không có** Eureka/Consul, config server, message broker. `docker-compose.yml` **riêng từng service**
  (Identity: PostgreSQL `127.0.0.1:5441`, Redis `127.0.0.1:6381`). Port Identity `8081`. Gateway: `AGENTS.md`
  ghi "đã chốt sẽ có, chưa tạo".

### 1.9 OpenFeign
- Identity chỉ **khai báo dependency**, không có `@EnableFeignClients`, không có client nào. Care-recipient skeleton
  cũng mới khai báo dependency.

### 1.10 Lombok / Swagger / Test
- Lombok + MapStruct (annotation processor khai báo trong `maven-compiler-plugin`).
- springdoc 3.1.0: `OpenApiConfig` (bearer scheme, scheme `csrf`, global 400/401/403/500/503, ẩn `@CurrentUserId`),
  `@Tag` + `@SecurityRequirements({})`, **không** `@Operation`.
- Test: JUnit 5 + Spring Boot Test + MockMvc + Mockito, **H2 (MODE=PostgreSQL) `create-drop`** mặc định; tùy chọn
  PostgreSQL/Redis thật bằng container riêng + cờ `-Didentity.testing.postgres=true`. Không có Testcontainers.

---

## 2. Thiết kế đề xuất

### 2.1 Port, env, error code
| Service | Port | Env prefix | Dải ErrorCode | DB dev |
|---|---|---|---|---|
| IdentityService | 8081 | `IDENTITY_` | 1000–1999 | 5441 |
| CareRecipientService | **8082** | `CARE_RECIPIENT_` | **2000–2999** | PostgreSQL **5442**, DB `CARECONNECT_CARE_RECIPIENT` |
| ApiGateway | **8080** | `GATEWAY_` | **9000–9099** (lỗi hạ tầng gateway) | không có |

Base package: `mss301.carerecipientservice`, `mss301.apigateway` (theo skeleton).

### 2.2 Cách lấy user hiện tại ở service phía sau — **chọn: tiếp tục tự validate JWT**
`AGENTS.md` §2/§7 #6 bắt buộc: mỗi service tự xác minh JWT bằng Resource Server, **không tin** `X-User-Id`/
`X-Roles`. CareRecipientService sẽ:
- `NimbusJwtDecoder.withJwkSetUri(IDENTITY_JWKS_URI)` (RS256 allowlist) + validator `iss`, `aud` chứa
  **`careconnect-api`** (audience chung của mọi service backend — Q1), `exp/iat`, `jti`, `sub` UUID,
  `token_type == access`.
- Converter `roles[]` → `ROLE_<code>`, `permissions[]` → giữ nguyên; tên authentication = userId; `@CurrentUserId`.
- Bearer header **hoặc** cookie `access_token` (web qua gateway). Request có cookie → bắt CSRF double-submit với
  cookie `XSRF-TOKEN` do Identity phát (service không tự phát cookie CSRF).
- Header `X-User-*` do gateway gắn **chỉ dùng cho log/tracing**, không dùng để phân quyền.
- Giới hạn đã biết (`AGENTS.md` §4): CareRecipient xác minh offline → tài khoản bị khóa vẫn gọi được tới khi access
  token hết hạn (≤ access TTL). Không tuyên bố thu hồi ngay.

### 2.3 IdentityService — chỉ BỔ SUNG
- **Audience chung (Q1):** access token mới có `aud = ["careconnect-identity", "careconnect-api"]` (hằng
  `TokenClaims.API_AUDIENCE`, không qua env). Refresh token giữ nguyên `careconnect-identity`. Decoder của Identity
  vẫn chỉ đòi `careconnect-identity` → token phát trước thay đổi vẫn dùng được ở Identity tới khi hết hạn.
- `GET /internal/users/{userId}`, `GET /internal/users/by-email?email=`, `POST /internal/users/batch`
  (body `{ "userIds": [...] }`, tối đa 100 id, trả các user tìm thấy, bỏ qua id không có / đã xóa mềm).
- Response `InternalUserResponse { id, email, fullName, phoneNumber, status, roles }` bọc `APIResponse`.
  User đã xóa mềm → 404 `USER_NOT_EXISTED`; LOCKED vẫn trả về kèm `status` (caller tự quyết).
- Bảo vệ: **SecurityFilterChain riêng** `@Order(1)` `securityMatcher("/internal/**")`, stateless, không CSRF/CORS,
  filter kiểm `X-Internal-Secret` so sánh constant-time với `IDENTITY_INTERNAL_SECRET`. Secret rỗng → mọi request
  `/internal/**` bị từ chối (fail-closed, không phá test/deploy hiện có). Lỗi trả envelope qua `SecurityErrorHandler`.
  Chain hiện có **không đổi**.

### 2.4 CareRecipientService — schema (`schema/care_recipient.sql`, áp dụng tay, không Flyway)
- Kiểu thời gian **`timestamptz`** (theo dự án; yêu cầu ghi "TIMESTAMP (UTC)").
- `care_recipients`: đủ cột theo yêu cầu, `version bigint NOT NULL DEFAULT 0` (`@Version`), `created_by uuid`,
  `updated_by uuid` (Q10 — người sửa gần nhất, lấy từ token), `deleted_at`; CHECK cho `gender`, `blood_type` (`'A+'…'O-','UNKNOWN'`), `mobility_level`, `independence_level`,
  `status`; CHECK `latitude ∈ [-90,90]`, `longitude ∈ [-180,180]`, `height_cm/weight_kg > 0`. Index
  `(status) WHERE deleted_at IS NULL`, `lower(full_name)`.
- `emergency_contacts`: FK nội bộ `recipient_id`, **partial unique** `(recipient_id) WHERE is_primary AND deleted_at IS NULL`.
- `medications`: `updated_by uuid`, `time_of_day jsonb` (mảng `"HH:mm"`, map bằng `@JdbcTypeCode(SqlTypes.JSON) List<LocalTime>`),
  `route` CHECK, CHECK `end_date IS NULL OR end_date >= start_date`.
- `recipient_managers`: `UNIQUE (recipient_id, user_id)` (`uk_recipient_managers_user`), partial unique
  `(recipient_id) WHERE is_primary`, index `user_id`, `recipient_id`; CHECK enum `relationship_type`, `access_level`.
- Enum `BloodType` dùng hằng `A_POSITIVE…` + `AttributeConverter`/`@JsonValue` để DB và API vẫn là `"A+"`.

### 2.5 CareRecipientService — phân quyền hồ sơ
- `RecipientAccessService.checkAccess(recipientId, userId, minLevel)` thứ tự `VIEWER < EDITOR < OWNER`, dùng ở
  mọi API customer và endpoint internal `/access`.
- Không phải manager của hồ sơ (hoặc hồ sơ đã xóa) → **404 `RECIPIENT_NOT_FOUND`**.
- Người tạo = OWNER + `is_primary = true`. Thay đổi danh sách managers khóa dòng `care_recipients`
  (`findForUpdate`, PESSIMISTIC_WRITE) để kiểm "OWNER cuối cùng" không bị race. Chuyển primary: bỏ cờ cũ → `flush`
  → đặt cờ mới, trong một `@Transactional`.
- Customer API yêu cầu `hasRole('CUSTOMER')`; admin API `hasRole('ADMIN')`.
- Caregiver: chưa có Booking → interface `CaregiverAssignmentService` (`isAssigned(recipientId, caregiverId)`),
  impl tạm trả `false` + `// TODO(BookingService)`; chưa mở endpoint nào cho caregiver.

### 2.6 CareRecipientService — API
Đường dẫn **trong service không có `/api/v1`** (giống Identity `/auth`, `/users`); gateway cắt prefix.
- Customer `/care-recipients`: `POST`, `GET` (page/size/status/keyword, kèm `myAccessLevel`), `GET /{id}`,
  `PUT /{id}` (EDITOR+, body có `version` → lệch → 409), `PATCH /{id}/status`, `DELETE /{id}` (OWNER);
  `/{id}/medications` CRUD + `PATCH /{mid}/active`; `/{id}/emergency-contacts` CRUD + `PATCH /{cid}/primary`;
  `/{id}/managers` `GET` (gọi Identity **batch 1 lần**), `POST` (email), `PATCH /{managerId}`,
  `PATCH /{managerId}/primary`, `DELETE /{managerId}` (OWNER gỡ người khác; ai cũng tự rời được, trừ OWNER cuối).
- Admin `/admin/care-recipients`: `GET` (page + filter status/keyword/createdBy), `GET /{id}`.
- Internal `/internal/care-recipients`: `GET /{id}/snapshot`, `GET /{id}/access?userId=&minLevel=`,
  `POST /batch` — bảo vệ bằng `X-Internal-Secret` (`CARE_RECIPIENT_INTERNAL_SECRET`), chain riêng như Identity.
- Danh sách phân trang trả `PageResponse<T> { items, page, size, totalElements, totalPages }` (DTO mới — Identity
  hiện chỉ trả `List`).

### 2.7 OpenFeign (CareRecipientService)
- `client/IdentityClient` (`@FeignClient(name = "identity", url = "${care-recipient.identity.url}")` — không có
  discovery nên dùng URL env `IDENTITY_SERVICE_URL`), DTO bản sao trong `client/dto`.
- `IdentityClientConfig`: `RequestInterceptor` gắn `X-Internal-Secret` (+ chuyển `X-Request-Id`),
  `ErrorDecoder` đọc envelope: 404 → `IDENTITY_USER_NOT_FOUND`, 5xx/timeout → `IDENTITY_UNAVAILABLE` (503), 401/403
  (sai secret) → log error + `IDENTITY_UNAVAILABLE` (không lộ cấu hình cho client).
- Timeout `spring.cloud.openfeign.client.config.identity.connect-timeout/read-timeout` từ env.
- Resilience4j (`spring.cloud.openfeign.circuitbreaker.enabled=true`) + `FallbackFactory`:
  `batch` → danh sách rỗng (API managers vẫn trả, thiếu profile); `byEmail`/`get` → ném `IDENTITY_UNAVAILABLE`
  (thao tác ghi không được đoán). Lỗi nghiệp vụ 404 cấu hình `ignore-exceptions` để không mở circuit.

### 2.8 ApiGateway (Spring Cloud Gateway Server **MVC** — theo skeleton đã chọn)
- Route (cắt `/api/v1`): `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/roles/**`, `/api/v1/permissions/**` →
  Identity; `/api/v1/care-recipients/**`, `/api/v1/admin/care-recipients/**` → CareRecipient. JWKS **không** route
  công khai qua gateway (service gọi trực tiếp Identity), trừ khi anh/chị muốn.
- **Chặn `/internal/**`** và `/**/internal/**` → 404 envelope, trước khi route.
- Xác thực JWT tại gateway (Resource Server, JWKS của Identity, kiểm iss/token_type/exp, `aud` chứa
  `careconnect-api`): **kiểm sớm**, service vẫn tự kiểm. Access token phát trước Q1 (thiếu `careconnect-api`) bị
  gateway trả 401 → client refresh lấy token mới (tối đa một access TTL). Bearer header hoặc cookie `access_token`. Whitelist: các endpoint token-flow của Identity
  (login/refresh/logout/csrf/mobile/*, register/*, forgot/reset), swagger, health — ở các path này gateway **không
  đọc token** (cookie hết hạn không được chặn refresh/logout, giống Identity).
- Xóa mọi header `X-User-*` từ client, gắn lại `X-User-Id`, `X-User-Roles` (và `X-User-Email` nếu chốt có claim).
- Chuyển nguyên `Authorization`, `Cookie`, `X-XSRF-TOKEN`, `Set-Cookie`.
- **Cookie (Q6/Q17, đã kiểm code):** Identity set `access_token`, `refresh_token`, `XSRF-TOKEN` qua
  `CookieUtils.set` (HttpOnly, `Path=/`, **không Domain**, `Secure`=`IDENTITY_COOKIE_SECURE`,
  `SameSite`=`IDENTITY_COOKIE_SAME_SITE`, mặc định Lax); cookie CSRF do `CookieCsrfTokenRepository` (`/auth/csrf`)
  cũng có `Path=/`, cùng Secure/SameSite. Vì `Path=/` và không có Domain, cookie gắn vào host của gateway và được
  gửi cho mọi `/api/v1/**` → **không cần rewrite path**. Gateway vẫn có bước kiểm: nếu sau này Identity đặt Path
  khác `/` thì rewrite `Path=/x` → `Path=/api/v1/x` (bộ lọc `Set-Cookie`), có test.
- Test web qua gateway (bước 4): `GET /api/v1/auth/csrf` → `POST /api/v1/auth/login` (cookie + CSRF) →
  `GET /api/v1/users/me` → ghi `POST /api/v1/care-recipients` có `X-XSRF-TOKEN` → `POST /api/v1/auth/refresh` →
  `POST /api/v1/auth/logout`; kèm luồng mobile.
- CORS tập trung tại gateway (`GATEWAY_ALLOWED_ORIGINS`, credentials); gateway **xóa header `Origin`** trước khi
  proxy để CORS filter của Identity không thêm header trùng → **không phải sửa code Identity**.
- 401/403/404/503 (route lỗi/downstream không kết nối được) trả `APIResponse` (code `401/403/404`,
  `GATEWAY_UPSTREAM_UNAVAILABLE 9001`).
- `X-Request-Id`: nhận nếu là UUID hợp lệ, không thì sinh mới; gắn vào request downstream + response; log 1 dòng
  method/path/status/duration/requestId (không log header/cookie/query nhạy cảm).
- Swagger tổng hợp: swagger-ui ở gateway với 2 nguồn `/api/v1/auth/../v3/api-docs` (route riêng
  `/api-docs/identity`, `/api-docs/care-recipient`).

### 2.9 Thư viện cần thêm
| Project | Thêm | Ghi chú |
|---|---|---|
| IdentityService | — | không thêm |
| CareRecipientService | `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-actuator`, `spring-cloud-starter-circuitbreaker-resilience4j`, MapStruct 1.6.3 + processor + `lombok-mapstruct-binding` 0.2.0, test: `h2`, `spring-boot-starter-security-test` | giữ JPA/Validation/WebMVC/springdoc/OpenFeign/PostgreSQL/Lombok đã có; **không** Redis, Flyway |
| _(chung)_ | — | `spring.threads.virtual.enabled: true` cho ApiGateway và CareRecipientService |
| ApiGateway | `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server`, `spring-boot-starter-actuator`, `spring-boot-starter-validation`, `lombok` | giữ gateway-server-webmvc, springdoc; rate limit (nếu chọn) cần `bucket4j` + Redis/caffeine |

---

## 3. File sẽ tạo / sửa

### 3.1 IdentityService (chỉ thêm, + 2 chỗ cấu hình)
- **Thêm:** `controller/InternalUserController`, `service/InternalUserService` + `Impl/InternalUserServiceImpl`,
  `dto/request/UserBatchRequest`, `dto/response/InternalUserResponse`, `security/InternalSecurityConfig`
  (chain `/internal/**`), `security/InternalSecretFilter`, test `InternalUserApiTests`.
- **Sửa (additive):** `JwtServiceImpl` (thêm `careconnect-api` vào `aud` của access), `TokenClaims`
  (+`API_AUDIENCE`), `config/swagger/OpenApiConfig` (scheme `internalSecret`, không gắn CSRF cho `/internal/**`),
  `ErrorCode` (+mã validate batch), `UserMapper` (+`toInternalResponse`, `toInternalResponseList`), `UserRepository`
  (+`findAllByIdInAndDeletedAtIsNull`), `IdentityProperties` (+`internalSecret`, mặc định rỗng),
  `application.yaml` (+`identity.internal-secret`), `.env.example`, `README.md`.
  README thêm ghi chú: access TTL nên giữ 10–15 phút vì service khác không nạp lại quyền theo thời gian thực.

### 3.2 CareRecipientService (mới)
```
pom.xml (sửa), docker-compose.yml, .env.example, README.md, AGENTS.md (riêng: dải 2000–2999, quyền hồ sơ)
schema/care_recipient.sql
scripts/Start-Dev.ps1
src/main/resources/application.yaml (thay application.properties)
src/main/java/mss301/carerecipientservice/
  CareRecipientServiceApplication (+@EnableFeignClients)
  config/  CareRecipientProperties, WebConfig, Converters (enum path/query), swagger/OpenApiConfig, swagger/SwaggerExamples
  security/ SecurityConfig, InternalSecurityConfig, InternalSecretFilter, JwtConfig, JwtAuthenticationConverter,
            SecurityErrorHandler, CurrentUserId(+Resolver), CookieUtils, TokenClaims, RequestIdFilter
  controller/ CareRecipientController, MedicationController, EmergencyContactController, RecipientManagerController,
              AdminCareRecipientController, InternalCareRecipientController
  dto/request/  CareRecipientCreateRequest, CareRecipientUpdateRequest, StatusRequest, MedicationRequest,
                EmergencyContactRequest, ManagerAddRequest, ManagerUpdateRequest, RecipientBatchRequest, …
  dto/response/ APIResponse, PageResponse, CareRecipientResponse, CareRecipientSummaryResponse, MedicationResponse,
                EmergencyContactResponse, RecipientManagerResponse, RecipientSnapshotResponse, AccessCheckResponse
  entity/  BaseEntity, CareRecipient, EmergencyContact, Medication, RecipientManager
  enums/   Gender, BloodType, MobilityLevel, IndependenceLevel, RecipientStatus, MedicationRoute,
           RelationshipType, AccessLevel, RoleCode
  exception/ AppException, ErrorCode, GlobalExceptionHandler
  mapper/  CareRecipientMapper, MedicationMapper, EmergencyContactMapper, RecipientManagerMapper
  repository/ CareRecipientRepository, EmergencyContactRepository, MedicationRepository, RecipientManagerRepository
  service/ (+Impl/) CareRecipientService, MedicationService, EmergencyContactService, RecipientManagerService,
           RecipientAccessService, AdminCareRecipientService, InternalCareRecipientService,
           IdentityUserService (bọc Feign), CaregiverAssignmentService (TODO Booking)
  client/  IdentityClient, IdentityClientConfig, IdentityClientFallbackFactory, IdentityErrorDecoder, dto/IdentityUserDto, dto/UserBatchRequest
src/test/…  RecipientAccessServiceTests, RecipientManagerServiceTests (owner cuối/primary), IdentityErrorDecoderTests,
            CareRecipientApiTests (MockMvc + H2, JWT ký bằng khóa test), repository test (H2; PostgreSQL thật qua cờ)
```
Xóa file Initializr thừa: `.gitignore`, `HELP.md`, `application.properties` (có `.gitignore` chung ở gốc).

### 3.3 ApiGateway (mới)
```
pom.xml (sửa), .env.example, README.md, AGENTS.md, scripts/Start-Dev.ps1
src/main/resources/application.yaml (thay application.properties)
src/main/java/mss301/apigateway/
  config/  GatewayProperties, RouteConfig (RouterFunction routes), CorsConfig, swagger/…
  security/ SecurityConfig, JwtConfig, SecurityErrorHandler, TokenClaims, CookieUtils
  filter/  RequestIdFilter, UserHeaderFilter (strip + gắn X-User-*), InternalPathBlockFilter
  dto/response/ APIResponse ; exception/ ErrorCode, GatewayErrorHandler
src/test/… route/security tests (MockMvc + WireMock-like stub hoặc MockRestServiceServer)
```
> `filter/` là package mới không có trong layout `AGENTS.md` §2 (gateway không có controller/service/entity) — sẽ
> ghi vào `ApiGateway/AGENTS.md`.

### 3.4 Tài liệu chung
`AGENTS.md` (bảng domain → `CareRecipientService`, mục gateway, ngoại lệ đã chốt), `docs/PLAN.md` (đánh dấu
hạng mục), README từng service, plan này.

---

## 4. Thứ tự thực hiện (sau khi xác nhận)
- [x] 1. Identity internal API + audience `careconnect-api` + test — 2026-10-03.
- [x] 2. CareRecipientService: SQL → entity → repository → DTO/mapper → service + phân quyền → controller → Feign → test — 2026-10-03.
- [x] 3. ApiGateway + test — 2026-10-03.
- [x] 4. End-to-end local với PostgreSQL/Redis thật (mobile, web, internal, fallback) + test Identity trên DB thật — 2026-10-03.

---

## 5. Quyết định đã chốt (2026-10-03)

Đồng ý toàn bộ đề xuất ở bản khảo sát, **trừ** các điểm ghi chú dưới đây.

| # | Chốt |
|---|---|
| Q1 | **Audience chung `careconnect-api`** thêm vào access token (giữ `careconnect-identity`), hằng trong code — không env danh sách. Mọi service backend (CareRecipient, service sau) và gateway chỉ kiểm `careconnect-api`. Identity vẫn kiểm `careconnect-identity` → token cũ không làm hỏng Identity. |
| Q2 | Bỏ `X-User-Email`; gateway chỉ gắn `X-User-Id`, `X-User-Roles`. |
| Q3 | Service phía sau tự validate JWT; header gateway chỉ để log. |
| Q4/Q5 | Giữ `schema/*.sql` áp tay + `timestamptz`, một `application.yaml` + env. Chuyển Flyway ghi ở nợ kỹ thuật §6. |
| Q6/Q17 | Đã kiểm cookie (§2.8): `Path=/`, không Domain → không cần rewrite. Gateway xử lý CORS, xóa `Origin` khi proxy, không sửa CORS của Identity. Bắt buộc test **luồng web** qua gateway (csrf → login → API có CSRF → refresh → logout), không chỉ mobile. |
| Q7 | `InternalUserController` riêng (`/internal/users`), ghi ngoại lệ vào `AGENTS.md`. |
| Q8 | `DELETE` = `ARCHIVED` + `deleted_at`; `PATCH status` chỉ ACTIVE↔INACTIVE. |
| Q9 | `medications`, `emergency_contacts` extends `BaseEntity` (soft delete); `recipient_managers` xóa dòng. |
| Q10 | **Chưa làm bảng audit.** Thêm cột `updated_by uuid` vào `care_recipients` và `medications`. |
| Q11 | Primary phải là OWNER; primary muốn rời phải chuyển primary trước. |
| Q12 | Không quan hệ → 404 `RECIPIENT_NOT_FOUND`; có quan hệ nhưng thiếu cấp → 403 `RECIPIENT_ACCESS_DENIED`. |
| Q13 | Chỉ `hasRole('CUSTOMER')` / `hasRole('ADMIN')`, không thêm permission. |
| Q14 | Gộp một mã `MANAGER_USER_INVALID` khi thêm người thân bằng email. |
| Q15 | Giữ `GET /internal/users/by-email?email=`, không log query. |
| Q16 | Mỗi service bị gọi có một secret riêng (`IDENTITY_INTERNAL_SECRET`, `CARE_RECIPIENT_INTERNAL_SECRET`). |
| Q18 | Thêm env `*_OPENAPI_SERVER_URL` (mặc định rỗng) để spec có `servers` = `/api/v1` khi đứng sau gateway. |
| Q19 | Chưa làm rate limiting. |
| Q20 | Gateway Server MVC. |
| Q21 | Build thử; lỗi tương thích sẽ báo lại, không tự đổi version. |
| Q22 | Không giới hạn số lượng; batch internal tối đa 100 id. |
| Bổ sung | `spring.threads.virtual.enabled=true` cho ApiGateway và CareRecipientService. |
| Bổ sung | README Identity/CareRecipient/Gateway ghi chú: access token nên giữ **10–15 phút** vì service khác không nạp lại quyền theo thời gian thực. |

## 6. Nợ kỹ thuật

- **Flyway:** chuyển toàn bộ service (Identity, CareRecipient, service sau) từ `schema/*.sql` áp tay sang Flyway
  trong **một đợt riêng** (baseline từ SQL hiện tại, cập nhật `AGENTS.md` §5/#10). Không làm lẻ từng service.
- **Đồng bộ trạng thái user ↔ `recipient_managers`:** khi user bị khóa/xóa mềm ở Identity, CareRecipient chưa biết
  → hồ sơ có thể còn OWNER đã bị khóa. Sau này Identity phát event (`user.locked`, `user.deleted`) qua broker +
  outbox; CareRecipient xử lý (đánh dấu/gỡ manager, cảnh báo hồ sơ mất OWNER hoạt động). Ghi `TODO` tại
  `RecipientManagerServiceImpl` và README.
- **Thu hồi quyền tức thời ở service khác:** service ngoài Identity xác minh offline → trễ tối đa access TTL (giữ
  10–15 phút). Cần contract kiểm `authz_version`/status nếu muốn tức thời.
- **Caregiver xem hồ sơ:** chờ BookingService (`CaregiverAssignmentService` TODO).
- **Audit hồ sơ người bệnh:** chưa có bảng audit (chỉ `updated_by`).
- **Rate limiting** tại gateway.

## 7. Kết quả kiểm thử (2026-10-03)

### Test tự động

| Project | H2 / mặc định | PostgreSQL / Redis thật | `mvnw package` |
|---|---|---|---|
| IdentityService | 56/56 → **58/58** sau thay đổi CSRF mobile (bên dưới) | **56/56** (PostgreSQL 17 + Redis 7, container `*-verify-*` 55494/56394; trước thay đổi CSRF) | thành công |
| CareRecipientService | 47/47 | **47/47** (PostgreSQL 17, schema SQL + `validate`, container 55495) | thành công |
| ApiGateway | 14/14 (MockMvc + upstream giả qua HTTP thật) | — | thành công |

### End-to-end (3 JAR thật + compose project riêng `careconnect-e2e-*`, PostgreSQL 17 + Redis 7)

Chạy lại bất cứ lúc nào từ `BE/`: **`.\scripts\e2e\Run-E2E.ps1`** (`-SkipBuild` dùng JAR có sẵn, `-KeepInfra` giữ
container/volume e2e). Script tự build JAR, dựng hạ tầng riêng, áp schema, chạy 3 service với `.env` + override e2e, seed
tài khoản, chạy `scripts/e2e/e2e.py` phase a/b/c, tắt Identity, chạy phase d, rồi dọn sạch. Log: `%TEMP%\careconnect-e2e`.
Lần chạy bằng script (2026-10-03, sau thay đổi CSRF mobile): **a 19/19, b 14/14, c 10/10, d 4/4 — E2E PASSED**.

DB e2e tách khỏi DB dev (volume riêng, port 55601/56601/55602, đã `down -v` sau khi chạy). Script: Python stdlib, giữ
cookie như trình duyệt, không in token/secret. Tài khoản CUSTOMER e2e được **seed thẳng vào DB Identity e2e** (BCrypt)
vì đăng ký thật cần OTP email Brevo — không tự động hóa được; ADMIN e2e qua `IDENTITY_BOOTSTRAP_*`.

| Mục | Kết quả |
|---|---|
| a) Mobile: login qua gateway → tạo hồ sơ → thêm thuốc, liên hệ khẩn cấp → thêm người thân bằng email (Feign) → danh sách managers có profile Identity → đổi quyền → chuyển primary (không gỡ được primary: 409 2405) → gỡ người thân → archive (sau đó 404 2100) | **PASS 19/19** |
| b) Web: `GET /api/v1/auth/csrf` → login (thiếu CSRF 403 1111; có CSRF: 3 cookie `Path=/; HttpOnly`) → `/users/me` bằng cookie → ghi CareRecipient bằng cookie (thiếu CSRF 403 2002; có CSRF của Identity 200) → refresh (cookie mới) → gọi API bằng cookie mới → logout (access hiện tại bị thu hồi 401) | **PASS 14/14** |
| c) `/internal/**` qua gateway → 404 (4 biến thể path); gọi thẳng Identity / CareRecipient không secret, sai secret, hoặc chỉ có JWT → 401; đúng secret → 200 | **PASS 10/10** |
| d) Tắt IdentityService: danh sách managers vẫn 200, `profileAvailable=false`; thêm người thân → 503 2500; route Identity qua gateway → 503 9001 | **PASS 4/4** |
| e) Toàn bộ test IdentityService trên PostgreSQL/Redis thật | **PASS 56/56** |

Kiểm thêm: admin `GET /api/v1/admin/care-recipients?includeDeleted=true` (thấy cả ARCHIVED); Swagger tổng hợp
`/swagger-ui.html` + `/api-docs/{identity,care-recipient}` có server `/api/v1`; `X-Request-Id` client được giữ và log.
CareRecipientService khởi động với `ddl-auto: validate` trên schema SQL thật (khớp entity).

## 8. Điểm lệch so với plan / ghi chú

- Enum quyền hồ sơ tên **`RecipientAccessLevel`** (plan: `AccessLevel`) vì trùng `lombok.AccessLevel` dùng khắp nơi;
  giá trị DB/JSON không đổi.
- Env của CareRecipient dùng prefix riêng theo `AGENTS.md`: `CARE_RECIPIENT_IDENTITY_URL` (plan ghi `IDENTITY_SERVICE_URL`),
  `CARE_RECIPIENT_IDENTITY_INTERNAL_SECRET`, `CARE_RECIPIENT_JWT_*`.
- `PATCH /{id}/status?status=` và `PATCH .../medications/{mid}/active?active=` nhận **query param** (giống
  `PATCH /users/{id}/status` của Identity). Đổi trạng thái hồ sơ cần **OWNER** (plan chưa nói rõ).
- `PUT` hồ sơ / thuốc / liên hệ là thay toàn bộ (null = xóa giá trị); PUT thuốc không đổi `active`; PUT liên hệ:
  `primary` null = giữ nguyên.
- Gateway có package `filter/` (không có trong layout chung) — ghi ở `ApiGateway/AGENTS.md`.
- `ApiGateway/docker-compose.yml` là **tùy chọn** (chạy JAR trong container, gọi service trên host); mới kiểm
  `docker compose config`, e2e chạy gateway bằng JAR trực tiếp.
- IdentityService — thay đổi ngoài internal API (đều additive, không đổi logic cũ): `JwtServiceImpl` thêm audience
  `careconnect-api` vào access token (Q1); `OpenApiConfig` thêm scheme `internalSecret` + server gateway tùy chọn (Q18);
  `IdentityProperties` + `application.yaml` thêm `internal-secret`, `open-api-server-url`; `ErrorCode` +1112/1113.
- `spring-cloud-starter-circuitbreaker-resilience4j` 5.0.3 kéo theo `resilience4j-spring-boot3` 2.3.0 (bản cho Boot 3)
  — chạy được trên Boot 4.1.1 trong mọi test/e2e; circuit breaker cấu hình bằng Java (`ResilienceConfig`) để không phụ
  thuộc binding yaml của module đó.
- `.env` local: đã thêm `IDENTITY_INTERNAL_SECRET` vào `IdentityService/.env`; tạo `CareRecipientService/.env`,
  `ApiGateway/.env` (giá trị ngẫu nhiên / copy issuer). Khi chạy JAR từ thư mục service, đường dẫn
  `IDENTITY_JWT_PRIVATE_KEY/PUBLIC_KEY` trong `IdentityService/.env` hiện tại không tìm thấy file (đang tương đối theo
  thư mục khác) — e2e đã override bằng đường dẫn tuyệt đối; nên kiểm lại nếu chạy `scripts/Start-Dev.ps1`.

### Thay đổi bổ sung sau khi xong bước 4 (2026-10-03, theo yêu cầu)

- **CSRF cho mobile (phương án b):** `IdentityService` `SecurityConfig` — `/users/register`, `/users/register/verify`,
  `/users/register/resend-otp`, `/users/forgot-password`, `/users/reset-password` được miễn CSRF khi request **không
  mang cookie nào** (app mobile gọi thẳng, không phải lấy `/auth/csrf`). Trình duyệt có cookie vẫn phải gửi CSRF; login /
  refresh / logout web vẫn luôn cần CSRF. An toàn vì các endpoint này không dựa vào phiên đăng nhập và không đặt cookie.
  Test cũ `webLoginRequiresCsrf` sửa để kiểm trường hợp có cookie; thêm `cookielessAccountEndpointsSkipCsrf`,
  `cookielessExemptionDoesNotCoverOtherEndpoints` → 58/58.
- **Script e2e vào repo:** `scripts/e2e/Run-E2E.ps1` (PowerShell 5.1, ASCII), `scripts/e2e/e2e.py`,
  `scripts/e2e/SeedCustomers.java`.
- File plan đổi tên `docs/PLAN_CARE_RECIPIENT_GATEWAY.md` → `docs/2026-10-03-PLAN_CARE_RECIPIENT_GATEWAY.md`
  (quy ước: plan theo phiên đặt tên `<yyyy-MM-dd>-<TÊN>.md`).

## 9. TODO còn lại

- Flyway cho mọi service (một đợt riêng) — §6.
- Đồng bộ `recipient_managers` khi user bị khóa/xóa ở Identity (event + outbox) — TODO trong `RecipientManagerServiceImpl`.
- Quyền caregiver xem hồ sơ khi có BookingService — `CaregiverAssignmentService` (TODO, đang fail-closed).
- Audit thay đổi người quản lý / hồ sơ (hiện chỉ `updated_by`).
- JWKS: gateway/service cache public key; Identity tắt lâu hơn thời gian cache → xác minh token mới trả 503. Cân nhắc
  cache dài hơn / key ring khi lên production.
- Rate limiting tại gateway (chưa chốt). FE chuyển proxy sang gateway `/api/v1`.
- Contract kiểm `authz_version`/trạng thái tài khoản cho service ngoài Identity (hiện chấp nhận trễ ≤ access TTL 10–15 phút).

