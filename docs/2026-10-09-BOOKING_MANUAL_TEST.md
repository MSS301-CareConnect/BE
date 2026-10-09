# Hướng dẫn test luồng Booking (giai đoạn 1)

> Áp dụng cho tiến độ 2026-10-09: tạo booking → trung tâm chấp nhận + phân công caregiver → đặt cọc (giả lập) →
> CONFIRMED → caregiver xem lịch và hồ sơ người bệnh. Chưa có: giá (Catalog), Payment thật, bắt đầu/kết thúc ca,
> care report. Thiết kế: `docs/2026-10-09-PLAN_BOOKING.md`.

Có hai cách:

- **Cách 1 — tự động** (khuyên dùng để kiểm nhanh mọi thứ còn chạy đúng).
- **Cách 2 — test tay** trên môi trường dev để quan sát từng bước.

---

## Cách 1 — Chạy script e2e (khoảng 5 phút)

Từ thư mục `BE/`:

```powershell
.\scripts\e2e\Run-E2E.ps1
```

- Tự build 5 JAR, dựng PostgreSQL/Redis **riêng** (không đụng DB dev), seed manager / caregiver / trung tâm, chạy
  toàn bộ luồng booking (phase `e`) và ca CareRecipient tắt (phase `f`), rồi dọn sạch.
- Kết quả mong đợi ở dòng cuối: `E2E PASSED (a, b, c, e, d, f)`.
- `-SkipBuild`: dùng JAR có sẵn. `-KeepInfra`: giữ container/volume e2e để xem dữ liệu.
- Log: `%TEMP%\careconnect-e2e\*.log`.
- Yêu cầu: JDK 21, Docker đang chạy, Python 3, file `.env` trong IdentityService, CareRecipientService,
  ProviderService, ApiGateway; cổng 8080–8082, 8084–8085 đang trống. BookingService **không** cần `.env` cho e2e.

---

## Cách 2 — Test tay trên môi trường dev

### 0. Cấu hình `.env` (một lần)

| File | Thêm / kiểm tra |
|---|---|
| `BookingService/.env` | Tạo từ `.env.example`. `BOOKING_JWT_ISSUER` = đúng `IDENTITY_JWT_ISSUER`; `BOOKING_JWT_JWKS_URI=http://localhost:8081/.well-known/jwks.json`; URL + secret của Identity / CareRecipient / Provider; `BOOKING_INTERNAL_SECRET`; các quy tắc lịch (để giá trị mặc định); `BOOKING_CATALOG_INTEGRATION_ENABLED=false` |
| `CareRecipientService/.env` | `CARE_RECIPIENT_BOOKING_URL=http://localhost:8085`, `CARE_RECIPIENT_BOOKING_INTERNAL_SECRET` = **giống hệt** `BOOKING_INTERNAL_SECRET` |
| `ApiGateway/.env` | `GATEWAY_BOOKING_URL=http://localhost:8085` |

Secret nội bộ: service bị gọi giữ secret gốc, service gọi dán **cùng giá trị**:

| Service bị gọi | Service gọi |
|---|---|
| Identity `IDENTITY_INTERNAL_SECRET` | `BOOKING_IDENTITY_INTERNAL_SECRET`, `CARE_RECIPIENT_IDENTITY_INTERNAL_SECRET`, `PROVIDER_IDENTITY_INTERNAL_SECRET` |
| CareRecipient `CARE_RECIPIENT_INTERNAL_SECRET` | `BOOKING_CARE_RECIPIENT_INTERNAL_SECRET` |
| Provider `PROVIDER_INTERNAL_SECRET` | `BOOKING_PROVIDER_INTERNAL_SECRET` |
| Booking `BOOKING_INTERNAL_SECRET` | `CARE_RECIPIENT_BOOKING_INTERNAL_SECRET` |

Tạo một secret ngẫu nhiên (PowerShell):

```powershell
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | % { $_.ToString('x2') })
```

Không commit giá trị thật (`.env` đã được gitignore).

### 1. Chạy các service

Trong từng thư mục `IdentityService`, `CareRecipientService`, `ProviderService`, `BookingService`:

```powershell
docker compose up -d
# chỉ khi DB mới tạo: áp schema SQL của service, ví dụ với Booking:
Get-Content -Raw schema/booking.sql | docker compose exec -T postgres psql -U <BOOKING_DB_USERNAME> -d CARECONNECT_BOOKING -v ON_ERROR_STOP=1
.\scripts\Start-Dev.ps1
```

Sau đó chạy `ApiGateway` (`.\scripts\Start-Dev.ps1`, không có DB). CareCatalogService **không** cần chạy.

| Service | Cổng |
|---|---|
| ApiGateway | 8080 |
| IdentityService | 8081 |
| CareRecipientService | 8082 |
| ProviderService | 8084 |
| BookingService | 8085 |

Kiểm tra: `GET http://localhost:<cổng>/actuator/health` trả `UP`.

**Công cụ gọi API:** Swagger tổng hợp `http://localhost:8080/swagger-ui.html` (chọn `identity` / `care-recipient` /
`booking`) hoặc Postman.

- Luôn dùng **mobile login + Bearer token** để không phải xử lý CSRF:
  `POST /api/v1/auth/mobile/login` với `{"email": "...", "password": "..."}` → lấy `result.accessToken`, gửi header
  `Authorization: Bearer <token>`.
- ProviderService **chưa có route qua gateway** → gọi thẳng `http://localhost:8084/...` (vẫn kèm Bearer).

### 2. Chuẩn bị dữ liệu (làm một lần)

Cần đủ 4 vai: customer, trung tâm ACTIVE, center manager, caregiver đã xác minh.

| # | Ai | Gọi | Body / ghi chú |
|---|---|---|---|
| 1 | ADMIN (tài khoản bootstrap) | `POST http://localhost:8084/admin/centers` | `{"centerName":"TT An Tâm","email":"antam@center.vn","address":"Hà Nội"}` → ghi lại **centerId** |
| 2 | ADMIN | `PATCH http://localhost:8084/admin/centers/{centerId}/status` | `{"status":"ACTIVE","version":0}` |
| 3 | ADMIN | `POST /api/v1/users` | `{"email":"manager@...","fullName":"Quản lý","roleCode":"CENTER_MANAGER","centerId":"<centerId>"}` |
| 4 | Manager | `POST /api/v1/users/forgot-password` → OTP email → `POST /api/v1/users/reset-password` | Tài khoản do admin tạo chưa có mật khẩu |
| 5 | Manager (đã login) | `POST /api/v1/users` | `{"email":"caregiver@...","fullName":"Chị Hoa","roleCode":"CAREGIVER"}` → ghi lại **user id** của caregiver; đặt mật khẩu bằng OTP như bước 4 |
| 6 | ADMIN | `POST http://localhost:8084/centers/{centerId}/caregivers` | `{"userId":"<user id caregiver>","fullName":"Chị Hoa","experienceYears":3}` → ghi lại **caregiverId** (của Provider) và `version` |
| 7 | ADMIN | `PATCH http://localhost:8084/admin/caregivers/{caregiverId}/verification` | `{"status":"VERIFIED","version":<version>}` → caregiver VERIFIED / ACTIVE |
| 8 | Customer | `POST /api/v1/users/register` → OTP → `POST /api/v1/users/register/verify` | Hoặc dùng tài khoản customer đã có |

Lưu ý:

- `centerId` ở bước 3 **phải đúng** id trung tâm bên Provider. Token của manager mang claim `center_id` này; Booking
  dùng nó để giới hạn manager chỉ thấy booking của trung tâm mình.
- Bước 4, 5, 8 cần OTP qua email (Brevo). Nếu chưa cấu hình Brevo: có thể sinh SQL tạo tài khoản bằng
  `scripts/e2e/SeedCustomers.java` (cách script e2e làm) — **chỉ** áp vào DB dev cục bộ của bạn.
- Booking dùng **hai id caregiver**: `caregiverId` (Provider) trong request/response, `caregiverUserId` (Identity)
  do Booking tự lấy từ Provider.

### 3. Luồng booking (qua gateway `http://localhost:8080`)

**3.1. Customer tạo hồ sơ người bệnh** — `POST /api/v1/care-recipients`

```json
{
  "fullName": "Bà Lan",
  "relationshipType": "CHILD",
  "addressLine": "12 Lý Thường Kiệt",
  "district": "Hoàn Kiếm",
  "province": "Hà Nội",
  "allergies": "Penicillin"
}
```

Ghi lại `result.profile.id` → **recipientId**.

**3.2. Customer đặt lịch** — `POST /api/v1/bookings`, header `Idempotency-Key: test-1`

```json
{
  "recipientId": "<recipientId>",
  "centerId": "<centerId>",
  "scheduleType": "WEEKLY",
  "startDate": "2026-10-19",
  "endDate": "2026-11-01",
  "startTime": "08:00",
  "endTime": "10:00",
  "daysOfWeek": [1, 3, 5],
  "customerNote": "Bà cần hỗ trợ đi lại",
  "services": [{ "careServiceId": "11111111-1111-4111-8111-111111111111" }]
}
```

Mong đợi:

- `status = PENDING`, `bookingCode` dạng `BK-yyyyMMdd-XXXXXX`.
- 6 buổi (Thứ Hai / Tư / Sáu trong 2 tuần), `startAt` kết thúc bằng `T01:00:00Z` (= 08:00 giờ Việt Nam).
- `recipientSnapshot` và `serviceAddress` lấy từ hồ sơ; giá (`totalAmount`, `unitPrice`) là `null` (chưa nối Catalog).

Quy tắc lịch:

- `startDate` cách hiện tại ít nhất **12 giờ**; tối đa **60 buổi**, khoảng **90 ngày**.
- `daysOfWeek`: 1 = Thứ Hai … 7 = Chủ Nhật, chỉ dùng cho `WEEKLY`. `ONE_TIME` bỏ `endDate`; `DAILY` không gửi `daysOfWeek`.
- `endTime` phải sau `startTime` (chưa hỗ trợ ca qua đêm).
- `careServiceId` điền UUID bất kỳ (chưa kiểm với Catalog). `caregiverId` có thể gửi ngay ở bước này (tùy chọn).

**3.3. Chống tạo trùng** — gửi lại đúng request 3.2 với cùng `Idempotency-Key` → trả **cùng `id`**. Đổi nội dung
nhưng giữ key → `409`, code `5102`.

**3.4. Xem các buổi** — `GET /api/v1/bookings/{bookingId}/occurrences` → 6 buổi `SCHEDULED`.

**3.5. Manager xem danh sách** — `GET /api/v1/center/bookings` (token manager) → thấy booking kèm `customer.fullName`,
`customer.phoneNumber`.

**3.6. Manager chấp nhận + phân công** — `POST /api/v1/center/bookings/{bookingId}/accept`

```json
{ "caregiverId": "<caregiverId của Provider>" }
```

Mong đợi: `status = AWAITING_DEPOSIT`, `caregiverUserId` = user id caregiver, `depositDueAt` có giá trị, các buổi đã
có `caregiverId`.

**3.7. Giả lập đã đặt cọc** (chưa có Payment Service) — gọi **thẳng Booking**, không qua gateway (gateway chặn
`/internal/**`):

```
POST http://localhost:8085/internal/bookings/{bookingId}/deposit-paid
X-Internal-Secret: <giá trị BOOKING_INTERNAL_SECRET>
Content-Type: application/json

{ "paymentReference": "PAY-1" }
```

Mong đợi: `status = CONFIRMED`. Gọi lại lần nữa vẫn `200` (không đổi gì).

**3.8. Customer xem chi tiết** — `GET /api/v1/bookings/{bookingId}` → `CONFIRMED`, kèm `services` và `occurrences`.

**3.9. Caregiver** (đăng nhập bằng tài khoản caregiver):

| Gọi | Mong đợi |
|---|---|
| `GET /api/v1/caregiver/occurrences?from=2026-10-15&to=2026-11-10` | 6 buổi được giao (khoảng ngày tối đa 62 ngày) |
| `GET /api/v1/caregiver/occurrences/{occurrenceId}` | Chi tiết buổi kèm `recipientSnapshot` (dị ứng, thuốc, liên hệ khẩn cấp…) |
| `GET /api/v1/caregiver/care-recipients/{recipientId}` | `200` — CareRecipient hỏi Booking và xác nhận đang được phân công |

### 4. Các ca lỗi nên thử

| Thử | Mong đợi |
|---|---|
| Tài khoản customer khác đặt lịch cho hồ sơ không phải của mình | `404`, code `5601` |
| Đặt lịch thứ hai cho cùng người bệnh, giờ chồng nhau | `409`, code `5216` |
| Người bệnh khác, cùng caregiver, giờ chồng nhau | `409`, code `5215` |
| Giờ nối tiếp đúng lúc kết thúc (vd 10:00–12:00 sau 08:00–10:00) | `200` (không tính là trùng) |
| Caregiver xem hồ sơ khi booking mới `AWAITING_DEPOSIT` | `404` |
| Caregiver khác xem buổi / hồ sơ không được giao | `404` |
| Manager trung tâm khác xem booking | `404`, code `5100` |
| `accept` khi booking chưa có caregiver và không gửi `caregiverId` | `409`, code `5400` |
| `deposit-paid` khi booking đang `PENDING` | `409`, code `5101` |
| `POST /api/v1/bookings/{id}/cancel` với `{"reason":"Đổi kế hoạch"}` | `CANCELLED`, mọi buổi `CANCELLED` |
| Manager từ chối: `POST /api/v1/center/bookings/{id}/reject` với `{"reason":"..."}` | `REJECTED`, caregiver được giải phóng |
| Tắt CareRecipientService rồi đặt lịch | `503`, code `5600`, không tạo booking |
| Tắt ProviderService rồi đặt lịch | `503`, code `5700` |

### 5. Thử hết hạn (tùy chọn)

Trong `BookingService/.env` đặt tạm:

```
BOOKING_ACCEPTANCE_TIMEOUT=2m
BOOKING_EXPIRY_SCAN_INTERVAL=30s
```

Khởi động lại Booking, tạo booking và **không** chấp nhận → sau khoảng 2–3 phút booking chuyển `EXPIRED`, các buổi
`CANCELLED`. Nhớ trả lại giá trị mặc định (`24h`, `5m`) sau khi thử.

---

## Tra cứu nhanh

| Mã | Ý nghĩa |
|---|---|
| `401` | Thiếu / sai token, hoặc `BOOKING_JWT_ISSUER` không khớp Identity |
| `5005` (503) | Booking không tải được JWKS (Identity chưa chạy / sai `BOOKING_JWT_JWKS_URI`) |
| `5007` (403) | Token manager không có `center_id` |
| `5100` / `5101` | Booking không tồn tại / sai trạng thái |
| `5200–5214` | Lỗi quy tắc lịch (ngày quá khứ, báo trước < 12h, thiếu `daysOfWeek`…) |
| `5400–5408` | Phân công / trung tâm (thiếu caregiver, khác trung tâm, chưa xác minh, trung tâm không hoạt động…) |
| `5500` / `5600` / `5700` | Identity / CareRecipient / Provider không khả dụng |
| `2502` (CareRecipient) | Caregiver xem hồ sơ nhưng Booking không khả dụng, hoặc sai `CARE_RECIPIENT_BOOKING_INTERNAL_SECRET` |

Bảng đầy đủ: `BookingService/README.md` và `BookingService/src/main/java/mss301/bookingservice/exception/ErrorCode.java`.
