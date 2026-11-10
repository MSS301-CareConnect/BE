# Hướng dẫn test nguyên luồng Booking trên Swagger

> Ngày viết: **2026-10-22** — theo code sau Sprint 1–6 (`docs/2026-10-22-SPRINT_1-6_BOOKING.md`). Thay cho bản cũ
> `docs/2026-10-09-BOOKING_MANUAL_TEST.md` (path `/admin/**`, `/center/**`, gọi thẳng Provider đã bỏ).
> Mọi request đi **qua gateway** `http://localhost:8080`. Luồng tự động tương đương: `scripts/e2e/Run-E2E.ps1`.

---

## 0. Chuẩn bị

### 0.1 Chạy hệ thống

Cách nhanh nhất — toàn bộ trong Docker, từ thư mục `BE/`:

```powershell
.\start.bat
```

Hoặc chạy từng service (`docker compose up -d` + `.\scripts\Start-Dev.ps1` trong mỗi thư mục): IdentityService,
CareRecipientService, CareCatalogService, ProviderService, BookingService, ApiGateway (MediaService không cần cho luồng này).

**PaymentService chưa có trong repo** — Booking gọi Payment khi tạo booking (kiểm nợ) và khi hủy (xem trước tiền hoàn),
thiếu Payment → 503 `5154`. Chạy PaymentService giả trong một cửa sổ riêng (gateway đã trỏ `GATEWAY_PAYMENT_URL=http://localhost:8087`):

```powershell
python scripts\e2e\payment_stub.py 8087
```

DB dev đã có dữ liệu cũ → áp các change script `schema/changes/2026-10-22_*` của BookingService (thứ tự trong
`BookingService/README.md`) và ProviderService trước khi chạy.

Kiểm tra: `http://localhost:808x/actuator/health` → `UP` (8080–8085).

### 0.2 Dùng Swagger

1. Mở `http://localhost:8080/swagger-ui.html`, chọn spec ở ô **Select a definition** (góc trên phải):
   `identity`, `care-catalog`, `provider`, `care-recipient`, `booking`.
2. Ở ô **Servers** chọn server của gateway (`http://localhost:8080`) nếu có nhiều lựa chọn.
3. Đăng nhập bằng **mobile login** để không phải xử lý cookie / CSRF:
   `identity` → `POST /api/v1/auth/mobile/login` `{"email": "...", "password": "..."}` → copy `result.accessToken`.
4. Bấm **Authorize** → ô `bearerAuth` dán **chỉ token** (không gõ chữ `Bearer`) → Authorize. Đổi vai = Logout rồi dán
   token khác. Token sống ~15 phút; hết hạn → login lại.

Ghi lại các giá trị sau trong lúc test (ký hiệu dùng bên dưới):

| Ký hiệu | Lấy ở bước |
|---|---|
| `ADMIN_TOKEN`, `MANAGER_TOKEN`, `CAREGIVER_TOKEN`, `CUSTOMER_TOKEN`, `SERVICE_TOKEN` | login / §1.7 |
| `centerId`, `centerVersion` | A1 |
| `managerUserId` (Identity) | A4a |
| `careServiceId` (catalog item), `centerServiceId`, `pricePlanId` | A3–A5 |
| `caregiverUserId` (Identity), `caregiverId` (Provider), `caregiverVersion` | A6–A7 |
| `recipientId`, `bookingId`, `occurrenceId`, `sessionId` | B… |

---

## 1. Dựng dữ liệu (làm một lần)

### Tài khoản và mật khẩu

- **ADMIN**: tài khoản bootstrap (`IDENTITY_BOOTSTRAP_ENABLED=true`, `IDENTITY_BOOTSTRAP_EMAIL/PASSWORD` trong
  `IdentityService/.env`, khởi động Identity một lần rồi tắt lại).
- Tài khoản do admin / manager tạo (`POST /api/v1/users`) **chưa có mật khẩu** → chủ email đặt qua OTP:
  `POST /api/v1/auth/forgot-password` `{"email": "..."}` → mở email lấy mã →
  `POST /api/v1/auth/reset-password` `{"email": "...", "otpCode": "123456", "newPassword": "Abc@12345"}`.
- Khách tự đăng ký: `POST /api/v1/auth/register` `{"email","password","fullName","phoneNumber"}` → OTP →
  `POST /api/v1/auth/register/verify` `{"email","otpCode"}`.
- Chưa cấu hình Brevo (không nhận được email) → tạo tài khoản bằng SQL sinh từ `scripts/e2e/SeedCustomers.java`
  (cách `Run-E2E.ps1` làm) — **chỉ** trên DB dev cục bộ.
- Mật khẩu ≥ 8 ký tự, có chữ hoa, chữ thường, số, ký tự đặc biệt.

### A. Trung tâm, dịch vụ, caregiver

| # | Vai (token) | Spec → endpoint | Body / ghi chú |
|---|---|---|---|
| A1 | ADMIN | provider → `POST /api/v1/care-centers` | `{"centerName": "TT An Tâm", "email": "antam@center.vn", "phoneNumber": "0901234567", "address": "12 Láng Hạ, Hà Nội", "operatingHours": "07:00-21:00"}` → **centerId**, `version` |
| A2 | ADMIN | provider → `PATCH /api/v1/care-centers/{centerId}/status` | `{"status": "ACTIVE", "version": <version A1>}` |
| A3 | ADMIN | care-catalog → `POST /api/v1/care-services` | `{"name": "Chăm sóc người già tại nhà", "category": "ELDERLY_CARE", "status": "ACTIVE"}` → **careServiceId** |
| A4a | ADMIN | identity → `POST /api/v1/users` | `{"email": "manager@antam.vn", "fullName": "Quản lý An Tâm", "roleCode": "CENTER_MANAGER", "centerId": "<centerId>"}` → **managerUserId** (`result.id`); đặt mật khẩu bằng OTP → login → **MANAGER_TOKEN** |
| A4c | ADMIN | provider → `PUT /api/v1/care-centers/{centerId}` | Gán manager cho trung tâm **bên Provider**: `{"centerName": "TT An Tâm", "email": "antam@center.vn", "phoneNumber": "0901234567", "address": "12 Láng Hạ, Hà Nội", "operatingHours": "07:00-21:00", "managerUserId": "<managerUserId>", "version": <version mới nhất — GET /api/v1/care-centers/{centerId}>}` → response có `managerUserId`. PUT toàn phần: gửi đủ field như A1 |
| A4b | MANAGER | care-catalog → `POST /api/v1/center-services` | `{"careServiceId": "<careServiceId>", "description": "Theo giờ", "status": "ACTIVE"}` → **centerServiceId** |
| A5 | MANAGER | care-catalog → `POST /api/v1/price-plans` | `{"centerServiceId": "<centerServiceId>", "planName": "Theo giờ", "pricingUnit": "HOUR", "durationMinutes": 60, "basePrice": 100000, "isActive": true}` → **pricePlanId** |
| A6a | MANAGER | identity → `POST /api/v1/users` | `{"email": "hoa@antam.vn", "fullName": "Chị Hoa", "roleCode": "CAREGIVER"}` (centerId lấy từ token manager) → **caregiverUserId**; đặt mật khẩu bằng OTP |
| A6b | MANAGER | provider → `POST /api/v1/caregivers/centers/{centerId}` | `{"userId": "<caregiverUserId>", "fullName": "Chị Hoa", "gender": "FEMALE", "experienceYears": 3, "bio": "3 năm chăm người cao tuổi"}` → **caregiverId**, `version` |
| A7 | MANAGER | provider → `PUT /api/v1/caregivers/{caregiverId}/services` | `{"centerServiceIds": ["<centerServiceId>"]}` — kỹ năng; thiếu → booking báo `CAREGIVER_SERVICE_NOT_SUPPORTED` |
| A8 | ADMIN | provider → `PATCH /api/v1/caregivers/{caregiverId}/verification` | `{"status": "VERIFIED", "version": <version mới nhất — GET /api/v1/caregivers/{caregiverId}>}` → caregiver VERIFIED + ACTIVE |

> **Vì sao cần A4c:** ProviderService không dùng `center_id` trong token để xét quyền quản lý — nó so `userId` của
> người gọi với `care_centers.manager_user_id` (`ProviderAuthorization.requireCenterManagement`). A4a chỉ ghi
> `centerId` vào tài khoản bên Identity; bỏ A4c thì A6b, B1, B3… trả 403 `4117 CENTER_ACCESS_DENIED` "Bạn không quản
> lý trung tâm này" dù `centerId` đúng. Mỗi manager chỉ quản lý một trung tâm (trùng → `CENTER_MANAGER_EXISTED`).
> `version` của trung tâm đã tăng sau A2 — luôn GET lại trước khi PUT.

### B. Lịch làm việc của caregiver (bắt buộc từ PRV-02)

Booking hỏi Provider "caregiver có ca làm việc phủ giờ buổi chăm sóc không" khi đặt / duyệt / đổi người. Không có ca →
409 `5226 CAREGIVER_NOT_AVAILABLE`. Tạo ca cho **đúng ngày sẽ đặt** (gọi là ngày `D`, phải cách hiện tại ≥ 12 giờ):

| # | Vai | Spec → endpoint | Body |
|---|---|---|---|
| B1 | MANAGER | provider → `POST /api/v1/work-shifts/centers/{centerId}` | `{"workDate": "D", "startTime": "06:00", "endTime": "20:00", "shiftType": "DAY", "shiftStatus": "OPEN", "capacity": 5}` → **shiftId** |
| B2 | CAREGIVER | provider → `POST /api/v1/work-shifts/{shiftId}/registrations` | `{"caregiverNote": "Em nhận ca"}` → **registrationId**, `version` |
| B3 | MANAGER | provider → `PATCH /api/v1/work-shifts/registrations/{registrationId}/review` | `{"status": "APPROVED", "version": <version B2>}` → sinh lịch làm việc |
| B4 | bất kỳ | provider → `GET /api/v1/caregivers/{caregiverId}/availability?from=D&to=D` | phải thấy khung 06:00–20:00 (giờ VN) |

Đặt nhiều ngày → tạo ca cho từng ngày (lặp B1–B3).

---

## 2. Luồng chính: đặt → duyệt → cọc → nhận ca → chăm sóc → hoàn tất

| # | Vai | Spec → endpoint | Body / kiểm tra |
|---|---|---|---|
| 2.1 | CUSTOMER | care-recipient → `POST /api/v1/care-recipients` | `{"fullName": "Bà Lan", "dateOfBirth": "1945-05-20", "gender": "FEMALE", "relationshipType": "CHILD", "addressLine": "45 Kim Mã", "district": "Ba Đình", "province": "Hà Nội", "allergies": "Hải sản"}` → **recipientId** |
| 2.2 | CUSTOMER | booking → `POST /api/v1/bookings/quotes` | body như 2.3 → xem `occurrences[].priceAmount`, `totalAmount` (2 giờ × 100.000 = 200.000/buổi). Không ghi DB |
| 2.3 | CUSTOMER | booking → `POST /api/v1/bookings` (header `Idempotency-Key: test-001` tùy chọn) | xem JSON dưới → `status = PENDING`, **bookingId**, `occurrences[0].id` = **occurrenceId** |
| 2.4 | CUSTOMER | `POST /api/v1/bookings` lần nữa, cùng `Idempotency-Key` | trả **cùng** booking (không tạo trùng) |
| 2.5 | MANAGER | booking → `GET /api/v1/bookings?status=PENDING&sort=createdAt,desc` | thấy booking + tên / SĐT khách; mỗi dòng có `totalCount`, `nextOccurrenceAt` |
| 2.6 | MANAGER | booking → `POST /api/v1/bookings/{bookingId}/accept` | body `{}` (đã chọn caregiver lúc đặt) hoặc `{"caregiverId": "<caregiverId>"}` → `AWAITING_DEPOSIT`, có `depositDueAt` |
| 2.7 | — | identity → `POST /api/v1/auth/service-token` | `{"clientId": "booking", "clientSecret": "<BOOKING_SERVICE_CLIENT_SECRET trong BookingService/.env>"}` → **SERVICE_TOKEN** (thay PaymentService xác nhận cọc) |
| 2.8 | SERVICE | booking → `POST /api/v1/bookings/{bookingId}/deposit-paid` | `{"paymentReference": "TEST-001"}` → `CONFIRMED`. Gọi bằng token khách → **403** |
| 2.9 | CAREGIVER | booking → `GET /api/v1/booking-occurrences?from=D&to=D` | trang `result.items` có buổi được giao |
| 2.10 | CAREGIVER | booking → `GET /api/v1/booking-occurrences/{occurrenceId}` | kèm `recipientSnapshot`, `specialNotes`, `services`; `customerPhone` bị che nếu buổi còn > 24h |
| 2.11 | CAREGIVER | care-recipient → `GET /api/v1/care-recipients/{recipientId}` | chỉ có `careSnapshot` (đang được phân công) |
| 2.12 | CAREGIVER | booking → `POST /api/v1/booking-occurrences/{occurrenceId}/acknowledge` | buổi `ACKNOWLEDGED` |

Body 2.2 / 2.3 (một buổi, ngày `D`):

```json
{
  "recipientId": "<recipientId>",
  "centerId": "<centerId>",
  "caregiverId": "<caregiverId>",
  "scheduleType": "ONE_TIME",
  "startDate": "D",
  "endDate": "D",
  "startTime": "08:00",
  "endTime": "10:00",
  "customerNote": "Bà cần hỗ trợ uống thuốc",
  "services": [{ "careServiceId": "<careServiceId>", "pricePlanId": "<pricePlanId>" }]
}
```

Nhiều buổi: `"scheduleType": "DAILY"` (thêm `endDate`) hoặc `"WEEKLY"` + `"daysOfWeek": [1, 3, 5]` (1 = Thứ Hai).

### 2.13–2.16 Ca chăm sóc (check-in / check-out)

Check-in chỉ được từ **30 phút trước giờ bắt đầu** tới trước giờ kết thúc. Booking phải đặt trước ≥ 12 giờ, nên muốn
test ngay thì dời giờ buổi về hiện tại trên **DB dev** (giống test tự động):

```sql
-- psql vào DB CARECONNECT_BOOKING (dev). Buổi bắt đầu 10 phút trước, dài 2 giờ.
UPDATE booking_occurrences
SET start_at = now() - interval '10 minutes', end_at = now() + interval '110 minutes'
WHERE occurrence_id = '<occurrenceId>';
```

| # | Vai | Endpoint | Body / kiểm tra |
|---|---|---|---|
| 2.13 | CAREGIVER | booking → `POST /api/v1/care-sessions` | `{"occurrenceId": "<occurrenceId>", "latitude": 21.03, "longitude": 105.82}` → **sessionId**, `tasks[]` (mỗi dịch vụ + thuốc trong khung giờ); booking `IN_PROGRESS` |
| 2.14 | CAREGIVER | booking → `POST /api/v1/care-sessions/{sessionId}/end` | còn việc PENDING → 409 `5805` (API cập nhật checklist là OPS-03 của người C) |
| 2.15 | MANAGER | booking → `POST /api/v1/care-sessions/{sessionId}/force-end` | `{"reason": "Test kết thúc ca"}` → session `ENDED`, việc PENDING → SKIPPED; buổi `COMPLETED` |
| 2.16 | CUSTOMER | booking → `GET /api/v1/bookings/{bookingId}` | hết buổi → `COMPLETED`, `completedAt`; cửa sổ PaymentService giả in `POST /api/v1/remaining-invoices` (hóa đơn phần còn lại qua outbox) |

Muốn qua 2.14 bằng caregiver: đặt việc thành DONE trên DB dev
`UPDATE care_tasks SET status = 'DONE' WHERE session_id = '<sessionId>';` rồi gọi lại `/end`.

### 2.17 Lịch sử

CUSTOMER / MANAGER → `GET /api/v1/bookings/{bookingId}/history` → các dòng `BOOKING_STATUS_CHANGED`
(PENDING → AWAITING_DEPOSIT → CONFIRMED → IN_PROGRESS → COMPLETED) xen kẽ sự kiện buổi (`OCCURRENCE_ACKNOWLEDGED`,
`OCCURRENCE_STATUS_CHANGED`…), sắp theo thời gian.

---

## 3. Các luồng phụ (mỗi luồng dùng một booking mới, làm lại 2.3 → 2.8)

Booking nhiều buổi dễ test hơn: `DAILY` 3 ngày (nhớ tạo ca làm việc cho 3 ngày).

### 3.1 Khách hủy một buổi có xem trước tiền hoàn (BK-03)

| # | Vai | Endpoint | Body / kiểm tra |
|---|---|---|---|
| 1 | CUSTOMER | `POST /api/v1/bookings/{bookingId}/cancellation-previews` | `{"occurrenceIds": ["<id buổi 2>"]}` → `items[].rule` (stub: ≥ 72h `FULL` 100%, 24–72h `TIER_24_72` 50%, < 24h 0%), `totalRefund`, **previewToken** (hạn 10 phút) |
| 2 | CUSTOMER | `POST /api/v1/bookings/{bookingId}/cancel` | `{"reason": "Gia đình đổi kế hoạch", "occurrenceIds": ["<id buổi 2>"], "previewToken": "<previewToken>"}` → buổi 2 `CANCELLED` (`cancelledByParty = CUSTOMER`), booking vẫn `CONFIRMED`, `refund.totalRefund`; stub in `POST /api/v1/refunds` |
| 3 | CUSTOMER | gọi lại bước 2 | 409 `5150` (buổi đã hủy) |
| 4 | CUSTOMER | `cancel` không có `occurrenceIds` | hủy mọi buổi còn lại → booking `CANCELLED` |

Thử lỗi: booking chưa cọc (PENDING) mà gửi `occurrenceIds` → 409 `5151`; id buổi của booking khác → 400 `5153`;
token sai / quá 10 phút → 409 `5159`.

### 3.2 Trung tâm hủy, từ chối (BK-04)

- MANAGER `POST /api/v1/bookings/{bookingId}/reject` `{"reason": "Không đủ nhân sự"}` — chỉ booking `PENDING` → `REJECTED`.
- MANAGER `POST /api/v1/bookings/{bookingId}/cancel` `{"reason": "Caregiver ốm"}` — booking đã cọc → mọi buổi chưa diễn
  ra `CANCELLED` (`cancelledByParty = CENTER`), hoàn `CENTER_CANCEL`. Manager trung tâm khác → 404.
- SERVICE `POST /api/v1/bookings/system-cancellations`:
  - `{"reason": "CENTER_LOCKED", "centerId": "<centerId>", "note": "Test khóa"}` → hủy + hoàn tiền mọi booking còn giữ
    chỗ; gọi lần 2 → `affectedBookings = 0`.
  - `{"reason": "CAREGIVER_DEACTIVATED", "caregiverId": "<caregiverId>"}` → không hủy, buổi tương lai có
    `needsReassignment = true` (xem `GET /api/v1/bookings/{id}`).

### 3.3 Đổi caregiver (BK-05)

Tạo caregiver thứ hai (A6–A8 + ca làm việc B1–B3), rồi MANAGER:
- `PATCH /api/v1/bookings/{bookingId}/occurrences/{occurrenceId}/caregiver` `{"caregiverId": "<caregiver 2>"}` → đổi một buổi;
- `PATCH /api/v1/bookings/{bookingId}/caregiver` `{"caregiverId": "<caregiver 2>"}` → đổi mọi buổi chưa diễn ra.

Kiểm tra `GET /api/v1/bookings/{bookingId}/history` có `CAREGIVER_CHANGED` với `oldCaregiverId` / `newCaregiverId`;
`GET /api/v1/bookings?caregiverId=<caregiver 2>` thấy booking. Caregiver 2 không có ca làm việc → 409 `5226`; trùng
giờ booking khác → 409 `5215`.

### 3.4 Khách vắng mặt / buổi bị bỏ (OPS-06)

Dời buổi về "đã bắt đầu 10 phút trước" bằng SQL ở §2.13–2.16, rồi:

| # | Vai | Endpoint | Kiểm tra |
|---|---|---|---|
| 1 | CAREGIVER | `POST /api/v1/booking-occurrences/{occurrenceId}/no-show-reports` `{"note": "Gọi 3 lần, chờ 30 phút không ai mở cửa"}` | buổi **vẫn** `SCHEDULED/ACKNOWLEDGED`, có `noShowReportedAt`; ghi chú < 10 ký tự → 400 `5812` |
| 2a | MANAGER | `POST /api/v1/booking-occurrences/{occurrenceId}/no-show-reports/decision` `{"approved": true}` | `CUSTOMER_NO_SHOW` (khách chịu phí) |
| 2b | MANAGER | như trên với `{"approved": false, "note": "Caregiver đến trễ"}` | `MISSED`, `missedReason = CAREGIVER`, stub nhận hoàn tiền `REFUND:CAREGIVER_MISSED:<occurrenceId>` |

**Job MISSED** (không báo vắng): dời buổi về bắt đầu 40 phút trước rồi chờ tối đa `BOOKING_MISSED_SCAN_INTERVAL`
(5 phút) → buổi `MISSED` + hoàn tiền tự động. Muốn nhanh: đặt `BOOKING_MISSED_SCAN_INTERVAL=30s` trong
`BookingService/.env` rồi khởi động lại Booking.

### 3.5 Danh sách, lọc, phân trang (BK-06)

- `GET /api/v1/bookings?sort=startDate,asc&page=0&size=10` · `?keyword=<6 ký tự cuối mã booking>` · `?status=CONFIRMED`
  · `?from=2026-11-01&to=2026-11-30`. `sort=customerId,asc` → 400 `5112`.
- `GET /api/v1/bookings/{bookingId}/occurrences?status=CANCELLED&page=0&size=20`.
- CAREGIVER `GET /api/v1/booking-occurrences?page=0&size=20` (không bắt buộc `from` / `to`).

---

## 4. Lỗi thường gặp khi test

| Hiện tượng | Nguyên nhân / cách xử lý |
|---|---|
| 503 `5154` khi tạo / hủy booking | Chưa chạy `payment_stub.py 8087` (§0.1) |
| 503 `5501` hoặc CareRecipient `2700` | Thiếu / sai client token SERVICE (`IDENTITY_SERVICE_CLIENTS` ↔ `*_SERVICE_CLIENT_*`); khởi động lại Identity sau khi sửa `.env` |
| 409 `5226` khi đặt / duyệt | Caregiver chưa có ca làm việc được duyệt phủ giờ buổi (§1.B) hoặc đang nghỉ |
| 400 `5214` | Buổi đầu phải cách hiện tại ≥ 12 giờ (`BOOKING_MIN_LEAD_TIME`) |
| 409 `5228` | Khách đang giữ quá 3 booking PENDING / AWAITING_DEPOSIT — hủy bớt |
| 409 `5157` | Khách có hóa đơn quá hạn (stub luôn trả "không nợ" nên ít gặp) |
| 400 `CAREGIVER_SERVICE_NOT_SUPPORTED` | Caregiver chưa được gán dịch vụ (A7) |
| 403 `5007` khi manager gọi | Token manager không có `center_id` — tài khoản manager phải tạo kèm `centerId` (A4a), login lại |
| 404 khi caregiver xem hồ sơ người bệnh | Booking chưa `CONFIRMED` hoặc caregiver không được giao buổi đó |
| 401 khắp nơi sau ~15 phút | Access token hết hạn → mobile login lại, Authorize lại |
| Đăng nhập web không nhận cookie | Web phải gửi header `X-Client-Type: web` — trên Swagger cứ dùng mobile login |
