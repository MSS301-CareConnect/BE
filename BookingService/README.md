# CareConnect BookingService

Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.3. Port mặc định **8085**. Service sở hữu booking (đặt lịch chăm sóc),
từng buổi chăm sóc (`booking_occurrences` — không có bảng care session riêng), dịch vụ trong booking và lịch sử trạng thái.
Không sở hữu tài khoản (Identity), hồ sơ người bệnh (CareRecipient), trung tâm / caregiver (Provider), danh mục & giá
(CareCatalog), tiền (Payment — chưa có).

Quy ước chung: `../AGENTS.md`; phần riêng: `AGENTS.md`; thiết kế + quyết định: `../docs/2026-10-09-PLAN_BOOKING.md`.

## Chạy local

```powershell
Copy-Item .env.example .env          # điền DB password, JWT issuer/JWKS, URL + secret của Identity / CareRecipient / Provider
docker compose up -d                 # PostgreSQL 127.0.0.1:5445 (DB CARECONNECT_BOOKING)
Get-Content -Raw schema/booking.sql | docker compose exec -T postgres psql -U <BOOKING_DB_USERNAME> -d CARECONNECT_BOOKING -v ON_ERROR_STOP=1   # chỉ DB mới
.\scripts\Start-Dev.ps1              # nạp .env rồi spring-boot:run
.\mvnw.cmd test                      # H2 + stub HTTP (JWKS, Identity, CareRecipient, Provider)
```

- Không Redis, không Flyway; `ddl-auto: validate`; JVM chạy UTC, thời điểm lưu `timestamptz`, lịch sinh theo `BOOKING_TIMEZONE`.
- Test trên PostgreSQL thật: container riêng (vd `docker run -d --name booking-test-pg -e POSTGRES_PASSWORD=booking_test_only -p 127.0.0.1:55496:5432 postgres:17`)
  rồi `.\mvnw.cmd test -Dbooking.testing.postgres=true` (mỗi lần chạy tạo schema riêng, áp `schema/booking.sql` + `validate`).
- Phụ thuộc lúc chạy: IdentityService (JWKS + `/internal/users/batch`), CareRecipientService (`/internal/care-recipients/**`),
  ProviderService (`/internal/centers/**`, `/internal/caregivers/**`). Mỗi bên cần secret nội bộ khớp (xem `.env.example`).

## Trạng thái booking

```
PENDING ──accept──▶ AWAITING_DEPOSIT ──deposit-paid──▶ CONFIRMED ──(buổi đầu bắt đầu, giai đoạn sau)──▶ IN_PROGRESS ──▶ COMPLETED
   ├─reject──▶ REJECTED              ├─quá hạn cọc──▶ EXPIRED      │
   ├─quá hạn duyệt──▶ EXPIRED         │                             │
   └─khách hủy──▶ CANCELLED ◀─────────┴─khách / trung tâm hủy ◀─────┴─ trung tâm hủy (đặc biệt, cả khi IN_PROGRESS)
```

Hủy / từ chối / hết hạn → các buổi `SCHEDULED` thành `CANCELLED` (giải phóng caregiver). Mỗi lần đổi trạng thái ghi
`booking_status_history`. Hạn duyệt = tạo + 24h, hạn cọc = chấp nhận + 24h (đều không quá giờ buổi đầu).

## API (đường dẫn trong service; qua gateway thêm `/api/v1`)

| Ai | Method + path | Ghi chú |
|---|---|---|
| CUSTOMER | `POST /bookings` | Header tùy chọn `Idempotency-Key` (≤100 ký tự `[A-Za-z0-9._-]`): cùng key + cùng nội dung → trả booking cũ; khác nội dung → 409 `5102` |
| | `GET /bookings?status=&recipientId=&page=&size=` · `GET /bookings/{id}` · `GET /bookings/{id}/occurrences` | Booking người khác → 404 |
| | `POST /bookings/{id}/cancel` `{reason}` | Trước giờ buổi đầu |
| CENTER_MANAGER | `GET /center/bookings?status=&caregiverId=&from=&to=` · `GET /center/bookings/{id}` | `center_id` từ token; kèm tên / SĐT khách |
| | `POST /center/bookings/{id}/accept` `{caregiverId?}` · `POST /{id}/reject` `{reason}` · `POST /{id}/cancel` `{reason}` | |
| | `PATCH /center/bookings/{id}/caregiver` `{caregiverId}` · `PATCH /center/bookings/occurrences/{occurrenceId}/caregiver` | Chỉ buổi SCHEDULED chưa bắt đầu; kiểm trùng |
| ADMIN | `GET /admin/bookings?status=&centerId=&customerId=&recipientId=&from=&to=` · `GET /admin/bookings/{id}` | Chỉ đọc |
| CAREGIVER | `GET /caregiver/occurrences?from=&to=&status=` (≤62 ngày) · `GET /caregiver/occurrences/{id}` | Chỉ buổi được giao của booking đã xác nhận; chi tiết kèm snapshot người bệnh |
| Nội bộ (`X-Internal-Secret`) | `GET /internal/bookings/caregiver-access?caregiverUserId=&recipientId=` | Luôn 200 `{allowed}`; CareRecipient dùng để cho caregiver xem hồ sơ |
| | `POST /internal/bookings/{id}/deposit-paid` `{paymentReference?}` | TODO(Payment) — tạm để test; gọi lại khi đã CONFIRMED → 200 |
| | `GET /internal/bookings/{id}` · `GET /internal/bookings/occurrences/{occurrenceId}` | Cho Payment / care report sau này |

`caregiverId` trong request / response = `caregiver_profiles.caregiver_id` của ProviderService; `caregiverUserId` = user
Identity của caregiver (lấy từ Provider, không nhận từ client).

Tạo booking: sinh buổi (ONE_TIME / DAILY / WEEKLY, `daysOfWeek` ISO 1=Thứ Hai…7=CN) → CareRecipient kiểm khách có quyền
≥ EDITOR (không → 404) + hồ sơ ACTIVE + lấy snapshot → Provider kiểm trung tâm ACTIVE (+ caregiver nếu chọn: thuộc trung
tâm, VERIFIED/ACTIVE, làm được dịch vụ) → khóa lịch, kiểm trùng caregiver / người bệnh → lưu. Giá: NULL tới khi nối Catalog.

## Bảng mã lỗi chính (5000–5999)

| Code | Key | Ý nghĩa |
|---|---|---|
| 5007 | `CENTER_SCOPE_REQUIRED` | Token manager không có `center_id` (403) |
| 5100 / 5101 | `BOOKING_NOT_FOUND` / `INVALID_BOOKING_TRANSITION` | Không thấy (404) / sai trạng thái (409) |
| 5102 / 5103 | `IDEMPOTENCY_KEY_REUSED` / `INVALID_IDEMPOTENCY_KEY` | |
| 5107 | `BOOKING_ALREADY_STARTED` | Khách hủy sau giờ buổi đầu |
| 5200–5214 | lịch | Thiếu field, ngày quá khứ, ca qua đêm, thứ trong tuần sai, quá 60 buổi / 90 ngày, báo trước < 12h… |
| 5215 / 5216 | `CAREGIVER_SCHEDULE_CONFLICT` / `RECIPIENT_SCHEDULE_CONFLICT` | Trùng lịch (409) |
| 5400–5408 | phân công / trung tâm | Thiếu caregiver khi accept, caregiver khác trung tâm / chưa xác minh / không hoạt động / không làm được dịch vụ, trung tâm không tồn tại / không hoạt động |
| 5500 / 5600 / 5700 | `IDENTITY_UNAVAILABLE` / `CARE_RECIPIENT_UNAVAILABLE` / `PROVIDER_UNAVAILABLE` | 503 |
| 5601 / 5602 | `RECIPIENT_NOT_FOUND` / `RECIPIENT_NOT_ACTIVE` | |

Danh sách đầy đủ: `exception/ErrorCode.java`.

## Biến môi trường

Xem `.env.example` (chỉ tên + chú thích): `BOOKING_SERVER_PORT`, `BOOKING_SWAGGER_ENABLED`, `BOOKING_OPENAPI_SERVER_URL` ·
`BOOKING_DB_*` · `BOOKING_JWT_ISSUER`, `BOOKING_JWT_JWKS_URI` · `BOOKING_INTERNAL_SECRET` · `BOOKING_IDENTITY_*`,
`BOOKING_CARE_RECIPIENT_*`, `BOOKING_PROVIDER_*` (URL, secret, timeout) · `BOOKING_TIMEZONE`, `BOOKING_MAX_OCCURRENCES`,
`BOOKING_MAX_SPAN_DAYS`, `BOOKING_MIN_LEAD_TIME`, `BOOKING_ACCEPTANCE_TIMEOUT`, `BOOKING_DEPOSIT_TIMEOUT`,
`BOOKING_MAX_CAREGIVER_RANGE_DAYS`, `BOOKING_EXPIRY_BATCH_SIZE`, `BOOKING_EXPIRY_SCAN_INTERVAL` ·
`BOOKING_CATALOG_INTEGRATION_ENABLED`.

Swagger: `http://localhost:8085/swagger-ui.html` (hoặc tổng hợp ở gateway). Health: `/actuator/health`.
Access token nên giữ 10–15 phút: service này xác minh offline, không nạp lại quyền theo thời gian thực.
