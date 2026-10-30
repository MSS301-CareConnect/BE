# AGENTS.md — CareConnect (microservices)

> Hướng dẫn **dùng chung cho mọi service** trong `CareConnect/`. Đọc hết file này trước khi viết hoặc sửa code.
> Service nào có quy ước riêng thì ghi thêm ở `<Service>/AGENTS.md` (chỉ ghi phần khác, không chép lại file này).
>
> Quy ước được mang sang từ `../backend/CLAUDE.md` (dự án AIMA của cùng tác giả), chủ yếu §3 Code Conventions,
> §4 Authentication Flow và §7 Rules, rồi chỉnh lại cho kiến trúc microservice. **Chỉ lấy cách code, không lấy
> nghiệp vụ AIMA**: không có brand/content/Meta/AI/payOS, không dùng mã FR/BR của AIMA, **không dùng Flyway**.
>
> Khi tài liệu này và code hiện tại khác nhau thì **tài liệu này là chuẩn** — báo lại chỗ lệch, không tự refactor
> khi đang làm việc khác. IdentityService đã được refactor theo chuẩn ngày 2026-10-01 (§8).

---

## 1. Project Overview

**CareConnect** là nền tảng trung gian giúp người thân tìm và đặt người chăm sóc tại nhà. Khách hàng xem hồ sơ
caregiver (chuyên môn, kinh nghiệm, bằng cấp, đánh giá) rồi đặt lịch theo giờ / theo ngày / theo ca. Trung tâm
chăm sóc quản lý caregiver, dịch vụ, giá và phân công. Admin quản trị toàn nền tảng. Caregiver dùng **app mobile**
để nhận lịch, cập nhật tiến độ và gửi báo cáo chăm sóc.

### Roles

| Role code (DB / code / JWT) | Nhãn hiển thị | Quyền chính |
|---|---|---|
| `CUSTOMER` | Người thân / khách hàng | Đặt lịch, xem caregiver, xem report, đánh giá |
| `CAREGIVER` | Người chăm sóc | Xem lịch được giao, cập nhật trạng thái ca, gửi report |
| `CENTER_MANAGER` | Center Manager | Quản lý caregiver của trung tâm, dịch vụ & giá, booking + phân công, đánh giá/phản hồi, khiếu nại cấp 1, thống kê trung tâm |
| `ADMIN` | Quản trị hệ thống | Quản lý trung tâm, khách hàng, danh mục dịch vụ chuẩn, chính sách nền tảng, khiếu nại cấp cao, thống kê toàn hệ thống |

Luôn dùng `CENTER_MANAGER` trong code/DB/token; "Center Manager" chỉ là nhãn UI.

### Chức năng theo role → domain sở hữu

Mỗi chức năng thuộc về **một** domain/service. Tên các service ngoài IdentityService **chưa chốt** — khi tạo
service mới, hỏi tên rồi cập nhật bảng này.

| Chức năng (theo mô tả nghiệp vụ) | Domain sở hữu |
|---|---|
| Đăng ký, đăng nhập, cập nhật thông tin cá nhân, đổi mật khẩu; khóa/mở khóa tài khoản khách hàng, trung tâm; xem danh sách người dùng toàn hệ thống; tạo tài khoản manager/caregiver; role & permission | **Identity** (`IdentityService`) |
| Hồ sơ người bệnh / người cần chăm sóc (tạo, sửa, nhiều hồ sơ), ghi chú đặc biệt, thuốc, liên hệ khẩn cấp, người thân cùng quản lý (OWNER/EDITOR/VIEWER) | **Care recipient** (`CareRecipientService`) |
| Trung tâm: đăng ký, duyệt/onboard, trạng thái hoạt động | **Provider** (`ProviderService`, `care_centers`) |
| Hồ sơ chuyên môn caregiver: kỹ năng, kinh nghiệm, bằng cấp, chứng chỉ; xác minh (manager) + audit/duyệt lại (admin); trạng thái hoạt động, lịch làm việc | **Provider** (`ProviderService`; kỹ năng = `caregiver_services`, PRV-01) |
| Danh mục dịch vụ chuẩn (admin); dịch vụ trung tâm cung cấp + giá theo giờ/ngày/ca | **Care catalog** (`CareCatalogService`) |
| Tìm kiếm / lọc caregiver (kỹ năng, kinh nghiệm, giới tính, giá, đánh giá) | Đọc tổng hợp từ caregiver profile + catalog + review (chưa chốt cách làm) |
| Booking: chọn dịch vụ/thời gian/caregiver, chi phí tạm tính, xác nhận/từ chối, phân công/đổi caregiver, hủy, lịch sử, trạng thái realtime | **Booking** (`BookingService`; giá từng buổi lấy từ Catalog, BK-01) |
| Ca chăm sóc: nhận ca, bắt đầu/kết thúc, checklist công việc (cho ăn, uống thuốc, vệ sinh…), ghi chú tình trạng, care report | **Booking** (`BookingService` — mỗi `booking_occurrence` là một buổi; start/end, checklist, care report: giai đoạn sau) |
| Đánh giá, phản hồi, khiếu nại cấp 1 (manager) / cấp cao (admin) | **Booking** (`BookingService` — review / complaint gắn với booking; route `/api/v1/feedbacks/**`, `/complaints/**`) |
| Đặt cọc, hoàn tiền theo % so với ngày thực hiện, hoa hồng & đối soát nền tảng ↔ trung tâm; cấu hình % (admin) | **Payment** (`PaymentService` — cọc, REMAINING, hoàn tiền, hóa đơn / công nợ, chính sách chỉ đọc; xem `PaymentService/AGENTS.md`. Hoa hồng / đối soát chưa làm — D15) |
| Thông báo: lịch xác nhận / hủy / hoàn thành / có report mới / lịch mới hoặc đổi lịch | **Booking** (`BookingService` — thông báo in-app + SSE `/api/v1/notifications/stream`) |
| File / ảnh (chứng chỉ, ảnh care report…): upload, metadata, URL tải có hạn | **Media** (`MediaService`, Cloudflare R2) |
| Thống kê trung tâm / toàn hệ thống | Reporting (chưa chốt) |

> ⚠️ **Phạm vi hiện tại: IdentityService, CareRecipientService, ApiGateway, CareCatalogService, ProviderService,
> BookingService, MediaService, PaymentService** (BookingService 2026-10-09 — `docs/2026-10-09-PLAN_BOOKING.md`;
> PaymentService viết lại theo quy ước 2026-10-29 — `docs/2026-10-29-PAYMENT_1A_SPEC.md`). Không tạo service hay
> bảng của các domain khác khi chưa được yêu cầu. Thông số riêng từng service: `<Service>/AGENTS.md`.
>
> **Chưa chốt — hỏi trước khi code phần liên quan:** tỷ lệ đặt cọc / mốc & % hoàn tiền; ai được sửa giá dịch vụ
> trung tâm (mô tả có chồng lấn admin ↔ manager); permission catalog của các domain sau; TTL token production;
> tên + ranh giới các service còn lại; rate limiting tại gateway.

| Service | Port | PostgreSQL (dev) | Dải mã lỗi `ErrorCode` | Ghi chú |
|---|---|---|---|---|
| ApiGateway | 8080 | — | `9000–9099` | + `401`, `403`, `404`, `9999` dùng chung ý nghĩa |
| IdentityService | 8081 | 5441 (+ Redis 6381) | `1000–1999` | phát access / refresh / token SERVICE, JWKS |
| CareRecipientService | 8082 | 5442 | `2000–2999` | |
| CareCatalogService | 8083 | 5443 | `3000–3999` | |
| ProviderService | 8084 | 5444 | `4000–4999` | |
| BookingService | 8085 | 5445 | `5000–5999` | gồm ca chăm sóc, review / complaint, notification, thống kê |
| MediaService | 8086 | 5446 | `7000–7999` | |
| PaymentService | 8087 | 5447 | `6000–6999` | VNPay; Stripe tắt sau cờ (D16) — xem `PaymentService/AGENTS.md` |

### IdentityService — thông số

| Field | Value |
|---|---|
| **Thư mục** | `CareConnect/IdentityService/` |
| **Artifact** | `mss301:IdentityService:0.0.1-SNAPSHOT` |
| **Base package** | `mss301.identityservice` (service mới: `mss301.<tênservice>` viết thường) |
| **Java** | 21 |
| **Spring Boot** | 4.1.1 (+ Spring Cloud BOM theo `pom.xml`) |
| **Build tool** | Maven Wrapper (`mvnw` / `mvnw.cmd`) |
| **Port** | `IDENTITY_SERVER_PORT` (mặc định 8081) |

> ⚠️ Build/run bằng **JDK 21** (`JAVA_HOME` phải trỏ JDK 21, nếu không Maven báo `release version 21 not supported`).

**Dependencies (từ `pom.xml`):**

| Library | Ghi chú |
|---|---|
| Spring Boot Starter WebMVC / Data JPA / Security / Validation / Actuator | `/actuator/health` (không lộ chi tiết) |
| Spring Boot Starter Security OAuth2 **Resource Server** | Xác minh JWT bằng `JwtDecoder` của Spring — **khác AIMA** (AIMA dùng filter tự viết) |
| Spring Boot Starter Data Redis | Refresh `jti`, access blacklist, OTP state |
| PostgreSQL JDBC | runtime |
| Lombok + MapStruct 1.6.3 (+ `lombok-mapstruct-binding` 0.2.0) | annotation processors khai báo trong `maven-compiler-plugin` |
| springdoc-openapi-starter-webmvc-ui 3.1.0 | Swagger UI |
| Spring Cloud OpenFeign | Khai báo sẵn cho gọi service-to-service; chưa dùng |
| H2 + `*-test` starters | chỉ test |

Email gửi qua **Brevo HTTP API** bằng `RestClient` (không SMTP, không mail starter). **Không** thêm Flyway, jjwt,
WebFlux vào service MVC.

---

## 2. Project Structure

### Repo

```
CareConnect/
├── AGENTS.md                    — file này (quy ước chung mọi service)
├── .gitignore                   — ignore chung mọi service (target/, .env, keys/, IDE...); service mới không cần file riêng
├── docs/PLAN.md                 — checklist tiến độ của CareConnect (độc lập với ../docs/PLAN.md của AIMA)
├── docs/2026-10-03-PLAN_CARE_RECIPIENT_GATEWAY.md — plan + kết quả CareRecipientService / ApiGateway / internal API
│                                  (plan theo phiên làm việc đặt tên <yyyy-MM-dd>-<TÊN>.md)
├── scripts/e2e/                 — Run-E2E.ps1 (dựng hạ tầng e2e riêng + chạy 7 JAR + e2e.py), e2e.py, SeedCustomers.java,
│                                  payment_stub.py (PaymentService giả cho e2e tới khi có service thật)
├── compose.yaml, start.bat, stop.bat, DOCKER.md — stack Docker tổng (project "careconnect", 2026-10-09): include
│                                  <Service>/compose.stack.yaml + <Service>/Dockerfile; dùng lại volume dữ liệu cũ
├── ApiGateway/                  — Spring Cloud Gateway Server MVC, port 8080 (xem ApiGateway/AGENTS.md)
├── CareRecipientService/        — hồ sơ người bệnh, port 8082, PostgreSQL riêng 5442 (xem CareRecipientService/AGENTS.md)
├── CareCatalogService/          — danh mục dịch vụ + giá, port 8083, PostgreSQL riêng 5443
├── ProviderService/             — trung tâm, caregiver, ca làm việc, port 8084, PostgreSQL riêng 5444
├── BookingService/              — booking + buổi chăm sóc, port 8085, PostgreSQL riêng 5445 (xem BookingService/AGENTS.md)
├── MediaService/                — file / ảnh trên Cloudflare R2, port 8086, PostgreSQL riêng 5446
├── PaymentService/              — đặt cọc / REMAINING / hoàn tiền / hóa đơn, port 8087, PostgreSQL riêng 5447 (xem PaymentService/AGENTS.md)
└── IdentityService/             — mỗi service là một Maven project độc lập
    ├── pom.xml, mvnw, mvnw.cmd
    ├── docker-compose.yml       — PostgreSQL + Redis RIÊNG của service (loopback)
    ├── .env / .env.example      — .env bị gitignore; .env.example chỉ placeholder, chia nhóm bằng dòng trống
    ├── keys/                    — RSA PEM dev (gitignore) — chỉ IdentityService có private key
    ├── schema/identity.sql      — DDL khởi tạo DB mới (áp dụng tay, KHÔNG Flyway)
    ├── scripts/                 — New-DevKeys.ps1, Start-Dev.ps1 (nạp .env rồi chạy), GenerateDevKeys.java
    ├── README.md                — cách chạy + API contract của service
    └── src/main/resources/application.yaml   — MỘT file cấu hình duy nhất, mọi giá trị đọc từ env
```

### Microservice boundaries

- **Mỗi service sở hữu DB + Redis namespace riêng.** Không dùng chung JPA entity/repository, không query DB của
  service khác, không FK xuyên DB. Tham chiếu chéo bằng **UUID** (`userId`, `centerId`, `bookingId`…).
- Dữ liệu cần từ service khác → gọi **API contract** (client trong `client/`) hoặc nhận **event** (khi có broker).
  Không import class của service khác; DTO của client là bản sao riêng trong service gọi.
- IdentityService sở hữu: danh tính đăng nhập, password hash, trạng thái tài khoản, role, permission, gán quyền,
  phiên (refresh/blacklist trên Redis), membership trung tâm (`users.center_id`) và audit quản trị tài khoản.
  **Không** sở hữu hồ sơ chuyên môn caregiver, hồ sơ người bệnh, trung tâm, dịch vụ, booking, report, review, tiền.
- Mỗi service tự xác minh JWT và tự phân quyền (kể cả khi đã có gateway). Không tin header `X-User-Id` /
  `X-Roles` do client gửi.
- **API gateway: `ApiGateway/`** — Gateway Server MVC, route `/api/v1/**` tới **cả 6 service** (Identity, CareRecipient,
  Booking, Provider, CareCatalog, Media) **và PaymentService** (route sẵn, GW-01), **không cắt prefix** (mỗi service
  tự khai báo `/api/v1`), kiểm token sớm, **chuyển nguyên** `Authorization`, cookie (`access_token`, `refresh_token`,
  `XSRF-TOKEN`) và `X-XSRF-TOKEN`; service vẫn tự xác minh. Cookie Identity `Path=/` nên không rewrite path. CORS tập trung ở gateway, gateway xóa `Origin` khi
  chuyển tiếp. Gateway xóa `X-User-*` của client và gắn lại `X-User-Id`/`X-User-Roles` từ JWT **chỉ để log**. JWKS của
  Identity gọi trực tiếp (không qua gateway). Service không hardcode host/port. **FE chỉ gọi qua gateway**, không gọi
  trực tiếp service.
- **Service-to-service (mọi lời gọi đi qua ApiGateway)**: service gọi service khác bằng OpenFeign trong `client/`, URL
  `<SERVICE>_<ĐÍCH>_URL` = **gateway** (Docker: `http://api-gateway:8080`), chỉ tới endpoint public `/api/v1/**` như một
  client bình thường; JWKS là ngoại lệ duy nhất gọi thẳng Identity. Có đúng **hai** cách mang danh tính:
  1. **Thay mặt người dùng** — `FeignAuthForwardingInterceptor` chuyển tiếp `Authorization: Bearer <token>` của request
     đang xử lý; service đích phân quyền theo role / phạm vi của chính người dùng đó (vd Booking hỏi CareRecipient
     `/{id}/access`, gọi Catalog báo giá, Identity `/users/batch`).
  2. **Danh tính máy — token SERVICE (IAM-01, 2026-10-22)** — cho lời gọi không có người dùng (scheduler, outbox) hoặc cần
     quyền người dùng không có (vd CareRecipient hỏi Booking `GET /api/v1/bookings/caregiver-access`, Booking hỏi Provider
     `POST /api/v1/caregivers/availability-checks`, Payment xác nhận cọc). Service gọi xin token client-credentials ở
     `POST /api/v1/auth/service-token` (body `clientId`, `clientSecret`; trả `accessToken`, `tokenType`,
     `expiresInSeconds`; `roles=["SERVICE"]`, `sub` = `client_uuid`, claim `client_id`, không refresh, audience
     `careconnect-api`), cache tới `exp − 60s` (`client/ServiceTokenProvider` + `client/ServiceTokenInterceptor`, mẫu ở
     BookingService) và **chỉ** gắn interceptor này vào Feign client cần quyền service (tách client riêng, vd
     `ProviderServiceClient` cạnh `ProviderClient`). Endpoint chỉ dành cho service dùng
     `@PreAuthorize("hasRole('SERVICE')")`; token SERVICE không gọi được API người dùng (`hasRole('CUSTOMER')`…).
     Env ở service gọi: `<SERVICE>_SERVICE_CLIENT_ID` / `<SERVICE>_SERVICE_CLIENT_SECRET` (+ `_SERVICE_TOKEN_*_TIMEOUT`);
     IdentityService chỉ giữ BCrypt hash trong `IDENTITY_SERVICE_CLIENTS` = `<clientId>:<bcrypt>,...` (sinh bằng
     `IdentityService/scripts/New-ServiceClient.ps1 <clientId>`; Docker Compose viết `$` thành `$$`). `SERVICE` không phải
     role gán được cho user; không dùng tài khoản ADMIN làm machine credential (rule #27).

  **Đã bỏ hẳn** `/internal/**`, `X-Internal-Secret`, `*_INTERNAL_SECRET`, `InternalSecurityConfig`, `InternalSecretFilter`
  và API tách theo role (`/api/v1/center/**`, `/admin/**`, `/caregiver/**`) — gateway trả 404, không tạo lại. Endpoint
  phục vụ service khác vẫn phân quyền bằng `@PreAuthorize` như mọi endpoint. DTO client thêm
  `@JsonIgnoreProperties(ignoreUnknown = true)` (vì `fail-on-unknown-properties: true`). Timeout từ env, circuit
  breaker Resilience4j cho lời gọi chỉ đọc, ErrorDecoder map sang `ErrorCode` của service gọi.
- Không tạo thư viện common, discovery server, message broker hay distributed transaction khi chưa có nhu cầu cụ
  thể được yêu cầu. Các class giống nhau giữa service (`APIResponse`, `AppException`, `ErrorCode`,
  `GlobalExceptionHandler`, `BaseEntity`) **chép theo cùng mẫu** vào từng service.

### Package layout (áp dụng cho mọi service)

```
src/main/java/mss301/<service>/
├── <Service>Application.java          — @SpringBootApplication
├── config/                            — @Configuration, @ConfigurationProperties, DataInitializer (seed idempotent), Converter
│   └── swagger/
│       ├── OpenApiConfig.java         — OpenAPI bean, global error responses, bearer scheme
│       └── SwaggerExamples.java       — MỌI chuỗi @ExampleObject (chỉ constants)
├── security/                          — CHỈ cấu hình / adapter bảo mật: SecurityConfig, JwtConfig, CookieUtils,
│                                        entry point, access-denied handler, Permissions (hằng permission code)
├── controller/                        — @RestController mỏng
├── dto/
│   ├── request/
│   └── response/                      — APIResponse<T> + response DTO
├── entity/                            — BaseEntity + entity JPA
├── enums/
├── exception/                         — AppException, ErrorCode, GlobalExceptionHandler
├── mapper/                            — MapStruct, một mapper / domain
├── repository/
├── service/                           — interface FooService
│   └── Impl/                          — FooServiceImpl (giữ chữ I hoa)
├── client/                            — client + DTO gọi service khác (chỉ tạo khi cần)
├── scheduler/                         — @Scheduled job (chỉ tạo khi cần)
├── util/                              — helper tĩnh thuần (vd EmailNormalizer), final class + private constructor
└── validation/                        — custom constraint (vd @StrongPassword)
```

Logic token / user-detail nằm ở `service/` + `service/Impl/` (`JwtService`, `IdentityStateService`,
`CurrentIdentityService`…), **không** nằm trong `security/`. Chỉ tạo package khi có code cần dùng.

**IdentityService hiện có** (tóm tắt):

```
controller/   AuthenticationController (/api/v1/auth/** — đăng ký, OTP, quên mật khẩu, login, refresh, logout, csrf),
              UserController (/api/v1/users/**), RoleController (/api/v1/roles),
              PermissionController (/api/v1/permissions), JwksController (/.well-known/jwks.json)
service/      AuthenticationService, UserService, RegistrationService (OTP đăng ký + quên mật khẩu),
              RoleService, JwtService, IdentityStateService (Redis), CurrentIdentityService, EmailService (Brevo)
entity/       BaseEntity, User, Role, Permission, UserRole(+UserRoleId), RolePermission(+RolePermissionId), IdentityAudit
enums/        RoleCode, UserStatus (ACTIVE/LOCKED), TokenType (access/refresh), AuditAction
dto/response/ APIResponse, UserResponse, RoleResponse, PermissionResponse, OtpResponse, AuthenticationResponse,
              CsrfResponse, AuthenticatedUser (nội bộ: user + authzVersion để phát token)
mapper/       MapStructConfig (@MapperConfig chung), UserMapper, RoleMapper, IdentityAuditMapper, RegistrationMapper,
              AuthenticationMapper, @IgnoreServerManagedFields (BaseEntity), @IgnoreServerManagedUserFields (field riêng User)
config/       IdentityProperties (identity.*), IdentityConfig (BCrypt), BrevoConfig (RestClient + timeout), WebConfig,
              RedisKeys (key/field Redis), DataInitializer (seed role/permission), BootstrapAdmin, swagger/
security/     SecurityConfig,
              JwtConfig (RS256 encoder + decoder), IdentityAuthenticationConverter (JWT → quyền hiện hành),
              SecurityErrorHandler (401/403/503 envelope), CurrentUserId + CurrentUserIdArgumentResolver,
              CookieUtils, Permissions, TokenClaims (tên claim JWT)
util/         EmailNormalizer
validation/   StrongPassword + StrongPasswordValidator (kèm giới hạn 72 byte BCrypt)
```

---

## 3. Code Conventions

### Lombok pattern on service / controller classes

```java
@Service                                                        // hoặc @RestController
@RequiredArgsConstructor                                        // constructor injection — không @Autowired
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)   // mọi field inject đã private final
@Slf4j
public class UserServiceImpl implements UserService {
    UserRepository userRepository;                              // KHÔNG viết thêm "private final"
    RoleRepository roleRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;

    static String SOME_CONSTANT = "...";                        // hằng nội bộ: static (FieldDefaults lo private final)

    @NonFinal
    @Value("${some.property}")                                  // chỉ khi thật sự cần @Value lẻ
    String someProperty;
}
```

- **Tên field inject = tên kiểu viết camelCase đầy đủ**: `userRepository`, `userMapper`, `userService`,
  `identityStateService`. Không đặt tắt kiểu `users`, `mapper`, `service`, `state`, `accounts`.
- Không viết `private final` khi đã có `@FieldDefaults(makeFinal = true)` (thừa, lệch mẫu).

### Config classes

`@Configuration` dùng **constructor injection** (`@RequiredArgsConstructor` + `@FieldDefaults` như trên, hoặc
`private final`) hoặc **`@Bean` method-parameter injection**. Không field-level `@Autowired`. Nhóm cấu hình liên
quan dùng `@ConfigurationProperties` (vd `IdentityProperties` ← `identity.*`).

### Code style (theo code thực tế ở `../backend`)

- **Import tường minh từng class**, không `import x.y.*`. Ngoại lệ giống AIMA: `lombok.*` (DTO/entity),
  `jakarta.persistence.*` (entity), `org.springframework.web.bind.annotation.*` và `dto.request.*` (controller).
  Không viết tên đầy đủ (`java.util.UUID`, `org.springframework...`) trong thân code — import vào.
- **Mỗi annotation một dòng** trên class/method; không dồn `@PostMapping @SecurityRequirements @Operation` trên
  một dòng.
- Không viết method một dòng kiểu `{ return x; }` cho method public; `if` luôn có `{ }`, không
  `if (...) throw ...;` cùng dòng; mỗi statement một dòng (không `a.setX(); b.setY();` cùng dòng).
- Một dòng trống giữa các method; thứ tự trong class: constants → fields inject → `@NonFinal` config → public
  methods (`@Override`) → private helpers.
- Không dùng `var` cho kiểu không hiển nhiên; tên helper private là **động từ** rõ nghĩa
  (`findActiveUser`, `checkCenterScope`, `assignRole`), không đặt tên một từ mơ hồ (`require`, `is`, `scope`).
- Comment tiếng Việt, ngắn, giải thích **vì sao** (không lặp lại code làm gì).
- Logging: `log.info` cho sự kiện nghiệp vụ (tạo tài khoản, khóa, gán role…), `log.warn` cho bất thường có thể
  phục hồi, `log.error` kèm exception cho lỗi dependency. Không log secret (xem rule #26).

### Repository

```java
@Repository                                                     // luôn có, kể cả interface JpaRepository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);         // lookup luôn lọc bản ghi xóa mềm

    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    Optional<User> findForUpdate(@Param("id") UUID id);         // @Query luôn dùng @Param tường minh
}
```

- Không nhúng literal nghiệp vụ trong JPQL (`'ADMIN'`, `'CAREGIVER'`) — bind tham số và truyền `RoleCode.X.name()`.
- Không `findAll()` rồi lọc `deletedAt` trong Java — viết method `...AndDeletedAtIsNull`.
- Method khóa dòng (`@Lock(PESSIMISTIC_WRITE)`) đặt tên `findForUpdate` và có comment vì sao cần khóa.
- PostgreSQL: tham số nullable trong native query cần `CAST(:x AS ...)`; filter động dùng JPA Specification.

### Người dùng hiện tại trong controller

Dùng `@CurrentUserId UUID actorId` (resolver ở `security/CurrentUserIdArgumentResolver`, đăng ký trong
`config/WebConfig`) — tương đương `@AuthenticationPrincipal UserDetails` của AIMA. Controller **không** nhận
`Authentication` rồi tự `UUID.fromString(...)`. Tham số này bị ẩn khỏi Swagger (`OpenApiConfig`).

```java
@GetMapping("/me")
public APIResponse<UserResponse> me(@CurrentUserId UUID actorId) {
    return userService.me(actorId);
}
```

### DTO pattern

Mọi DTO dùng `@Data @Builder @AllArgsConstructor @NoArgsConstructor @FieldDefaults(level = AccessLevel.PRIVATE)`.
Request DTO có Bean Validation với `message` = **key của `ErrorCode`**:

```java
@NotBlank(message = "EMAIL_REQUIRED")   // → ErrorCode.EMAIL_REQUIRED
@Email(message = "INVALID_EMAIL_FORMAT")
String email;

@StrongPassword                          // message mặc định = "WEAK_PASSWORD"
String password;
```

- Tách `dto/request` và `dto/response`. Không trả entity, không nhận entity làm request body. Không trả
  `Map<String, …>` làm result — tạo response DTO (ngoại lệ duy nhất: JWKS, xem rule #3).
- Request public (đăng ký, sửa hồ sơ) **không** chứa role/permission/status/centerId/actor — chống mass assignment;
  `spring.jackson.deserialization.fail-on-unknown-properties: true` đang bật, giữ nguyên.

**Entity:** `@Entity @Table @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@FieldDefaults(level = AccessLevel.PRIVATE)`; collection/giá trị mặc định dùng `@Builder.Default`. Không `@Data`
trên entity (lazy relation + `toString` lộ password). Entity thường **extends `BaseEntity`**
(`id` UUID `@UuidGenerator`, `createdAt`, `updatedAt`, `deletedAt` — kiểu `Instant` ↔ `timestamptz`). Đổi tên cột
PK bằng `@AttributeOverride(name = "id", column = @Column(name = "user_id"))`. Bảng liên kết có metadata
(`user_roles`, `role_permissions`) là entity riêng với `@EmbeddedId` + `@MapsId`, **không** `@ManyToMany`.

### Error handling

**Mọi method service/`*Impl` phải chặn input và các nhánh lỗi bằng exception tường minh — không để giá trị sai,
entity thiếu hay trùng lặp đi tiếp âm thầm.**

1. **Validate request DTO ở boundary**: Bean Validation với `message = "ERROR_CODE_KEY"`, param controller có
   `@Valid`. Thêm `ErrorCode` tương ứng cho mọi message validation mới.
2. **Chặn trong service trước khi thao tác:**
   - Lookup có thể thiếu → `.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED))`.
   - Uniqueness / điều kiện trước → kiểm rồi `throw new AppException(ErrorCode.EMAIL_EXISTED)`; **đồng thời** có
     unique constraint ở DB (hai request song song vẫn có thể lọt qua bước kiểm).
   - Trạng thái nghiệp vụ (tài khoản LOCKED, OTP hết hạn, sai mật khẩu hiện tại, ngoài phạm vi trung tâm…) →
     `ErrorCode` riêng.
3. Service ném `new AppException(ErrorCode.X)`; điều kiện mới chưa có code → **thêm vào `ErrorCode`** (code +
   message **tiếng Anh** ngắn gọn, thân thiện — chuẩn từ 2026-10-30 cho mọi service + `HttpStatus`). `AppException` truyền message của `ErrorCode` lên `super(...)`
   nên `e.getMessage()` luôn có nghĩa trong log.
4. `GlobalExceptionHandler` bắt và trả `APIResponse` với `code`/`message` từ `ErrorCode` + đúng HTTP status.
5. **Không** ném raw exception ra controller, **không** tự dựng `APIResponse` lỗi (kể cả `APIResponse.error(...)`).
6. `try/catch` chỉ khi cần dịch lỗi dependency / fallback có chủ đích: log bằng `@Slf4j` rồi ném lại
   `AppException` hoặc fallback rõ ràng — không nuốt lỗi.
7. Mã HTTP: token thiếu/sai/hết hạn/bị thu hồi → **401**; đã xác thực nhưng thiếu quyền/ngoài phạm vi → **403**;
   không tồn tại → 404; input/enum sai → 400; trùng → 409 (hoặc 400 theo `ErrorCode` hiện có); Redis/DB/email
   không khả dụng → 503. Danh sách rỗng → 200 + list rỗng (không ném lỗi).

### Response envelope

Mọi endpoint nghiệp vụ trả **`APIResponse<T>`** (giữ đúng tên `APIResponse` của dự án này, không đổi sang
`ApiResponse` như AIMA):

```java
return APIResponse.success("message", result);   // thành công có payload
return APIResponse.success("message");           // thành công không payload
```

`@JsonInclude(NON_NULL)` trên `APIResponse` là cố ý — field null bị bỏ khỏi JSON. Format chung:
`{ "code": 200, "message": "...", "result": ... }`. Không dùng `APIResponse.builder()` ở service (thiếu `code` →
thành 0); builder chỉ dùng trong `GlobalExceptionHandler`/security handler để dựng lỗi từ `ErrorCode`.

**Giữ `return` ngắn — tính result DTO vào biến có tên trước:**

```java
UserResponse userResponse = userMapper.toResponse(savedUser);        // ✅
return APIResponse.success("Tạo tài khoản thành công", userResponse);

return APIResponse.success("...", userMapper.toResponse(savedUser)); // ❌
```

### Naming (controllers & services)

- **CRUD**: `create` / `list` / `get` / `update` / `delete` / `updateStatus`. **Business action** dùng động từ
  riêng: `register`, `verify`, `login`, `refresh`, `logout`, `assign`, `revoke`, `lock`… Tên method **không lặp
  tên entity** đã có trong tên class (`UserController.get`, không `getUser`).
- Interface `FooService` ở `service/`, implementation `FooServiceImpl` ở `service/Impl/`.
- Async job (khi có ở service sau): service `start...` + `getJob`; worker luôn `process(UUID)`.
- Method cũ trái quy tắc được đổi tên khi chạm tới — không mass-rename.

### Aggregate & versioning (BẮT BUỘC — 2026-10-18)

- **Mỗi entity gốc (aggregate) có đúng một bộ**: `Foo` → `FooRepository` → `FooService` (interface, `service/`) →
  `FooServiceImpl` (class, `service/Impl/`) → `FooController` (`controller/`). Entity con chỉ có `Repository` riêng;
  nghiệp vụ của nó nằm trong service của aggregate cha (vd `Medication` thuộc `CareRecipientService`… trừ khi tự là
  aggregate độc lập có API riêng). Không tách service/controller theo role hay theo chức năng
  (`Admin*`, `Customer*`, `Caregiver*`, `*Management*`, `Internal*`).
- **`repository/` và `service/` chỉ chứa `interface`.** Mọi logic nằm trong `*ServiceImpl`; repository chỉ khai báo
  method Spring Data / `@Query`, không có `class` hay `default` method chứa logic. Class bảo mật như
  `ReadOnlyCookieCsrfTokenRepository` thuộc `security/`, không phải repository dữ liệu.
- **Version bắt buộc**: mọi `@RequestMapping` cấp class bắt đầu bằng `/api/v1/<resource>` (số nhiều, kebab-case).
  Gateway **không cắt prefix** — path ở gateway = path ở service. Đổi hợp đồng phá vỡ → mở `/api/v2/...`, không sửa v1.
- **Auth tách khỏi User**: đăng ký / OTP / quên-đặt lại mật khẩu / login / refresh / logout / csrf nằm ở
  `AuthenticationController` (`/api/v1/auth/**`); `UserController` (`/api/v1/users/**`) chỉ lo hồ sơ cá nhân, đổi mật
  khẩu và quản trị tài khoản.
- **Entity giữ `@Getter @Setter`** (Lombok) — MapStruct và service cần accessor; **cấm `@Data`** trên entity
  (`toString`/`equals` kéo lazy relation, lộ secret). Không tự viết getter/setter thủ công.
- **Mapper**: một `@Mapper(componentModel = "spring")` cho mỗi aggregate, kế thừa `MapStructConfig`. Có thể dùng
  plugin **JPA Buddy** (IntelliJ) để sinh khung mapper/DTO từ entity — plugin chỉ là công cụ IDE, không phải dependency;
  thư viện chạy lúc build vẫn là MapStruct. Sinh xong phải rà lại: bỏ field server-managed
  (`@IgnoreServerManagedFields`), không map field nhạy cảm, đặt đúng package `mapper/`.

### Swagger

- `@Tag(name = ..., description = ...)` trên mỗi controller. **Không** dùng `@Operation` và
  `@io.swagger.v3.oas.annotations.parameters.RequestBody` trong controller (bỏ ngày 2026-10-01) — springdoc tự suy
  ra endpoint và schema request từ `@RequestBody` của Spring.
- Endpoint public có `@SecurityRequirements({})` **và** phải nằm trong danh sách permit của `SecurityConfig`.
- Controller **chỉ** dùng `mss301.<service>.dto.response.APIResponse`; không dùng annotation Swagger
  `io.swagger.v3.oas.annotations.responses.ApiResponse`. Global 400/401/403/500 khai báo trong `OpenApiConfig`.
- **Sắp xếp method trong Controller & Swagger UI (BẮT BUỘC — 2026-10-19):**
  - Trong source code controller: gom cụm các method theo HTTP method theo thứ tự: các method `@GetMapping` trước,
    tiếp theo đến `@PostMapping`, `@PutMapping`, `@PatchMapping`, và cuối cùng là `@DeleteMapping`.
  - Cấu hình Swagger UI trong `application.yaml`: luôn bật `springdoc.swagger-ui.operationsSorter: method` và
    `springdoc.swagger-ui.tagsSorter: alpha` để Swagger UI tự động gom nhóm và sắp xếp các endpoint theo HTTP method.

### Quy tắc MapStruct / tầng mapper (BẮT BUỘC — mọi service, hiện tại và sau này)

Chuẩn rút ra từ đợt rà soát 2026-10-03 (`docs/2026-10-03-MAPPER_REVIEW.md`). IdentityService và CareRecipientService
đã theo đúng mục này; code cũ lệch quy tắc phải sửa khi chạm tới.

**1. Cấu hình chung — mỗi service đúng MỘT `@MapperConfig`** (`mapper/MapStructConfig`), mọi mapper khai báo
`@Mapper(config = MapStructConfig.class)` (thêm `uses`/`imports` khi cần), không tự cấu hình riêng:

```java
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR,        // quên map/ignore thuộc tính đích = lỗi compile
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,  // mapper con (uses) inject qua constructor
        builder = @Builder(disableBuilder = true))          // builder Lombok không set được field của BaseEntity
public interface MapStructConfig {
}
```

**2. Annotation ignore trường hệ thống — mỗi service có `mapper/IgnoreServerManagedFields`** (`id`, `createdAt`,
`updatedAt`, `deletedAt` của `BaseEntity`), gắn trên MỌI method tạo/cập nhật entity kế thừa `BaseEntity`. Field server
quản lý riêng của một entity, dùng lặp lại ở nhiều method, gom vào `IgnoreServerManaged<Entity>Fields` (vd
`IgnoreServerManagedUserFields`: password, status, centerId, authzVersion, userRoles; `IgnoreServerManagedRecipientFields`:
status, version) và gắn CÙNG `@IgnoreServerManagedFields`. Không lặp `@Mapping(target = "id", ignore = true)`… ở từng
method (ngoại lệ: entity không kế thừa BaseEntity và chỉ có một method tạo — vd bảng liên kết).

**3. Giữ / bỏ `@Mapping`.** Chỉ GIỮ khi thuộc một trong các trường hợp:
- tên nguồn và đích khác nhau (`userStatus ← profile.status`, `profile ← recipient`);
- lấy từ object lồng / entity quan hệ (`recipientId ← recipient.id`, `myAccessLevel ← membership.accessLevel`);
- nhiều tham số nguồn cùng có thuộc tính trùng tên → chỉ rõ nguồn (`id ← manager.id` khi `profile` cũng có `id`);
- `ignore = true` cho trường server quản lý hoặc quan hệ entity không lấy từ request;
- `expression` / `qualifiedByName` / `defaultValue` / `constant` thật sự cần (khóa ghép, giá trị dẫn xuất, null → giá trị
  mặc định cho kiểu primitive);
- một tham số nguồn mà chính tham số đó là thuộc tính đích (`user ← user` trong `toAuthenticatedUser(User user)`);
- whitelist của `@BeanMapping(ignoreByDefault = true)`.

BỎ mọi `@Mapping` MapStruct tự làm được: cùng tên + kiểu tương thích (kể cả enum → String, Boolean → boolean), tham số
method trùng tên thuộc tính đích, thuộc tính chỉ một nguồn có.

**4. Phân loại method & tên**

| Loại | Chữ ký / cấu hình | Ghi chú |
|---|---|---|
| Read | `toResponse(E)`, `toResponseList(List<E>)`; response phụ `toSummaryResponse`, `toDetailResponse`, `toSnapshotResponse`, `toMeResponse`… | Không lặp tên entity của mapper |
| Create | `E to<Entity>(Request request[, giá trị service quyết định…])` | `@IgnoreServerManagedFields` (+ annotation riêng); quan hệ cha truyền vào (tham số hoặc `@Context`), không lấy từ request |
| Danh sách con tạo cùng cha | `List<E> to<Entity>List(List<Request>, @Context Parent parent, …)` + `@IterableMapping(nullValueMappingStrategy = RETURN_DEFAULT)` | Service gọi `repository.saveAll(mapper.to…List(...))`, không tự viết vòng lặp map |
| Update toàn phần (PUT) | `void update(Request, [UUID updatedBy,] @MappingTarget E)` — mặc định, **null ghi đè** | Field PUT không được đổi (vd `active`, `primary`, `version`) phải `ignore` rõ ràng + comment |
| Update một phần (PATCH) | `@BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = IGNORE)` + whitelist `@Mapping(target = …)` | Tên `update` (hoặc `updateXxx` khi có nhiều nguồn) |
| Entity liên kết / audit | `toUserRole(User user, Role role, UUID assignedBy)`, `toAudit(...)` | Không `new` + setter trong service |

Mỗi method PUT/PATCH phải khớp ngữ nghĩa contract của API gọi nó. Nếu contract đã công bố khác tên method HTTP (vd
`PUT /users/me` của IdentityService là cập nhật một phần) thì giữ contract và ghi comment trên method mapper.
Một mapper / domain; quan hệ cha–con dùng `uses = {...}`. Xóa method không ai gọi (method phần tử của list để MapStruct
tự sinh); method phục vụ API nội bộ (`/internal/**`, snapshot, batch) giữ và ghi comment.

**5. Không đưa vào mapper:** truy vấn DB, gọi Feign/HTTP, kiểm tra quyền, mã hóa mật khẩu, gán role, quy tắc nghiệp vụ
(trạng thái mặc định như `ACTIVE` / `active = true`, OWNER / quản lý chính, ai là người tạo). Service quyết định rồi gán,
hoặc truyền vào mapper dưới dạng tham số nếu chỉ là gán giá trị (vd `toCareRecipient(request, actorId)`). Logic dẫn xuất
mà service cũng dùng (vd quyền hiệu lực) đặt ở entity (`User.effectiveRoleCodes()`) — mapper chỉ gọi qua `expression`;
method đó không đặt tên `getXxx` để không thành thuộc tính. Hàm tính toán thuần chỉ phục vụ response (vd `calculateAge`)
được là `default` method của mapper.

**6. Trường nhạy cảm** (`password`, password hash, OTP, token, secret, private key): luôn `ignore` rõ ràng khi map vào
entity (service tự gán sau khi xử lý) và KHÔNG BAO GIỜ có trong response DTO. Ngoại lệ duy nhất đã chốt:
`AuthenticationResponse` của `/auth/mobile/*` trả access/refresh token theo thiết kế (rule #13); bản web phải `ignore`
token. Có unit test khẳng định response DTO không có field nhạy cảm.

**7. Ví dụ** (`RecipientManagerMapper` và `CareRecipientServiceImpl.create`, trước / sau 2026-10-03):

```java
// SAI — cấu hình riêng; createdAt/fullName/email/phoneNumber chỉ một nguồn có → @Mapping thừa
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, builder = @Builder(disableBuilder = true))
public interface RecipientManagerMapper {
    @Mapping(target = "id", source = "manager.id")
    @Mapping(target = "createdAt", source = "manager.createdAt")
    @Mapping(target = "phoneNumber", source = "profile.phoneNumber")
    @Mapping(target = "fullName", source = "profile.fullName")
    @Mapping(target = "email", source = "profile.email")
    @Mapping(target = "userStatus", source = "profile.status")
    @Mapping(target = "profileAvailable", expression = "java(profile != null)")
    RecipientManagerResponse toResponse(RecipientManager manager, IdentityUserResponse profile);

// ĐÚNG — config chung; chỉ giữ trùng tên giữa nguồn (id), đổi tên (userStatus), dẫn xuất (profileAvailable)
@Mapper(config = MapStructConfig.class)
public interface RecipientManagerMapper {
    @Mapping(target = "id", source = "manager.id")
    @Mapping(target = "userStatus", source = "profile.status")
    @Mapping(target = "profileAvailable", expression = "java(profile != null)")
    RecipientManagerResponse toResponse(RecipientManager manager, IdentityUserResponse profile);
```

```java
// SAI — service tự lặp và set tay field mapper làm được
for (EmergencyContactRequest contactRequest : request.getEmergencyContacts()) {
    EmergencyContact contact = emergencyContactMapper.toEmergencyContact(contactRequest, savedRecipient);
    contact.setPrimary(Boolean.TRUE.equals(contactRequest.getPrimary()));
    emergencyContacts.add(emergencyContactRepository.save(contact));
}
// ĐÚNG
List<EmergencyContact> emergencyContacts = emergencyContactRepository.saveAll(
        emergencyContactMapper.toEmergencyContactList(request.getEmergencyContacts(), savedRecipient));
```

**8. Checklist khi tạo service / mapper mới**
- [ ] Có `mapper/MapStructConfig` (§1) và `mapper/IgnoreServerManagedFields` (§2); mọi mapper khai báo `config = ...`.
- [ ] `mvnw clean compile` không lỗi, không warning của MapStruct.
- [ ] Đọc `target/generated-sources/annotations/**/*MapperImpl.java`: mỗi field đích lấy đúng nguồn, không field nào bị
  bỏ sót, không trường nhạy cảm / server quản lý nào bị chép từ request.
- [ ] Mỗi `@Mapping` thuộc danh sách GIỮ ở §3; method không ai gọi đã xóa.
- [ ] Service không còn `new XxxResponse`, builder gán từng field, hay chuỗi `entity.setX(request.getX())`.
- [ ] Unit test cho mapping không hiển nhiên: nhiều nguồn trùng tên, nguồn phụ null (vd profile null →
  `profileAvailable = false`), PATCH bỏ qua null, PUT ghi đè null, field server / nhạy cảm không bị chép
  (mẫu: `IdentityMapperTests`, `CareRecipientMapperTests` — dùng thẳng `XxxMapperImpl`, không cần Spring).

---

## 4. Authentication Flow

> **Kiến trúc:** IdentityService là nơi **duy nhất phát token**. JWT ký **RS256** bằng private key (chỉ
> IdentityService giữ), public key công bố qua **JWKS** `GET /.well-known/jwks.json` (có `kid`). Mọi service
> (kể cả IdentityService) xác minh token bằng **Spring Security OAuth2 Resource Server** (`JwtDecoder` +
> converter authorities) — **không** viết `JwtAuthenticationFilter` tự chế như AIMA, không chạy song song hai cơ
> chế. Token state (refresh `jti`, blacklist, OTP) nằm trên **Redis của IdentityService**, không có bảng token
> trong DB. Logic token ở `service/JwtService` + `service/Impl/JwtServiceImpl`; `security/` chỉ cấu hình.

### ACCESS token (RS256)

```
header: alg=RS256, kid=IDENTITY_JWT_KEY_ID
sub (= userId UUID, KHÔNG phải email), iss, aud[] = [IDENTITY_JWT_AUDIENCE, "careconnect-api"], iat, exp, jti,
token_type="access", authz_version (số), roles[] (role code), permissions[] (permission code),
center_id (chỉ khi user có membership trung tâm)
```

- TTL: `IDENTITY_ACCESS_TTL` (bắt buộc cấu hình; dev mẫu 15 phút).
- Web: HttpOnly cookie `access_token`. Mobile/Swagger: header `Authorization: Bearer <token>`.
- Blacklist khi logout: Redis `careconnect:identity:blacklist:{jti}`, TTL = thời gian sống còn lại.
- Không nhét dữ liệu nhạy cảm (hồ sơ bệnh nhân, giấy tờ, password) vào JWT — JWT ký chứ không mã hóa.
- Converter: `roles[]` → `ROLE_<code>`, `permissions[]` → giữ nguyên mã (`user:read`). Không trộn prefix.

### REFRESH token (cũng là JWT, `token_type="refresh"`)

JWT RS256 không có roles/permissions. Web: HttpOnly cookie `refresh_token`; mobile: trả trong body, app lưu ở OS
secure storage. `jti` được lưu Redis `careconnect:identity:rt:{jti}` → userId. Rotation **claim nguyên tử bằng Lua
script** (`IdentityStateService.consumeRefresh`): chỉ một request refresh đồng thời thành công; token đã rotate /
thu hồi → 401. Refresh token **không** dùng để gọi API (decoder resource server bắt buộc `token_type=access`).

### Xác minh ở mọi service (Resource Server)

Decoder bắt buộc kiểm: chữ ký RS256 (allowlist thuật toán), `iss`, `aud` chứa audience của service, `exp`/`iat`,
`jti`, `sub` là UUID, `token_type == "access"` (`JwtConfig`: bean `identityJwtDecoder` kiểm claim chung, bean
`@Primary jwtDecoder` chỉ nhận access). IdentityService còn kiểm thêm mỗi request (`IdentityAuthenticationConverter`
→ `CurrentIdentityService.getActiveUser`): jti không bị blacklist, user tồn tại, chưa soft delete, `ACTIVE`,
`authz_version` khớp DB, rồi nạp quyền **hiện hành** từ DB — nên khóa tài khoản / thu hồi quyền có hiệu lực ngay tại
IdentityService. Tên authentication = userId (đọc bằng `@CurrentUserId`).

> ⚠️ Service khác chỉ xác minh offline thì **không** biết ngay việc khóa/thu hồi — độ trễ tối đa = access TTL.
> Không tuyên bố "thu hồi ngay toàn hệ thống" cho tới khi có contract kiểm `authz_version`/status (chưa chốt).
> Audience: IdentityService kiểm `careconnect-identity`; **mọi service backend khác và gateway chỉ kiểm audience chung
> `careconnect-api`** (`TokenClaims.API_AUDIENCE`, chỉ có trong access token — refresh token không có). **Không** tắt
> kiểm audience cho tiện.

### Login — `POST /auth/login` (web) · `POST /auth/mobile/login` (mobile)

1. Chuẩn hóa email (trim + lowercase), tìm user chưa xóa.
2. BCrypt verify. Email không tồn tại và sai mật khẩu trả **cùng** `INVALID_CREDENTIALS` (không lộ tài khoản).
3. Kiểm status: `LOCKED` → `USER_INACTIVE` (403).
4. Phát ACCESS + REFRESH (jti mới), lưu refresh jti vào Redis **trước** khi trả thành công (Redis lỗi → 503, không
   login nửa vời).
5. Web: set 2 HttpOnly cookie qua `CookieUtils`, trả `csrfToken` mới, **không** trả token trong body.
   Mobile: trả access + refresh trong body, không set cookie.

### Registration CUSTOMER — email/password + OTP

Tài khoản chỉ được tạo **sau khi** xác thực email. State trên Redis
`careconnect:identity:otp:{purpose}:{email}` (purpose tách đăng ký và quên mật khẩu):

1. `POST /users/register` — email đã tồn tại → `EMAIL_EXISTED` 1003, không gửi OTP. Còn cooldown →
   `OTP_RESEND_TOO_SOON` 1079. Lưu pending (fullName, phone, password **đã BCrypt**) + hash OTP (`SecureRandom`
   6 số), gửi email qua `EmailService`. Gửi lỗi → xóa OTP để user thử lại ngay (`EMAIL_DELIVERY_FAILED` 1106).
2. `POST /users/register/resend-otp` — cần pending còn hạn (`REGISTRATION_SESSION_EXPIRED` 1080); mã mới thay mã
   cũ, reset bộ đếm sai.
3. `POST /users/register/verify` — sai mã → `INVALID_OTP_OR_EMAIL` 1063; quá `IDENTITY_OTP_MAX_ATTEMPTS` →
   `OTP_ATTEMPTS_EXCEEDED` 1072; hết hạn → `OTP_NOT_FOUND` 1060. Mã đúng **dùng một lần** (consume nguyên tử).
   Kiểm lại email (unique violation → 1003), tạo user + gán role `CUSTOMER` trong **một transaction**, ghi audit.
   Không tự login — client gọi login riêng.

Đăng ký public **chỉ** cấp `CUSTOMER`; caller không chọn được role khác.

### Provision tài khoản (quản trị) — `POST /users`

- `ADMIN` tạo `CUSTOMER` hoặc `CENTER_MANAGER` (bắt buộc `centerId` cho manager).
- `CENTER_MANAGER` tạo `CAREGIVER`; `centerId` **lấy từ manager**, gửi centerId khác → từ chối.
- Tài khoản provision **chưa có password** (`password` null); chủ email tự đặt qua quên mật khẩu (OTP).
- Chưa có CenterService nên IdentityService chưa xác minh `centerId` tồn tại (xem `docs/PLAN.md`).

### Refresh — `POST /auth/refresh` (web, cookie) · `POST /auth/mobile/refresh` (mobile, body)

1. Lấy refresh token → thiếu → `UNAUTHENTICATED`.
2. `JwtService.parseRefresh` (chữ ký, claims, `token_type == "refresh"`).
3. Consume `jti` nguyên tử trên Redis → không còn → 401 (đã rotate/thu hồi).
4. Kiểm user còn `ACTIVE`, chưa xóa, `authz_version` khớp; lấy role/permission **hiện hành** từ DB.
5. Phát cặp token mới, lưu jti mới; web set lại cookie + trả `csrfToken` mới.

### Logout — `POST /auth/logout` (web + mobile + mọi phiên, gộp 2026-10-22)

- Logout: thu hồi refresh jti, blacklist access hiện tại tới `exp`, xóa cookie (cả CSRF) — token đã hết hạn vẫn
  logout được. Mobile gửi `refreshToken` trong body; `/auth/mobile/logout` đã bỏ (2026-10-22).
- Body `allSessions: true` (thay `/auth/logout-all`, đã bỏ 2026-10-22): xóa mọi session Redis + tăng
  `users.authz_version` → mọi token cũ mất hiệu lực tại IdentityService. Chủ tài khoản lấy từ refresh còn trên Redis
  hoặc access hợp lệ đầy đủ (chưa blacklist, `ACTIVE`, version khớp); không có → 401.
- Đổi mật khẩu, reset mật khẩu, khóa tài khoản, xóa mềm cũng tăng `authz_version`.

### Web cookies, CSRF, CORS

- Web lấy CSRF qua `GET /auth/csrf`, gửi header `X-XSRF-TOKEN` ở mọi request ghi (kể cả refresh/logout). Cookie
  CSRF `XSRF-TOKEN`. Request mang cookie **luôn** phải qua CSRF, kể cả khi có thêm bearer header; request bearer
  thuần (mobile) được miễn. Đăng ký / OTP / quên-đặt lại mật khẩu (`/users/register*`, `/users/forgot-password`,
  `/users/reset-password`) được miễn khi request **không mang cookie nào** (mobile, 2026-10-03) — không dựa vào phiên,
  không đặt cookie. Thiếu/sai CSRF → 403 `CSRF_INVALID` 1111 (tách khỏi `UNAUTHORIZED` 403 thiếu quyền).
- Swagger: scheme `csrf` (apiKey header `X-XSRF-TOKEN`) được `OpenApiConfig.csrfRequirements` gắn vào mọi
  POST/PUT/PATCH/DELETE trừ `/auth/mobile/*`; cookie CSRF là HttpOnly nên phải dán `result.token` của `/auth/csrf`
  vào Authorize (tính năng tự gắn CSRF của springdoc không đọc được cookie).
- Cookie `Secure` theo `IDENTITY_COOKIE_SECURE` (production = true), SameSite theo `IDENTITY_COOKIE_SAME_SITE`.
- CORS allowlist từ `IDENTITY_ALLOWED_ORIGINS` + credentials; không wildcard origin khi có credentials.
- FE web không đọc/lưu token; gọi `GET /users/me` để biết mình là ai.

### Email transport (Brevo HTTP API)

`EmailService`/`EmailServiceImpl` dựng nội dung và gửi qua Brevo Transactional Email API bằng `RestClient` có
connect/read timeout. Config `identity.brevo.*` (env `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME`).
**Không** gửi email trong DB transaction; test luôn mock email.

### Password policy

Đăng ký, reset, đổi mật khẩu dùng **cùng** `@StrongPassword` (`WEAK_PASSWORD` 1074): ≥ 8 ký tự gồm chữ thường,
chữ hoa, số, ký tự đặc biệt, tối đa **72 byte UTF-8** (giới hạn BCrypt). Lưu BCrypt hash, không bao giờ plain text.

### Password reset (OTP) & đổi mật khẩu

- `POST /users/forgot-password` — luôn trả message trung tính (không lộ email có tồn tại); gửi OTP purpose reset.
- `POST /users/reset-password` — `email, otpCode, newPassword`; OTP được consume **ngay trong thao tác reset**
  (không có bước verify tạo cờ dùng lại được). Thành công → cập nhật hash + tăng `authz_version`.
- `POST /users/me/change-password` — authenticated, cần `currentPassword` (`PASSWORD_INCORRECT` 1070); thành công
  thu hồi mọi phiên. **Không** áp giới hạn 7 ngày của AIMA.

### Protected request flow

```
Request → BearerTokenAuthenticationFilter (Resource Server)
       → BearerTokenResolver: Authorization header, hoặc cookie access_token (web)
       → JwtDecoder: RS256 + iss + aud + exp + jti + sub UUID + token_type=access
       → blacklist jti? (Redis) → 401
       → IdentityAuthenticationConverter → CurrentIdentityService.getActiveUser(userId, authz_version)
         → user ACTIVE + version khớp, nạp authorities hiện hành (ROLE_<code> + permission code)
       → @EnableMethodSecurity: @PreAuthorize (role + permission) ở controller
       → @CurrentUserId bind userId → service kiểm resource scope (self / cùng trung tâm / target role)
   SecurityErrorHandler (entry point + access denied + failure handler, serialize APIResponse bằng ObjectMapper):
   token sai → 401; thiếu quyền → 403; Redis/DB sập → 503 AUTH_DEPENDENCY_UNAVAILABLE (không giả làm 401, không bypass).
```

### Không có trong phạm vi hiện tại

Google OAuth2, OAuth2/OIDC Authorization Server chuẩn (discovery/grant), xóa tài khoản kiểu GDPR / grace period
30 ngày, key ring rotate RSA không gián đoạn. Làm khi được yêu cầu; nếu làm Google login thì không tự ghép tài
khoản chỉ vì trùng email và không ghi đè phương thức đăng nhập.

---

## 5. Database & Redis

### Không dùng Flyway

- Mỗi service có file DDL khởi tạo `schema/<service>.sql` (IdentityService: `schema/identity.sql`) — **áp dụng tay
  một lần cho DB mới** (`psql -v ON_ERROR_STOP=1`). DB đã có dữ liệu → viết change script riêng, được review, đặt
  ở `schema/changes/` (vd `schema/changes/2026-10-05_add_x.sql`) **và** cập nhật luôn `schema/<service>.sql`.
- **Nợ kỹ thuật (2026-10-03):** chuyển toàn bộ service sang Flyway trong **một đợt riêng** (chưa làm); tới lúc đó giữ
  SQL áp tay cho mọi service, không chuyển lẻ.
- `spring.jpa.hibernate.ddl-auto: validate`, `spring.sql.init.mode: never`. Không `update`/`create`/`create-drop`
  ngoài test DB cô lập; không chạy DDL trong initializer lúc khởi động.
- `validate` không kiểm index/CHECK/unique — những thứ đó phải có trong SQL. Thêm giá trị enum → sửa CHECK
  constraint trong SQL cùng lúc.
- Thời điểm dùng `Instant` ↔ `timestamptz`, JVM/Hibernate chạy UTC (`hibernate.jdbc.time_zone: UTC`); FE tự đổi
  sang múi giờ người dùng.

### PostgreSQL / Redis của IdentityService

```
DB:     IDENTITY_DB_URL (…/CARECONNECT_IDENTITY), IDENTITY_DB_USERNAME, IDENTITY_DB_PASSWORD — dev Docker 127.0.0.1:5441
Redis:  IDENTITY_REDIS_HOST / IDENTITY_REDIS_PORT (dev 6381) / IDENTITY_REDIS_PASSWORD, timeout 2s
Prefix: careconnect:identity:  → rt:{jti}, blacklist:{jti}, otp:{purpose}:{email}
```

Service mới dùng DB riêng và prefix Redis `careconnect:<service>:`. Không đọc Redis/DB nội bộ của IdentityService.

### `users` — `User.java` (extends `BaseEntity`)

| Column | Type | Notes |
|---|---|---|
| `user_id` | UUID (PK) | `@AttributeOverride` của `BaseEntity.id` |
| `email` | VARCHAR | not null, unique (`uk_users_email`), CHECK lưu dạng `lower(btrim(email))` |
| `password` | VARCHAR | BCrypt; **nullable** (tài khoản provision chưa đặt mật khẩu) |
| `full_name` | VARCHAR | not null |
| `phone_number` | VARCHAR | nullable |
| `user_status` | VARCHAR | `UserStatus` (`@Enumerated(STRING)`): `ACTIVE`, `LOCKED` — CHECK constraint |
| `center_id` | UUID | nullable; tham chiếu **ngoài** tới trung tâm (không FK). Manager/caregiver thuộc đúng một trung tâm |
| `authz_version` | BIGINT | not null, mặc định 0; tăng khi logout mọi phiên / đổi-reset mật khẩu / khóa / xóa mềm |
| `created_at` / `updated_at` / `deleted_at` | TIMESTAMPTZ | từ `BaseEntity` (soft delete) |

### `roles` — `Role.java` (extends `BaseEntity`)

| Column | Type | Notes |
|---|---|---|
| `role_id` | UUID (PK) | |
| `role_code` | VARCHAR | unique (`uk_roles_code`); 4 role nền tảng theo `RoleCode` |
| `role_name` | VARCHAR | not null |
| `description` | VARCHAR | nullable |
| `is_active` | BOOLEAN | `false` → role bị loại khỏi quyền hiệu lực (kể cả lúc refresh) |
| `created_at` / `updated_at` / `deleted_at` | TIMESTAMPTZ | |

### `permissions` — `Permission.java` (extends `BaseEntity`)

| Column | Type | Notes |
|---|---|---|
| `permission_id` | UUID (PK) | |
| `permission_code` | VARCHAR | unique; dạng `<resource>:<action>` |
| `permission_name` | VARCHAR | not null |
| `resource` | VARCHAR | not null |
| `description` | VARCHAR | nullable |
| `created_at` / `updated_at` / `deleted_at` | TIMESTAMPTZ | |

### `user_roles` — `UserRole.java` (`@EmbeddedId UserRoleId`)

| Column | Type | Notes |
|---|---|---|
| `user_id` + `role_id` | UUID (PK composite, FK nội bộ) | `@MapsId` |
| `assigned_by` | UUID | actor lấy từ token, **không** từ request |
| `assigned_at` | TIMESTAMPTZ | not null |

### `role_permissions` — `RolePermission.java` (`@EmbeddedId RolePermissionId`)

| Column | Type | Notes |
|---|---|---|
| `role_id` + `permission_id` | UUID (PK composite, FK nội bộ) | `@MapsId` |
| `granted_at` | TIMESTAMPTZ | not null |

### `identity_audit` — `IdentityAudit.java`

`id`, `actor_id`, `target_id`, `center_id`, `action`, `detail`, `created_at` — ghi mọi thao tác quản trị
(tạo/khóa/xóa tài khoản, gán/thu hồi role, đổi permission của role).

### Permission catalog hiện có (`security/Permissions`)

| Code | Dùng cho |
|---|---|
| `user:read` | Xem danh sách / chi tiết tài khoản trong phạm vi |
| `user:manage` | Sửa hồ sơ, khóa/mở, xóa mềm tài khoản trong phạm vi |
| `manager:create` | ADMIN tạo CENTER_MANAGER |
| `caregiver:create` | CENTER_MANAGER tạo CAREGIVER |
| `role:manage` | ADMIN quản lý role/permission, gán/thu hồi role |

Role/permission seed **idempotent** ở `DataInitializer`; grants của 4 role nền tảng cố định, không sửa qua API.
Role bổ sung (custom) có thể tạo và cấu hình permission. Không seed tài khoản admin có mật khẩu hardcode — admin
đầu tiên qua `IDENTITY_BOOTSTRAP_*` (bật một lần rồi tắt). Service sau thêm permission theo cùng dạng
`<resource>:<action>` (vd `booking:assign`, `care_report:create`) — catalog chưa chốt, hỏi trước.

---

## 6. Commands

Trong `CareConnect/IdentityService` (PowerShell, JDK 21, Docker):

```powershell
Copy-Item .env.example .env          # điền DB password, Brevo key/sender
.\scripts\New-DevKeys.ps1            # tạo RSA PEM vào keys/ (không ghi đè)
docker compose up -d                 # PostgreSQL :5441 + Redis :6381 riêng của service
Get-Content -Raw schema/identity.sql | docker compose exec -T postgres psql -U careconnect_identity -d CARECONNECT_IDENTITY -v ON_ERROR_STOP=1   # chỉ DB mới
.\scripts\Start-Dev.ps1              # nạp .env vào process env rồi spring-boot:run
.\mvnw.cmd test                      # test mặc định: H2 + Redis giả trong bộ nhớ + email mock
.\mvnw.cmd package                   # build JAR
```

- Spring Boot **không tự đọc `.env`** — chạy qua `Start-Dev.ps1` hoặc tự cấp env trong IDE.
- Toàn bộ 8 app (7 service + gateway) + hạ tầng trong Docker: từ `BE/` chạy `start.bat` / `stop.bat` (xem `DOCKER.md`).
  Mỗi service khai báo phần của mình ở
  `<Service>/compose.stack.yaml` (biến `${...}` đọc từ `.env` của service đó, `environment` chỉ ghi đè host/port
  container — mọi `*_URL` Feign trỏ `http://api-gateway:8080`, JWKS trỏ `http://identity-service:8081`, không
  `localhost`) và `<Service>/Dockerfile`. Service mới: thêm hai file này + một dòng `include` trong `compose.yaml`.
  Không chạy song song với `docker-compose.yml` riêng (chung volume).
- E2E toàn hệ thống (Identity, CareRecipient, CareCatalog, Provider, Booking, Media + Gateway; PaymentService thay bằng
  `scripts/e2e/payment_stub.py`; PostgreSQL/Redis thật, compose project riêng, không đụng DB dev; client token SERVICE
  sinh mỗi lần chạy): từ thư mục `BE/` chạy `.\scripts\e2e\Run-E2E.ps1` (`-SkipBuild`, `-KeepInfra`). Cần JDK 21,
  Docker, Python 3, `.env` của Identity / CareRecipient / CareCatalog / Provider / Media / ApiGateway, port 8080–8087 trống.
- Kiểm PostgreSQL/Redis thật: dùng container test riêng (xem `IdentityService/README.md`), cờ
  `-Didentity.testing.postgres=true -Didentity.testing.real-state=true`. Không trỏ test vào DB thật.
- Không dùng `-DskipTests` làm bằng chứng đã kiểm chứng.

**Runtime URLs (IdentityService, dev):**

```
API:      http://localhost:8081
Swagger:  /swagger-ui.html   (tắt bằng IDENTITY_SWAGGER_ENABLED=false ở production)
OpenAPI:  /v3/api-docs
JWKS:     /.well-known/jwks.json
Health:   /actuator/health
```

**Biến môi trường** (xem `.env.example`, chia nhóm): `IDENTITY_SERVER_PORT`, `IDENTITY_SWAGGER_ENABLED` ·
`IDENTITY_DB_*` · `IDENTITY_REDIS_*` · `IDENTITY_JWT_ISSUER/AUDIENCE/KEY_ID/PRIVATE_KEY/PUBLIC_KEY` ·
`IDENTITY_ACCESS_TTL/REFRESH_TTL/OTP_TTL/OTP_COOLDOWN/OTP_MAX_ATTEMPTS` (bắt buộc, không có default) ·
`IDENTITY_ALLOWED_ORIGINS`, `IDENTITY_COOKIE_SECURE`, `IDENTITY_COOKIE_SAME_SITE` · `BREVO_*` ·
`IDENTITY_BOOTSTRAP_*`. Service mới dùng prefix env riêng (`<SERVICE>_...`), port riêng, không đụng port của AIMA.

---

## 7. Rules

> ⚠️ **BẮT BUỘC — không phải gợi ý.** Mọi thay đổi trong `CareConnect/` PHẢI theo package layout ở §2 và mọi rule
> dưới đây. Trước khi kết thúc task, agent PHẢI tự đối chiếu diff với mục này và sửa chỗ lệch. Nếu thật sự không
> đáp ứng được, DỪNG lại và nói rõ lý do — không âm thầm phá cấu trúc. File mới đặt đúng package theo vai trò
> (`controller/`, `service/` + `service/Impl/`, `dto/request|response/`, `mapper/`, `config/...`); controller
> không bao giờ chứa business logic hay dựng DTO.

1. **Không đổi tên** package, class, interface public, key `ErrorCode` đã có (`APIResponse`,
   `mss301.identityservice`, `UserService`…). Chỉ đổi khi được yêu cầu và sửa mọi caller cùng lúc.

   **1a. Một Controller + một Service cho mỗi domain aggregate — KHÔNG tách theo role/chức năng.** Ví dụ
   `Booking` → `BookingRepository` / `BookingService` + `BookingServiceImpl` / `BookingController`. Mọi thao tác của
   CUSTOMER / CAREGIVER / CENTER_MANAGER / ADMIN trên aggregate đó nằm chung một controller, phân quyền **từng method**
   bằng `@PreAuthorize`. Cấm tạo `AdminXxxController`, `CustomerXxxController`, `CaregiverXxxController`,
   `InternalXxxController`, `XxxManagementService`… Chi tiết ở §3 "Aggregate & versioning".

2. **Key `ErrorCode` chính là key message validation.** Mọi `@NotBlank(message = "KEY")` ánh xạ `ErrorCode.KEY`;
   validation mới cần `ErrorCode` mới. **`code` số phải duy nhất trong service**, và mỗi service dùng **dải mã
   riêng** để FE/gateway phân biệt: IdentityService `1000–1999`, CareRecipientService `2000–2999`, CareCatalogService
   `3000–3999`, ProviderService `4000–4999`, BookingService `5000–5999`, PaymentService `6000–6999`,
   MediaService `7000–7999`, ApiGateway `9000–9099`
   (+ `401`, `403`, `404`, `9999` dùng chung ý nghĩa). Service mới chọn dải kế tiếp và ghi vào `<Service>/AGENTS.md`.

3. **Luôn trả `APIResponse<T>`** ở mọi method controller — không trả raw type, `Map`, hay `ResponseEntity`.
   *Ngoại lệ đã ghi nhận:* `JwksController.get` trả JWK Set nguyên dạng (giao thức JWKS yêu cầu
   `{"keys":[...]}` ở top-level). *Ngoại lệ thứ hai (PaymentService — INF-01):* IPN VNPay
   `GET /api/v1/payments/vnpay/ipn` trả **JSON thô theo chuẩn VNPay** `{"RspCode":"00","Message":"Confirm Success"}`
   (mã `00/01/02/04/97/99`, không dùng `ErrorCode`), vì VNPay chỉ đọc đúng định dạng này để biết merchant đã nhận kết
   quả; endpoint public (chữ ký HMAC VNPay thay cho JWT). Return URL `GET /api/v1/payments/vnpay/return` vẫn trả
   `APIResponse<T>`. `GlobalExceptionHandler` và security handler được dùng `ResponseEntity`/ghi
   response để đặt HTTP status. Không thêm ngoại lệ khác nếu chưa ghi vào đây kèm lý do.

4. **`@JsonInclude(NON_NULL)` trên `APIResponse` là cố ý** — không bỏ.

5. **JWT ký RS256**, private key chỉ ở IdentityService (`IDENTITY_JWT_PRIVATE_KEY`, file PEM ngoài git). Không
   đổi thuật toán, không dùng HS512/shared secret, không phát private key cho service khác.

6. **Xác minh token bằng Spring OAuth2 Resource Server** (`JwtDecoder` + validator + converter) ở mọi service —
   không viết custom JWT filter. Claim loại token là `token_type` (`access`/`refresh`); danh tính là
   **`sub` = userId UUID** (không phải email). Refresh token không bao giờ được chấp nhận làm access.

7. **Controller không dùng `@Operation` / Swagger `@RequestBody`** (chỉ `@Tag`, `@SecurityRequirements`). Không
   inline JSON example trong annotation ở controller.

8. **`@SecurityRequirements({})` bắt buộc trên mọi endpoint public** và endpoint đó phải được permit trong
   `SecurityConfig`. Marker Swagger không thay cho runtime authorization.

9. **Soft delete mặc định.** Entity kế thừa `BaseEntity` (`deleted_at`); đánh dấu `deleted_at` thay vì xóa vật lý.
   Mọi query/lookup lọc bản ghi đã xóa (`...AndDeletedAtIsNull`). Không cascade xóa sang dữ liệu lịch sử hay sang
   service khác. Bảng liên kết role (`user_roles`) được xóa dòng khi thu hồi nhưng phải có audit.

10. **Không Flyway; schema do SQL trong repo quản lý; `ddl-auto: validate`.** Mọi thay đổi entity/index/constraint
    phải cập nhật `schema/<service>.sql` + change script cho DB đã có (§5). Không runtime DDL, không
    `create`/`create-drop`/`update` trên DB có dữ liệu. Không tự chuyển sang Liquibase.

    **10a. Đổi enum phải sửa CHECK constraint trong SQL.** Cột NOT NULL mới trên bảng có dữ liệu cần backfill an
    toàn trong change script.

11. **Implementation nằm ở `service/Impl/`**, không ở `service/`. Giữ cặp `FooService` (interface) +
    `FooServiceImpl`.

12. **Constructor injection qua `@RequiredArgsConstructor` + `@FieldDefaults(makeFinal = true)`** cho service và
    controller; không field-level `@Autowired`; không viết thừa `private final`. Field đặt tên đầy đủ theo kiểu
    (`userRepository`, không `users`).

13. **Refresh token là JWT ký (`token_type="refresh"`), theo dõi bằng `jti` trên Redis** (`IdentityStateService`,
    key `careconnect:identity:rt:{jti}`). Rotation phải consume nguyên tử (Lua). Không log, không trả raw refresh
    token ngoài cookie HttpOnly (web) hoặc body của endpoint `/auth/mobile/*`.

14. **`password` và `phone_number` của `User` nullable** — luôn null-check (tài khoản provision chưa có password
    thì login trả `INVALID_CREDENTIALS`, không NPE).

15. **Blacklist access token và OTP nằm trên Redis**, không tạo bảng DB cho token. Redis key luôn có prefix
    `careconnect:<service>:` và được định nghĩa ở một chỗ.

16. **Thứ tự side effect DB ↔ Redis phải có chủ đích.** DB và Redis không commit nguyên tử chung qua
    `@Transactional`. Login chỉ thành công khi đã lưu refresh jti; Redis lỗi → 503, không bypass kiểm tra. Ghi
    rõ hành vi khi một bên lỗi (vd OTP đã consume mà DB lỗi → yêu cầu OTP mới).

17. **Write có `@Transactional` ở service boundary**, đọc thuần `@Transactional(readOnly = true)`. Không gọi
    method `@Transactional`/`@Async` qua self-invocation (proxy không áp dụng).

18. **MapStruct cho mọi chuyển đổi entity↔DTO trên toàn bộ CRUD — không tự dựng DTO/entity trong service.** Theo đúng
    §3 "Quy tắc MapStruct / tầng mapper" (config chung, annotation ignore, giữ/bỏ `@Mapping`, PUT/PATCH, trường nhạy cảm).
    Kể cả entity liên kết và audit. Dùng `@Mapping` cho field đổi tên/lồng nhau.

19. **Lỗi luôn qua `AppException` + `ErrorCode` — chặn input và nhánh lỗi ở mọi method service.** `@Valid` +
    `message = "ERROR_CODE_KEY"` ở boundary; trong `*Impl` chặn mọi lookup (`.orElseThrow`), uniqueness, trạng
    thái nghiệp vụ. Không ném raw exception ra controller, không tự dựng `APIResponse` lỗi, không nuốt exception.

20. **401/403 trả envelope sạch ở cả filter lẫn controller.** Lỗi trong security filter không tới
    `@ControllerAdvice` → dùng `AuthenticationEntryPoint` / `AccessDeniedHandler`, serialize `APIResponse` bằng
    `ObjectMapper` từ `ErrorCode` (không ghép chuỗi JSON tay). Token sai không bao giờ thành 500.

21. **Tác vụ tốn thời gian chạy nền** (gửi thông báo hàng loạt, thống kê nặng, xử lý file…) — trả job/handle ngay,
    xử lý background. Login/authorization **không** bao giờ thành async job.

22. **Controller mỏng — service trả `APIResponse<T>`; controller chỉ delegate.** Thân method controller là một
    `return xxxService.method(...)`; KHÔNG chứa business logic, parse chuỗi (kể cả `UUID.fromString`), lookup
    entity hay dựng DTO, kể cả đọc cookie (`CookieUtils.read`) — truyền `HttpServletRequest` xuống service.
    Người dùng hiện tại: `@CurrentUserId UUID actorId` (§3), không `UUID.fromString(authentication.getName())`.
    Không dùng cờ `boolean` để rẽ nhánh hành vi (vd `mobile`) — tách thành method riêng (`login` / `loginMobile`).
    Enum path/query variable bind bằng Spring `Converter` trong `config/`, không parse trong controller. Service
    tầng thấp (token, Redis, email, client) được trả primitive/DTO; service đứng sau controller mới dựng envelope.

23. **Hằng dùng chung có một nguồn duy nhất** — role code (`RoleCode`), permission code (`Permissions`), tên
    cookie (`CookieUtils`), prefix Redis, tên claim JWT (`token_type`, `roles`, `permissions`, `center_id`,
    `authz_version`) và giá trị `"access"`/`"refresh"`. Không lặp magic string ở nhiều class.

24. **Không I/O mạng trong DB transaction đang mở.** Gửi email, gọi service khác, gọi API ngoài chạy ngoài
    transaction; side effect chỉ khi thành công thì dùng `TransactionSynchronization.afterCommit()`. Cleanup
    best-effort log lỗi, không làm hỏng luồng chính. Event liên service bắt buộc tin cậy → cần outbox (khi có).

25. **Background worker** (khi service sau cần): service tạo job + dispatch ở `afterCommit`; worker là **bean
    riêng** (`service/` + `Impl/`, `@Async` trên executor có tên) với `process(UUID)`; gọi mạng ngoài transaction,
    mỗi lần ghi DB là transaction ngắn (`TransactionTemplate`); map mọi lỗi sang `ErrorCode`, lỗi một item không
    làm sập scheduler. Retry chỉ cho lỗi tạm thời, có giới hạn — không retry sai credentials/OTP/quyền.

26. **Secret không bao giờ ra log hay response**: password/hash, OTP, refresh/access token, private key, header
    `Authorization`/`Cookie`, Brevo key. Hạn chế log dữ liệu cá nhân (email đầy đủ, hồ sơ bệnh nhân, giấy tờ).
    Không dùng `@Data` (`toString`) trên class chứa secret.

27. **Ranh giới microservice** (§2): không cross-DB join/FK/shared entity; tham chiếu bằng UUID; gọi service khác
    qua `client/` với URL + timeout cấu hình bằng env (không hardcode host/port). Service-to-service có danh tính
    và least privilege riêng — không dùng tài khoản ADMIN làm machine credential.

28. **Phân quyền = role + permission + phạm vi tài nguyên.** `hasRole('ADMIN')` ↔ `ROLE_ADMIN`;
    `hasAuthority('user:manage')` ↔ mã permission. Có permission vẫn phải đúng phạm vi: CUSTOMER chỉ dữ liệu của
    mình; CAREGIVER chỉ ca được giao; CENTER_MANAGER chỉ caregiver/booking **cùng `center_id`** và không bao giờ
    thao tác ADMIN/manager khác; ADMIN không tự động kế thừa mọi quyền nghiệp vụ của role khác. Không self-elevation,
    manager không gán ADMIN. Bảo vệ **ADMIN hoạt động cuối cùng** (`LAST_ADMIN` 1104). Kiểm phạm vi ở **service sở
    hữu tài nguyên**, không đẩy hết về IdentityService.

29. **Mọi thay đổi role/permission/status có audit** (`identity_audit`) và tăng `authz_version` khi làm giảm quyền.
    Không tuyên bố quyền bị thu hồi ngay trên service khác khi chưa có cơ chế đảm bảo (§4).

30. **Kiểm thử phù hợp thay đổi.** Auth/security cần test: thiếu/sai/hết hạn token, refresh dùng làm access, sai
    issuer/audience, thiếu role, tài khoản khóa, refresh reuse/đồng thời, OTP reuse, truy cập user/trung tâm của
    người khác. Test dùng DB/Redis cô lập, mock email/HTTP ngoài; không gửi email thật, không dùng secret thật.
    Context-load test không chứng minh authorization đúng. Báo rõ đã chạy gì, kết quả, giới hạn.

31. **Cập nhật tài liệu cùng thay đổi.** Hoàn thành + kiểm chứng một hạng mục → đánh `[x]` + ngày trong
    `CareConnect/docs/PLAN.md` (độc lập với `../docs/PLAN.md` của AIMA). Đổi contract/schema/quy ước → sửa
    `<Service>/README.md`, `schema/*.sql` và file này cùng lúc. Thiếu business rule (tỷ lệ cọc/hoàn tiền, TTL,
    permission catalog…) → **hỏi**, không tự đặt số.

32. **Repository luôn có `@Repository`**, `@Query` dùng `@Param` tường minh, không literal nghiệp vụ trong JPQL,
    lookup lọc `deletedAt` ở query (§3 "Repository").

---

## 8. Refactor IdentityService theo quy ước (2026-10-01)

Code IdentityService ban đầu do một AI khác viết, lệch cách code ở `../backend`. Đã refactor toàn bộ `src/main`
theo §3/§7, **giữ nguyên API contract** (path, request/response field, mã lỗi, cookie, claim JWT). Kiểm chứng:
45/45 test pass trên H2 và trên PostgreSQL 17 + Redis 7 thật (container test riêng), `mvnw package` exit 0.

Đã sửa:
- **Style:** bỏ import wildcard; mỗi annotation một dòng; bỏ method/`if` một dòng và nhiều statement trên một dòng;
  sửa thụt lề `AppException`; bỏ `var` và tên class viết đầy đủ trong code.
- **Lombok/field:** bỏ `private final` thừa; field đổi sang tên đầy đủ (`userRepository`, `accountService`…);
  thêm `@Slf4j` + log sự kiện nghiệp vụ; `JwtServiceImpl`, `EmailServiceImpl` dùng `@RequiredArgsConstructor`
  (RestClient Brevo chuyển thành bean `BrevoConfig.brevoRestClient`, decoder dùng chung thành bean
  `JwtConfig.identityJwtDecoder`).
- **Controller mỏng:** `@CurrentUserId` thay cho `UUID.fromString(authentication.getName())`; đọc cookie refresh
  chuyển xuống service; `AuthenticationService` tách `login/refresh/logout` (web) và `loginMobile/refreshMobile/
  logoutMobile` thay cho cờ `boolean mobile`; thêm `@Tag`, `@Operation(description)` và `SwaggerExamples`.
- **Response:** `/auth/csrf` trả `CsrfResponse` (field `token`, `headerName` như cũ); `AccountService.Subject`
  (record lồng) → `dto/response/AuthenticatedUser`; xóa `APIResponse.error(...)`.
- **Error handling:** `SecurityErrorHandler` (entry point + access denied + failure handler) serialize `APIResponse`
  bằng `ObjectMapper` thay cho ghép chuỗi JSON; 404 dùng `ErrorCode.PATH_NOT_FOUND` (code 404 như cũ).
- **MapStruct:** `UserRole`/`RolePermission` tạo qua `UserMapper.toUserRole` / `RoleMapper.toRolePermission`;
  `AuthenticationResponse`/`OtpResponse`/`CsrfResponse` qua mapper (`AuthenticationMapper`, `RegistrationMapper`);
  9 `@Mapping(ignore)` lặp gom vào `@IgnoreServerManagedUserFields`; `RoleMapper` thêm `toResponseList`,
  `toPermissionResponse(List)`.
- **Magic string:** `TokenType` (access/refresh), `TokenClaims` (tên claim, `ROLE_`, `Bearer `), `AuditAction`,
  `RedisKeys` (prefix + field OTP), `Permissions.ALL`, `EmailNormalizer`, `StrongPasswordValidator.BCRYPT_MAX_BYTES`.
- **Repository:** thêm `@Repository` + `@Param`; bỏ literal `'ADMIN'`/`'CAREGIVER'` trong JPQL
  (`countActiveByRoleCode`, `findByCenterAndRole`, `RoleRepository.findForUpdate(roleCode)`); thêm
  `...AndDeletedAtIsNull` thay cho `findAll()/findById()` rồi lọc trong Java.
- **Service:** helper đổi tên rõ nghĩa (`findActiveActor`, `lockTargetUser`, `checkManageScope`, `checkNotLastAdmin`,
  `assignRole`, `revokeSessions`, `writeAudit`…); `RoleService.permissions` → `listPermissions`;
  `CurrentIdentityService.get` → `getActiveUser`; `AccountService.credentials/invalidate` → `verifyCredentials/
  invalidateSessions`.
- **ErrorCode:** xóa hằng kế thừa AIMA không dùng: `USER_EXISTED`, `USERNAME_INVALID`, `USERNAME_REQUIRED`,
  `INVALID_PASSWORD`, `DATE_OF_BIRTH_REQUIRED`, `INVALID_DATE_OF_BIRTH`, `DOB_REQUIRED`, `INVALID_DOB`,
  `ACCOUNT_ALREADY_PENDING_DELETE`, `ACCOUNT_NOT_PENDING_DELETE`, `PASSWORD_CHANGE_LIMIT`,
  `PROFILE_ALREADY_COMPLETED`, `INVALID_AVATAR_URL`, `USER_LIST_EMPTY`.

Thay đổi hành vi nhỏ (có chủ đích): message thành công đổi từ `"Success"` sang tiếng Việt; `/auth/mobile/logout`
không còn gửi header `Set-Cookie` xóa cookie (mobile không dùng cookie).

Còn lại (chưa làm, chờ quyết định):
- `ErrorCode` chưa dùng nhưng có thể cần sau: `USER_NOT_FOUND`, `PHONE_REQUIRED`, `DELETE_ADMIN_INVALID`,
  `DELETE_OTHER_ADMIN_INVALID`, `EMAIL_NOT_FOUND`, `OTP_EXPIRED`, `TOKEN_NOT_FOUND`, `PASSWORDS_NOT_MATCH`,
  `CONFIRM_PASSWORD_REQUIRED`, `OTP_NOT_VERIFIED`, `REGISTER_OTP_INCORRECT`.
- Code test (`src/test`) mới chỉ sửa cho khớp API mới, chưa restyle.
