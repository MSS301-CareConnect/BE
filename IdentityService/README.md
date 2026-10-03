# CareConnect IdentityService

Java 21 / Spring Boot 4.1.1. Port mặc định **8081**. Service sở hữu tài khoản, RBAC, authentication và audit; không sở hữu dữ liệu trung tâm, hồ sơ chuyên môn, booking hoặc care report.

## Chức năng đã có

- Đăng ký CUSTOMER bằng OTP email Brevo; chỉ tạo tài khoản sau xác thực. OTP hash, TTL, cooldown, giới hạn thử và claim một lần trên Redis.
- Login web bằng HttpOnly cookies; login mobile bằng bearer/access + refresh trong body. CSRF bắt buộc cho web, kể cả refresh/logout; request bearer thuần không dùng cookie được miễn CSRF.
- JWT RS256, `sub=userId`, `iss`, `aud`, `jti`, `token_type`, `authz_version`; access có `roles`, `permissions`, `center_id` khi có. JWKS chỉ công bố public key.
- `aud` của **access token** = `["careconnect-identity", "careconnect-api"]`; refresh token chỉ có `careconnect-identity`. IdentityService kiểm `careconnect-identity`; các service backend khác và ApiGateway kiểm `careconnect-api` (audience chung, hằng `TokenClaims.API_AUDIENCE`). Access token phát trước khi thêm `careconnect-api` vẫn hợp lệ tại IdentityService tới khi hết hạn; gateway/service khác trả 401 với token đó → client refresh.
- API nội bộ `/internal/users/**` cho service khác, xác thực bằng header `X-Internal-Secret` (chain bảo mật riêng, không JWT/cookie/CSRF).
- Refresh JWT lưu `jti` trên Redis, rotation nguyên tử; logout thu hồi refresh và blacklist access được gửi. Logout all/đổi mật khẩu/reset/khóa/xóa mềm tăng `authz_version`.
- Mỗi request tại IdentityService kiểm account/status/version và nạp quyền hiện hành từ DB; token không giữ được quyền đã thu hồi tại service này.
- ADMIN tạo CUSTOMER/manager; manager tạo và quản lý caregiver trong trung tâm của mình. ADMIN xem toàn bộ tài khoản, manager chỉ danh sách caregiver cùng trung tâm; CUSTOMER/CAREGIVER chỉ hồ sơ cá nhân.
- Gán/thu hồi role, tạo role bổ sung và cấu hình permissions của role bổ sung. Bốn role nền tảng có responsibilities/grants cố định trong bản này; không đổi permissions của built-in roles qua API. Permission catalog hiện chỉ gồm các thao tác IdentityService đã triển khai.
- Audit quản trị, bảo vệ ADMIN cuối cùng, soft delete; schema SQL thủ công, không Flyway.

## Dependency bổ sung

`spring-boot-starter-security-oauth2-resource-server` (Spring Security JWT/JOSE), `spring-boot-starter-data-redis`, `spring-boot-starter-actuator`, MapStruct 1.6.3 + processor + `lombok-mapstruct-binding` 0.2.0; H2 chỉ cho tests. Giữ JPA, MVC, Security, Validation, PostgreSQL, Lombok, springdoc và OpenFeign có sẵn. Brevo sử dụng Spring `RestClient` với connect/read timeout, không cần SDK/mail starter. Không thêm Flyway, jjwt hoặc WebFlux cho service MVC này.

## Chạy local

Trong `CareConnect/IdentityService`, với Java 21 trên PATH, Windows PowerShell 5.1 hoặc PowerShell 7 và Docker:

```powershell
Copy-Item .env.example .env
# Sửa .env: DB password, Brevo API key và sender đã được Brevo xác minh.
.\scripts\New-DevKeys.ps1
docker compose up -d
Get-Content -Raw schema/identity.sql | docker compose exec -T postgres psql -U careconnect_identity -d CARECONNECT_IDENTITY -v ON_ERROR_STOP=1
.\scripts\Start-Dev.ps1
```

- SQL trên chỉ áp dụng **một lần cho DB mới**. Nếu đã có tables, tạo change script được review; không dùng script khởi tạo để nâng cấp DB. Hibernate chỉ `validate`, `spring.sql.init.mode=never` ở application thông thường.
- Lệnh psql dùng username trong mẫu; điều chỉnh nếu thay `IDENTITY_DB_USERNAME`. Compose dùng PostgreSQL `5441` và Redis `6381` trên loopback theo `.env.example`, tránh dùng DB của dự án khác.
- Tên DB theo lựa chọn của người dùng là `CARECONNECT_IDENTITY`: đặt `IDENTITY_DB_NAME` và tên database cuối `IDENTITY_DB_URL` giống nhau. Compose chỉ tạo database theo `POSTGRES_DB` khi volume còn mới; đổi env không đổi tên/tự tạo thêm database trên volume đã khởi tạo. Nếu dùng PostgreSQL có sẵn, dùng host/port/credentials của DB đó, không khởi chạy container PostgreSQL riêng rồi trông chờ có dữ liệu DB cũ.
- `Start-Dev.ps1` đọc `.env` thành process environment, không chạy nội dung như code. Nếu chạy qua IDE hoặc `mvnw.cmd spring-boot:run` trực tiếp, phải tự cung cấp env; Spring Boot không tự đọc `.env`.
- `.env.example` là mẫu các biến cần cấu hình trong `.env`; thay placeholders bằng giá trị của môi trường đang chạy. Cấu hình Spring chỉ nằm trong `src/main/resources/application.yaml`; bật/tắt Swagger qua `IDENTITY_SWAGGER_ENABLED`, cookie Secure qua `IDENTITY_COOKIE_SECURE`.
- `ddl-auto: validate` kiểm tra bảng/cột/kiểu dữ liệu theo mapping khi khởi động và báo lỗi nếu không khớp, không tự sửa schema. Do đã chọn SQL quản lý trong repo, hãy áp dụng `schema/identity.sql` cho DB mới và change scripts khi đổi schema. `update` tự thay đổi schema dựa trên entity nên không dùng trong cơ chế này; `validate` cũng không thay thế việc kiểm tra các index/check constraints trong SQL.
- `keys/` và `.env` được gitignore. Script tạo keys không ghi đè khóa cũ. Keys trong repo này là PKCS8 private / X509 public PEM.
- `New-DevKeys.ps1` dùng Java chạy `scripts/GenerateDevKeys.java` để tạo RSA PEM, nên chạy được cả Windows PowerShell 5.1. Không cần biên dịch helper bằng Maven hoặc cài PowerShell 7 để tạo khóa.
- Các TTL trong `.env.example` là ví dụ dev: access 15 phút, refresh 7 ngày, OTP 5 phút, cooldown 60 giây, 5 lần thử. Ứng dụng yêu cầu cấu hình tường minh; đây không phải policy nghiệp vụ cố định.
- JVM và audit dùng UTC/Instant. FE hiển thị thời điểm theo timezone của người dùng.

### Tài khoản ADMIN đầu tiên

Trên DB mới, đặt trong `.env`:

```dotenv
IDENTITY_BOOTSTRAP_ENABLED=true
IDENTITY_BOOTSTRAP_EMAIL=<email-admin>
IDENTITY_BOOTSTRAP_PASSWORD=<mật-khẩu-mạnh>
```

Khởi động một lần rồi đặt `IDENTITY_BOOTSTRAP_ENABLED=false` và xóa bootstrap password khỏi cấu hình. Bootstrap chỉ tạo account mới khi chưa có ADMIN hoạt động; không tự nâng quyền một email có sẵn. Không có tài khoản/mật khẩu mặc định được seed.

### URL

- API: `http://localhost:8081`
- Swagger: `http://localhost:8081/swagger-ui.html`
- OpenAPI: `http://localhost:8081/v3/api-docs`

Test trên Swagger (nút **Authorize** có hai ô `bearerAuth` và `csrf`):

- **Bearer (khuyên dùng):** `POST /auth/mobile/login` → dán `result.accessToken` vào `bearerAuth`. Không cần CSRF.
- **Web cookie / đăng ký / quên mật khẩu:** `GET /auth/csrf` → dán `result.token` vào `csrf`, rồi gọi `/users/register`, `/auth/login`… Sau `/auth/login` hoặc `/auth/refresh` thay ô `csrf` bằng `result.csrfToken` mới. Sau login web, trình duyệt tự gửi cookie `access_token` cho Swagger.
- Đừng trộn hai cách: cookie `access_token` và bearer khác nhau → 401. Gọi `/auth/logout` hoặc xóa cookie `localhost:8081` trước khi đổi cách.
- Thiếu/sai CSRF → 403 code `1111` (`CSRF_INVALID`); 403 code `403` là thiếu quyền thật.
- JWKS: `http://localhost:8081/.well-known/jwks.json`
- Health: `http://localhost:8081/actuator/health` (không lộ chi tiết dependency)

## API contract

API nghiệp vụ trả `APIResponse<T>` `{code,message,result}`; errors giữ HTTP status tương ứng. JWKS/OpenAPI/health giữ format giao thức. Request không nhận field thừa để tránh role/status injection.

| Method / path | Hành vi / quyền |
|---|---|
| `GET /auth/csrf` | Public; trả token/headerName, đặt cookie CSRF |
| `POST /users/register` | Public + CSRF (*); `email,password,fullName,phoneNumber?`; gửi OTP |
| `POST /users/register/resend-otp` | Public + CSRF (*); `email`; cần pending registration còn hạn |
| `POST /users/register/verify` | Public + CSRF (*); `email,otpCode`; tạo CUSTOMER, chưa tự login |
| `POST /users/forgot-password` | Public + CSRF (*); `email`; trả message trung tính |
| `POST /users/reset-password` | Public + CSRF (*); `email,otpCode,newPassword`; OTP được consume trực tiếp trong thao tác reset |
| `POST /auth/login` | Public + CSRF; `email,password`; HttpOnly access/refresh cookies, không trả token trong body |
| `POST /auth/refresh` | Public + CSRF; refresh cookie, rotate cả hai token |
| `POST /auth/logout` | Public + CSRF; refresh/access cookies, clear cookies; token đã hết hạn vẫn logout được |
| `POST /auth/mobile/login` | Public JSON; `email,password`; trả access/refresh, không đặt auth cookies |
| `POST /auth/mobile/refresh` | Public JSON; `refreshToken`; rotate |
| `POST /auth/mobile/logout` | Public JSON; `refreshToken`; gửi access trong bearer header để blacklist |
| `POST /auth/logout-all` | Authenticated; tăng version, clear cookies |
| `GET /users/me`, `PUT /users/me` | Authenticated; chỉ đọc/sửa tài khoản mình; update `fullName?,phoneNumber?` |
| `POST /users/me/change-password` | Authenticated; `currentPassword,newPassword`; thu hồi phiên cũ |
| `GET /users?page=0&size=20` | ADMIN: toàn hệ thống; manager: caregiver cùng trung tâm; size 1–100 |
| `GET /users/{id}` | Self hoặc quyền đọc + phạm vi hợp lệ |
| `POST /users` | ADMIN: CUSTOMER/CENTER_MANAGER; manager: CAREGIVER cùng trung tâm |
| `PUT /users/{id}` | Quản trị basic profile theo quyền + phạm vi |
| `PATCH /users/{id}/status?status=ACTIVE\|LOCKED` | Quản trị trạng thái theo quyền + phạm vi |
| `DELETE /users/{id}` | Xóa mềm theo quyền + phạm vi; không tự xóa bản thân |
| `PUT /users/{id}/roles/{roleId}`, `DELETE ...` | ADMIN + `role:manage`; gán/thu hồi, không xóa ADMIN cuối cùng |
| `GET /roles`, `POST /roles` | ADMIN + `role:manage`; list/create role bổ sung |
| `PUT /roles/{id}/permissions` | ADMIN + `role:manage`; `permissionIds[]`; thay grants của role bổ sung |
| `GET /permissions` | ADMIN + `role:manage`; danh mục supported permissions |

### API nội bộ (service-to-service)

Chỉ gọi trực tiếp giữa các service; **ApiGateway chặn `/internal/**`**. Header bắt buộc `X-Internal-Secret: <IDENTITY_INTERNAL_SECRET>` (so sánh constant-time). Secret trống → mọi request `/internal/**` bị 401 (fail-closed). Không nhận bearer token người dùng; không cần CSRF.

| Method / path | Hành vi |
|---|---|
| `GET /internal/users/{userId}` | `{id,email,fullName,phoneNumber,status,roles}`; user đã xóa mềm / không có → 404 `1016`. User `LOCKED` vẫn trả về kèm `status`. |
| `GET /internal/users/by-email?email=` | Email được trim + lowercase; thiếu → 400 `1011`; không có → 404 `1016`. |
| `POST /internal/users/batch` | Body `{"userIds":[...]}` (1–100, lỗi `1112`/`1113`); trả các user tìm thấy, bỏ qua id không có / đã xóa mềm, id trùng chỉ trả một lần. |

Response không có permissions, centerId, password hay dữ liệu phiên. Sai/thiếu secret → 401 `401`.

Provision request: `email,fullName,phoneNumber?,roleCode,centerId?`. Manager tạo caregiver thì service lấy `centerId` từ manager; centerId khác bị từ chối. ADMIN tạo manager cần `centerId`. Tài khoản provision chưa có password; người nhận dùng forgot/reset password với OTP để tự đặt password. Trong bản này IdentityService chưa gọi CenterService để xác minh trung tâm tồn tại; ADMIN phải chọn UUID hợp lệ từ domain trung tâm khi tích hợp UI/service đó.

Web gọi `GET /auth/csrf` với credentials, lưu token trả về trong bộ nhớ và gửi header `X-XSRF-TOKEN` ở các request ghi, đồng thời gửi cookies. Sau login/refresh, cập nhật token CSRF trong bộ nhớ bằng `result.csrfToken` mới trả về; CSRF không đổi khi chỉ đọc API. Logout xóa cả cookie CSRF, lấy lại `/auth/csrf` trước đăng nhập mới. Token CSRF không phải token đăng nhập. Mobile lưu refresh trong OS secure storage. (*) Các endpoint đăng ký / OTP / quên-đặt lại mật khẩu dùng chung cho web và mobile: request **không mang cookie nào** (mobile) được miễn CSRF — các endpoint này không dựa vào phiên đăng nhập và không đặt cookie nên không có gì để tấn công CSRF; trình duyệt đang có cookie vẫn phải gửi `X-XSRF-TOKEN` như cũ (2026-10-03). Login/refresh/logout web luôn cần CSRF; mobile dùng `/auth/mobile/*`.

Profile update bỏ qua field null; không có semantics xóa phone bằng null. Password tối thiểu 8 ký tự, gồm hoa/thường/số/ký tự đặc biệt và tối đa **72 byte UTF-8** do BCrypt. Email normalize lowercase/trim và unique; email đã soft delete vẫn được giữ, chưa có quy trình tái sử dụng hoặc hard purge.

## Tích hợp các service sau

- Đi qua **ApiGateway** (`../ApiGateway`, port 8080): mọi path ở bảng API contract có prefix `/api/v1` (gateway cắt prefix); `/internal/**` và JWKS không mở qua gateway. Cookie `Path=/` dùng được qua gateway, không cần rewrite.

- **Access TTL nên giữ ngắn (10–15 phút).** Service khác (CareRecipient, gateway…) xác minh JWT offline và **không** nạp lại quyền/trạng thái theo thời gian thực như IdentityService: tài khoản bị khóa / thu hồi role vẫn gọi được service khác tới khi access token hết hạn.
- Khi đứng sau ApiGateway, đặt `IDENTITY_OPENAPI_SERVER_URL=/api/v1` để Swagger có server "Qua API gateway"; bỏ trống khi chạy độc lập.

- Service khác dùng Resource Server RS256, kiểm issuer/audience/expiry/type và converter đọc `roles[]` → `ROLE_*`, `permissions[]` → mã permission. JWKS public, private key chỉ IdentityService giữ.
- Audience: IdentityService dùng `careconnect-identity`; mọi service backend khác kiểm `careconnect-api` (đã có trong access token). Không bỏ audience validation cho tiện.
- Authority JWT là snapshot; downstream chỉ xác minh offline **chưa** có cơ chế kiểm trạng thái/quyền hiện hành như IdentityService. Cần internal status/version contract hoặc policy TTL trước khi tuyên bố thu hồi ngay toàn hệ thống.
- Center UUID là external reference, không FK xuyên DB. Domain trung tâm quyết định vòng đời trung tâm; cần contract đồng bộ khi khóa/chuyển trung tâm. Bản này chưa có chức năng chuyển membership.
- Bản này dùng một key pair; chưa hỗ trợ chồng lấp nhiều public key trong quá trình rotation. Thay key hiện tại làm token cũ mất hiệu lực. Cần bổ sung key ring/overlap trước rotation không gián đoạn production.
- OTP pending và hash hết hạn cùng TTL: quá hạn cần đăng ký lại; resend dùng dữ liệu pending còn hạn. Verify/reset consume OTP trước DB write, nên lỗi DB sau consume cần yêu cầu OTP mới; không có transaction phân tán DB–Redis.
- Không có OAuth2/OIDC authorization-server/discovery/grant chuẩn, Google login, account erasure hoặc grace period trong phạm vi này.

## Kiểm thử

Đã kiểm chứng ngày 2026-09-30: **44 tests thành công** với PostgreSQL/Redis thật và Maven package. JAR chạy tại port **8081** đã qua kiểm tra health, OpenAPI, JWKS, đăng nhập web/mobile, CSRF, refresh/logout và lỗi Redis trả `503`. Môi trường test riêng được dọn sau kiểm chứng; chưa gửi email qua tài khoản Brevo thật.

Sau refactor theo `../AGENTS.md` (2026-10-01): **45 tests thành công** trên H2 và trên PostgreSQL 17 + Redis 7 thật (container test riêng, đã dừng), `mvnw package` thành công; API contract không đổi. Chưa chạy lại JAR với stack dev.

Bổ sung API nội bộ `/internal/users/**` + audience `careconnect-api` (2026-10-03): **56/56 tests** trên H2 và trên PostgreSQL 17 + Redis 7 thật (container test riêng); `mvnw package` thành công; JAR chạy sau ApiGateway trong e2e (login web/mobile, CSRF, refresh, logout, internal API) — xem `../docs/2026-10-03-PLAN_CARE_RECIPIENT_GATEWAY.md` §7.

Sau khi miễn CSRF cho request không cookie ở đăng ký / quên mật khẩu (mobile, 2026-10-03): **58/58 tests** trên H2. E2E toàn hệ thống: `..\scripts\e2e\Run-E2E.ps1`.

Tests mặc định dùng H2 + state test trong bộ nhớ, email mock. Không cần env/keys/DB thật:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Để kiểm chính schema PostgreSQL, constraint/concurrency và Lua Redis, tạo **containers test riêng** (không có volume data thật):

```powershell
docker run --rm -d --name careconnect-identity-verify-pg -e POSTGRES_PASSWORD=identity_test_only -p 127.0.0.1:55494:5432 postgres:17
docker run --rm -d --name careconnect-identity-verify-redis -p 127.0.0.1:56394:6379 redis:7-alpine
.\mvnw.cmd test '-Didentity.testing.postgres=true' '-Didentity.testing.real-state=true'
docker stop careconnect-identity-verify-pg careconnect-identity-verify-redis
```

Đợi PostgreSQL sẵn sàng trước chạy test. Suite tạo schema có UUID riêng rồi dọn schema đó; không chạy flags này trỏ vào DB production. Tests bao gồm auth/token claims, web cookie/CSRF, role và center isolation, revocation/soft delete, OTP/refresh replay và concurrent consumption, unique email, password reset, bootstrap guard/admin cuối cùng và Brevo adapter bằng HTTP server local. Brevo account thật chưa được kiểm chứng nếu chưa có key/sender hợp lệ.

Khi triển khai production đặt `IDENTITY_SWAGGER_ENABLED=false`, `IDENTITY_COOKIE_SECURE=true` và dùng HTTPS; secret/key từ cấu hình triển khai và Redis có persistence phù hợp cho refresh/blacklist. Không cần file application theo profile riêng. Tạo schema/change script theo runbook; không bật tự động create/update database.
