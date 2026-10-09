# CareConnect ApiGateway

Java 21 / Spring Boot 4.1.1 / **Spring Cloud Gateway Server MVC** 5.0.3 (servlet, không WebFlux). Port mặc định **8080**.
Điểm vào duy nhất cho FE/mobile: định tuyến `/api/v1/**`, kiểm JWT sớm, CORS tập trung, correlation id, chặn API nội bộ.
Không có DB/Redis. Quy ước: `../AGENTS.md`; phần riêng: `AGENTS.md`.

## Định tuyến

Gateway **cắt prefix `/api/v1`** rồi chuyển nguyên phần còn lại (kể cả query string, body, method).

| Path ngoài (gateway) | Service | Path trong service |
|---|---|---|
| `/api/v1/auth/**` | IdentityService | `/auth/**` |
| `/api/v1/users/**` | IdentityService | `/users/**` |
| `/api/v1/roles/**`, `/api/v1/permissions/**` | IdentityService | `/roles/**`, `/permissions/**` |
| `/api/v1/care-recipients/**` | CareRecipientService | `/care-recipients/**` |
| `/api/v1/admin/care-recipients/**` | CareRecipientService | `/admin/care-recipients/**` |
| `/api/v1/caregiver/care-recipients/**` | CareRecipientService | `/caregiver/care-recipients/**` |
| `/api/v1/bookings/**` | BookingService | `/bookings/**` |
| `/api/v1/center/bookings/**` | BookingService | `/center/bookings/**` |
| `/api/v1/admin/bookings/**` | BookingService | `/admin/bookings/**` |
| `/api/v1/caregiver/occurrences/**` | BookingService | `/caregiver/occurrences/**` |
| `/api-docs/identity`, `/api-docs/care-recipient`, `/api-docs/booking` | từng service | `/v3/api-docs` (Swagger tổng hợp) |

- `/api/v1/caregiver/**` route **từng path cụ thể** (không route cả prefix): ProviderService cũng có `/caregiver/profile`,
  `/caregiver/schedules`… (chưa route qua gateway).

- **Không** có route tới `/internal/**` hay `/.well-known/jwks.json` (service gọi trực tiếp nhau).
- Mọi path có segment `internal` (dạng thô hoặc đã decode, không phân biệt hoa thường) → **404** ngay, trước xác thực.
  URL không chuẩn hóa (`..`, `%2e`, `;`…) bị firewall Spring Security từ chối → 400 `9000`.
- Path không thuộc route nào → 404 (`401` nếu chưa đăng nhập).

## Xác thực tại gateway

- Kiểm **sớm** access token (Bearer header hoặc cookie `access_token`): RS256 qua JWKS Identity (cache), `iss`,
  `aud` chứa `careconnect-api`, `token_type=access`, `exp/iat/jti`, `sub` UUID. Service phía sau **vẫn tự xác minh**;
  gateway không kiểm blacklist/trạng thái tài khoản (việc của Identity).
- Endpoint public (gateway không đọc token — cookie cũ không chặn được login/refresh/logout):
  `/api/v1/auth/{login,refresh,logout,csrf}`, `/api/v1/auth/mobile/{login,refresh,logout}`,
  `/api/v1/users/{register,register/verify,register/resend-otp,forgot-password,reset-password}`, Swagger
  (`/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`, `/api-docs/**`), `/actuator/health/**`.
- Header client gửi bị xóa trước khi chuyển tiếp: mọi `X-User-*`, `X-Internal-Secret`, `Origin`. Gateway gắn lại
  `X-User-Id` (= `sub`) và `X-User-Roles` (roles, phân tách dấu phẩy) từ token đã xác minh — **chỉ để log/tracing**,
  service không phân quyền theo header này. (Không có `X-User-Email`: JWT không chứa email.)
- Chuyển nguyên `Authorization`, `Cookie` (`access_token`, `refresh_token`, `XSRF-TOKEN`), `X-XSRF-TOKEN`, và
  `Set-Cookie` từ service. CSRF do service kiểm, gateway không kiểm.
- **Access token nên giữ ngắn (10–15 phút)**: gateway và service ngoài Identity xác minh offline, không nạp lại quyền theo
  thời gian thực.

## Cookie web

IdentityService đặt `access_token`, `refresh_token`, `XSRF-TOKEN` với `HttpOnly`, **`Path=/`**, không `Domain`,
`Secure`/`SameSite` theo env của Identity. Vì `Path=/`, cookie gắn với host của gateway và được gửi cho mọi `/api/v1/**`
→ **không cần rewrite cookie path** (có test khẳng định). Nếu Identity đổi Path khác `/` phải xem lại.

## CORS

Tập trung tại gateway: `GATEWAY_ALLOWED_ORIGINS` (danh sách, không wildcard), credentials bật, methods
GET/POST/PUT/PATCH/DELETE/OPTIONS, headers `Authorization`, `Content-Type`, `X-XSRF-TOKEN`, `X-Request-Id`; expose
`X-Request-Id`. Gateway xóa `Origin` khi chuyển tiếp nên CORS của service không thêm header trùng (CORS của
IdentityService giữ nguyên cho trường hợp gọi trực tiếp khi dev). Origin không cho phép → 403.

## Lỗi (envelope `APIResponse`)

| Tình huống | HTTP / code |
|---|---|
| Thiếu / sai / hết hạn token, sai audience, refresh token | 401 / `401` |
| Path nội bộ, path không có route | 404 / `404` |
| URL bị firewall từ chối | 400 / `9000` |
| Service phía sau không kết nối được | 503 / `9001` |
| Không lấy được JWKS của Identity | 503 / `9002` |
| Service phía sau quá `GATEWAY_READ_TIMEOUT` | 504 / `9003` |

Phản hồi lỗi nghiệp vụ của service được chuyển nguyên (mã của service đó: Identity 1xxx, CareRecipient 2xxx).

## Correlation id

`X-Request-Id` hợp lệ (`[A-Za-z0-9-]{8,64}`) từ client được giữ, không thì sinh UUID. Gắn vào MDC (log), header gửi
xuống service và response (một lần). Mỗi request log một dòng `METHOD path -> status (ms)` (không log query/header).

## Chạy local

```powershell
Copy-Item .env.example .env     # điền URL service, issuer/JWKS của Identity, origin FE
.\scripts\Start-Dev.ps1
```

Tùy chọn chạy trong container từ JAR (gọi service trên host qua `host.docker.internal`):
`.\mvnw.cmd package` rồi `docker compose up -d`.

| Biến | Ý nghĩa |
|---|---|
| `GATEWAY_SERVER_PORT`, `GATEWAY_SWAGGER_ENABLED` | Port, bật Swagger tổng hợp `/swagger-ui.html` |
| `GATEWAY_IDENTITY_URL`, `GATEWAY_CARE_RECIPIENT_URL`, `GATEWAY_BOOKING_URL` | Base URL service phía sau |
| `GATEWAY_CONNECT_TIMEOUT`, `GATEWAY_READ_TIMEOUT` | Timeout (Duration) khi chuyển tiếp |
| `GATEWAY_JWT_ISSUER`, `GATEWAY_JWT_JWKS_URI` | Phải khớp IdentityService |
| `GATEWAY_ALLOWED_ORIGINS` | Origin FE (CORS + credentials) |

Swagger tổng hợp: `http://localhost:8080/swagger-ui.html` (chọn `identity` / `care-recipient` / `booking`). Đặt
`IDENTITY_OPENAPI_SERVER_URL=/api/v1`, `CARE_RECIPIENT_OPENAPI_SERVER_URL=/api/v1`, `BOOKING_OPENAPI_SERVER_URL=/api/v1`
để "Try it out" gọi qua gateway.

FE (`FE-careconnect`): Vite proxy hiện cắt `/api` rồi gọi thẳng service. Khi dùng gateway: `API_PROXY_TARGET=http://localhost:8080`
và rewrite `/api` → `/api/v1` (hoặc đặt `VITE_API_BASE_URL=/api/v1` và bỏ rewrite) — thay đổi phía FE, chưa làm.

## Kiểm thử

`.\mvnw.cmd test` — **14/14** (2026-10-03): gateway thật (MockMvc) chuyển tiếp HTTP thật tới Identity/CareRecipient giả:
cắt prefix, token sai/thiếu/refresh/sai audience, endpoint public bỏ qua cookie cũ, cookie `Path=/` đi qua `/api/v1/**`
(login/ghi có CSRF/refresh), thay `X-User-*`, request id, chặn `/internal`, CORS + bỏ `Origin`, Swagger tổng hợp,
timeout 504, upstream tắt 503, JWKS không truy cập được 503. E2E toàn hệ thống: `..\scripts\e2e\Run-E2E.ps1`; kết quả: `../docs/2026-10-03-PLAN_CARE_RECIPIENT_GATEWAY.md` §7.

2026-10-09: **16/16** — thêm route BookingService (4 prefix + Idempotency-Key đi qua), `/caregiver/care-recipients`
tới CareRecipient, `/caregiver/schedules` không bị route nhầm, chặn `/internal/bookings/**`. Test 404 cũ đổi path ví dụ
từ `/api/v1/bookings` (nay đã có route) sang `/api/v1/unknown-resources`.

## Chưa làm

Rate limiting (chưa chốt); cache JWKS khi Identity tắt lâu hơn thời gian cache (token mới sẽ nhận 503).
