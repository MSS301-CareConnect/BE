# CareConnect CareRecipientService

Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.3. Port mặc định **8082**. Service sở hữu hồ sơ người bệnh /
người cần chăm sóc: thông tin sức khỏe, thuốc, liên hệ khẩn cấp và danh sách người thân cùng quản lý hồ sơ (OWNER /
EDITOR / VIEWER). Không sở hữu tài khoản (IdentityService), booking, caregiver hay trung tâm.

Quy ước chung: `../AGENTS.md`; phần riêng của service: `AGENTS.md`.

## Chức năng

- CUSTOMER tạo hồ sơ (kèm liên hệ khẩn cấp và thuốc tùy chọn) và tự động là **OWNER + quản lý chính**.
- Một customer quản lý nhiều hồ sơ; một hồ sơ có nhiều người thân với quyền khác nhau:
  - **OWNER**: toàn quyền, quản lý danh sách người thân, đổi trạng thái, lưu trữ hồ sơ.
  - **EDITOR**: xem + sửa hồ sơ, thuốc, liên hệ khẩn cấp.
  - **VIEWER**: chỉ xem.
- Không quản lý hồ sơ → **404** (không lộ hồ sơ tồn tại). Có quan hệ nhưng thiếu cấp → 403 `2101`.
- Đúng một quản lý chính, luôn là OWNER; chuyển quản lý chính trong một transaction. Không gỡ / hạ quyền quản lý chính
  (`2405`, phải chuyển trước) hay OWNER cuối cùng (`2403`). Ai cũng tự rời được hồ sơ (trừ hai trường hợp trên).
- Thêm người thân bằng **email**: gọi IdentityService (Feign) — tài khoản phải tồn tại, `ACTIVE`, có role `CUSTOMER`;
  mọi trường hợp không thỏa trả cùng mã `2401` (không dò được email).
- Danh sách người thân kèm họ tên / email / SĐT lấy từ Identity bằng **một lời gọi batch**. Identity lỗi / timeout /
  circuit mở → vẫn trả danh sách, `profileAvailable=false`, chỉ thiếu profile.
- Optimistic locking (`version`) khi sửa hồ sơ; `updated_by` trên hồ sơ và thuốc. Soft delete hồ sơ / thuốc / liên hệ.
- ADMIN xem hồ sơ toàn hệ thống (kể cả đã lưu trữ) để xử lý khiếu nại.
- API nội bộ cho Booking/Caregiver (snapshot, kiểm quyền, batch) — không mở qua gateway.

## Chạy local

Trong `CareConnect/CareRecipientService`, Java 21, Docker, IdentityService đang chạy (JWKS + API nội bộ):

```powershell
Copy-Item .env.example .env
# Sửa .env: DB password, CARE_RECIPIENT_IDENTITY_INTERNAL_SECRET (= IDENTITY_INTERNAL_SECRET của Identity),
# CARE_RECIPIENT_INTERNAL_SECRET (chuỗi ngẫu nhiên), CARE_RECIPIENT_JWT_ISSUER (= IDENTITY_JWT_ISSUER).
docker compose up -d
Get-Content -Raw schema/care_recipient.sql | docker compose exec -T postgres psql -U careconnect_care_recipient -d CARECONNECT_CARE_RECIPIENT -v ON_ERROR_STOP=1   # chỉ DB mới
.\scripts\Start-Dev.ps1
```

- PostgreSQL riêng `127.0.0.1:5442` (DB `CARECONNECT_CARE_RECIPIENT`). Không Redis, không Flyway; `ddl-auto: validate`.
- Schema chỉ áp dụng một lần cho DB mới; đổi schema → change script ở `schema/changes/` + cập nhật
  `schema/care_recipient.sql`.
- JVM chạy UTC; cột thời gian `timestamptz`.
- `spring.threads.virtual.enabled=true`.

URL (dev): API `http://localhost:8082` · Swagger `/swagger-ui.html` · OpenAPI `/v3/api-docs` · Health `/actuator/health`.
Đi qua gateway: `http://localhost:8080/api/v1/...` (khuyên dùng cho FE).

### Biến môi trường

| Nhóm | Biến |
|---|---|
| App | `CARE_RECIPIENT_SERVER_PORT`, `CARE_RECIPIENT_SWAGGER_ENABLED`, `CARE_RECIPIENT_OPENAPI_SERVER_URL` (= `/api/v1` khi sau gateway) |
| DB | `CARE_RECIPIENT_DB_NAME`, `CARE_RECIPIENT_DB_URL`, `CARE_RECIPIENT_DB_PORT`, `CARE_RECIPIENT_DB_USERNAME`, `CARE_RECIPIENT_DB_PASSWORD` |
| JWT | `CARE_RECIPIENT_JWT_ISSUER`, `CARE_RECIPIENT_JWT_JWKS_URI` (audience cố định `careconnect-api`) |
| Feign → Identity | `CARE_RECIPIENT_IDENTITY_URL`, `CARE_RECIPIENT_IDENTITY_INTERNAL_SECRET`, `CARE_RECIPIENT_IDENTITY_CONNECT_TIMEOUT_MS`, `CARE_RECIPIENT_IDENTITY_READ_TIMEOUT_MS` |
| API nội bộ của service này | `CARE_RECIPIENT_INTERNAL_SECRET` (trống = khóa `/internal/**`) |

## Xác thực

- Service **tự xác minh JWT** (Spring OAuth2 Resource Server): RS256 qua JWKS của Identity (cache), `iss`, `aud` chứa
  **`careconnect-api`**, `token_type=access`, `exp/iat/jti`, `sub` UUID. Refresh token và token chỉ có audience
  `careconnect-identity` bị từ chối. Header `X-User-*` do gateway gắn **không** được dùng để phân quyền.
- Mobile/Swagger: `Authorization: Bearer <access>`. Web (qua gateway): cookie `access_token` do Identity đặt; request
  ghi bằng cookie phải gửi `X-XSRF-TOKEN` khớp cookie `XSRF-TOKEN` (token lấy từ `GET /api/v1/auth/csrf`). Service
  chỉ đọc cookie, không bao giờ đặt cookie. Thiếu/sai CSRF → 403 `2002`.
- **Access token nên giữ ngắn (10–15 phút)**: service này xác minh offline, không nạp lại quyền/trạng thái tài khoản theo
  thời gian thực như IdentityService — tài khoản bị khóa vẫn gọi được tới khi access token hết hạn.
- CORS tắt ở service (gateway xử lý CORS tập trung và bỏ header `Origin` khi chuyển tiếp).

## API (path trong service; qua gateway thêm prefix `/api/v1`)

Mọi response là `APIResponse` `{code,message,result}`. Danh sách phân trang trả
`{items,page,size,totalElements,totalPages}` (`size` 1–100, lỗi `2003`).

### Customer (`hasRole('CUSTOMER')`)

| Method / path | Quyền tối thiểu | Ghi chú |
|---|---|---|
| `POST /care-recipients` | — | Body hồ sơ + `relationshipType` (bắt buộc) + `emergencyContacts[]` (≤10, tối đa 1 primary) + `medications[]` (≤50) |
| `GET /care-recipients?status=&keyword=&page=&size=` | — | Hồ sơ mình quản lý, kèm `myAccessLevel`, `primaryManager` |
| `GET /care-recipients/{id}` | VIEWER | Hồ sơ + thuốc + liên hệ khẩn cấp + `myAccessLevel` |
| `PUT /care-recipients/{id}` | EDITOR | Thay toàn bộ thông tin (null = xóa giá trị), bắt buộc `version`; lệch → 409 `2102` |
| `PATCH /care-recipients/{id}/status?status=ACTIVE\|INACTIVE` | OWNER | `ARCHIVED` không đặt được ở đây (`2103`) |
| `DELETE /care-recipients/{id}` | OWNER | Lưu trữ: `status=ARCHIVED` + `deleted_at`; sau đó customer nhận 404 |
| `POST/GET /care-recipients/{id}/medications` (`?active=`) | EDITOR / VIEWER | `timeOfDay` dạng `["HH:mm"]`, `endDate ≥ startDate` |
| `GET/PUT/DELETE .../medications/{medicationId}` | VIEWER / EDITOR / EDITOR | PUT không đổi `active` |
| `PATCH .../medications/{medicationId}/active?active=true\|false` | EDITOR | |
| `POST/GET /care-recipients/{id}/emergency-contacts` | EDITOR / VIEWER | `primary=true` → bỏ cờ liên hệ chính cũ |
| `GET/PUT/DELETE .../emergency-contacts/{contactId}` | VIEWER / EDITOR / EDITOR | PUT: `primary` null = giữ nguyên |
| `PATCH .../emergency-contacts/{contactId}/primary` | EDITOR | |
| `GET /care-recipients/{id}/managers` | VIEWER | Kèm profile từ Identity (batch); Identity lỗi → `profileAvailable=false` |
| `POST /care-recipients/{id}/managers` | OWNER | `{email, relationshipType, accessLevel}`; Identity lỗi → 503 `2500` |
| `PATCH .../managers/{managerId}` | OWNER | `{accessLevel?, relationshipType?}`; bảo vệ quản lý chính / OWNER cuối |
| `PATCH .../managers/{managerId}/primary` | OWNER | Người nhận phải là OWNER (`2404`) |
| `DELETE .../managers/{managerId}` | OWNER (người khác) / chính mình | Tự rời: message "Bạn đã rời khỏi hồ sơ" |

### Admin (`hasRole('ADMIN')`, chỉ đọc)

| Method / path | Ghi chú |
|---|---|
| `GET /admin/care-recipients?status=&keyword=&createdBy=&includeDeleted=&page=&size=` | Toàn hệ thống |
| `GET /admin/care-recipients/{id}` | Cả hồ sơ đã lưu trữ |

### API nội bộ (service-to-service — gateway chặn `/internal/**`)

Header bắt buộc `X-Internal-Secret: <CARE_RECIPIENT_INTERNAL_SECRET>`; thiếu/sai → 401. Không nhận JWT người dùng.

| Method / path | Hành vi |
|---|---|
| `GET /internal/care-recipients/{id}/snapshot` | Tên, ngày sinh/tuổi, giới tính, SĐT, địa chỉ + tọa độ, nhóm máu, mobility/independence, dị ứng, bệnh mạn tính, ghi chú ăn uống/hành vi, yêu cầu chăm sóc, trạng thái, **thuốc đang active**, **liên hệ khẩn cấp chính**, `snapshotAt`. Hồ sơ đã lưu trữ → 404. Booking lưu nguyên snapshot lúc đặt lịch. |
| `GET /internal/care-recipients/{id}/access?userId=&minLevel=VIEWER` | Luôn 200: `{recipientId,userId,allowed,accessLevel}` |
| `POST /internal/care-recipients/batch` | `{"recipientIds":[...]}` (1–100); bỏ qua id không có / đã lưu trữ |

Caregiver chưa được xem hồ sơ: chờ BookingService (`service/CaregiverAssignmentService`, TODO).

### Tích hợp IdentityService (OpenFeign)

`client/IdentityClient` → `/internal/users/{id}`, `/internal/users/by-email`, `/internal/users/batch` (URL env, không
discovery). Interceptor gắn `X-Internal-Secret` + `X-Request-Id`. `IdentityErrorDecoder`: 404 → `IDENTITY_USER_NOT_FOUND`
(lỗi nghiệp vụ, không tính vào circuit breaker); 401/403/5xx/khác → `IDENTITY_UNAVAILABLE` 503. Circuit breaker
Resilience4j (cấu hình Java `config/ResilienceConfig`: cửa sổ 10, tối thiểu 5 lời gọi, ngưỡng 50%, mở 10s), chạy trên
thread request (không time limiter; timeout do connect/read timeout của Feign). Fallback: batch → rỗng; get/by-email →
ném lại lỗi (không đoán khi cần xác minh trước khi ghi). Không gọi Identity trong DB transaction đang mở.

## Kiểm thử

```powershell
.\mvnw.cmd test        # H2 + IdentityService giả (JWKS + /internal/users) qua HTTP thật, không cần env
```

Kiểm schema thật (PostgreSQL 17, container riêng, không trỏ vào DB dev):

```powershell
docker run --rm -d --name careconnect-care-recipient-verify-pg -e POSTGRES_PASSWORD=care_recipient_test_only -p 127.0.0.1:55495:5432 postgres:17
.\mvnw.cmd test '-Dcare-recipient.testing.postgres=true'
docker stop careconnect-care-recipient-verify-pg
```

Kết quả 2026-10-03: **47/47** trên H2 và **47/47** trên PostgreSQL 17 (schema SQL + `validate`, partial unique index thật).
Gồm: JWT sai audience/issuer/refresh, role, header giả mạo, 404 vs 403, OWNER/EDITOR/VIEWER, quản lý chính / OWNER
cuối cùng (unit Mockito + MockMvc), version conflict, CSRF cookie, Feign ErrorDecoder + fallback + circuit mở, API nội bộ.
E2E toàn hệ thống: từ `BE/` chạy `.\scripts\e2e\Run-E2E.ps1`; kết quả lần chạy: `../docs/2026-10-03-PLAN_CARE_RECIPIENT_GATEWAY.md` §7.

## TODO

- Đồng bộ `recipient_managers` khi user bị khóa / xóa ở Identity (event qua broker + outbox) — hiện chưa có.
- Quyền xem hồ sơ cho caregiver được phân công (BookingService).
- Bảng audit thay đổi người quản lý / hồ sơ (hiện chỉ có `updated_by`).
- Chuyển Flyway cùng đợt với mọi service.
