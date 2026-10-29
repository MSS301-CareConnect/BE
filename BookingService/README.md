# CareConnect BookingService

Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.3. Port mặc định **8085**. Service sở hữu booking (đặt lịch chăm sóc),
từng buổi chăm sóc (`booking_occurrences`), ca chăm sóc thực tế (`care_sessions` + checklist `care_tasks`), dịch vụ trong
booking, lịch sử (`booking_status_history`, `booking_events`) và outbox gửi việc sang PaymentService. Không sở hữu tài
khoản (Identity), hồ sơ người bệnh (CareRecipient), trung tâm / caregiver (Provider), danh mục & giá (CareCatalog), tiền
(Payment — số tiền cọc / hoàn / hóa đơn do PaymentService tính).

Quy ước chung: `../AGENTS.md`; phần riêng: `AGENTS.md`; thiết kế + quyết định: `../docs/2026-10-09-PLAN_BOOKING.md`,
`../docs/WORK_PLAN_B_BOOKING.md`.

## Chạy local

```powershell
Copy-Item .env.example .env          # điền DB password, JWT issuer/JWKS, URL gateway, client id/secret SERVICE
docker compose up -d                 # PostgreSQL 127.0.0.1:5445 (DB CARECONNECT_BOOKING)
Get-Content -Raw schema/booking.sql | docker compose exec -T postgres psql -U <BOOKING_DB_USERNAME> -d CARECONNECT_BOOKING -v ON_ERROR_STOP=1   # chỉ DB mới
.\scripts\Start-Dev.ps1              # nạp .env rồi spring-boot:run
.\mvnw.cmd test                      # H2 + stub HTTP (JWKS, Identity, CareRecipient, Provider, Catalog, Payment)
```

- Không Redis, không Flyway; `ddl-auto: validate`; JVM chạy UTC, thời điểm lưu `timestamptz`, lịch sinh theo `BOOKING_TIMEZONE`.
- `schema/booking.sql` cần extension `btree_gist` (BK-09, exclusion constraint chống trùng lịch caregiver).
- DB đã có dữ liệu: áp các change script **theo thứ tự** trong `schema/changes/` (cùng ngày thì theo thứ tự dưới đây):
  `2026-10-22_occurrence_price.sql` → `2026-10-22_occurrence_acknowledge.sql` → `2026-10-22_care_sessions_outbox.sql` →
  `2026-10-22_occurrence_no_show.sql` → `2026-10-22_occurrence_cancellation.sql` → `2026-10-22_occurrence_slot_exclusion.sql`
  (script cuối dừng lại nếu dữ liệu cũ đã có buổi trùng giờ của cùng caregiver).
- Test trên PostgreSQL thật: container riêng (vd `docker run -d --name booking-test-pg -e POSTGRES_PASSWORD=booking_test_only -p 127.0.0.1:55496:5432 postgres:17`)
  rồi `.\mvnw.cmd test -Dbooking.testing.postgres=true` (mỗi lần chạy tạo schema riêng, áp `schema/booking.sql` + `validate`;
  bật thêm test exclusion constraint của BK-09).
- Gọi service khác qua **ApiGateway** (`BOOKING_*_URL` = gateway). Lời gọi thay mặt người dùng chuyển tiếp bearer của
  request; lời gọi máy (Provider kiểm lịch rảnh C5.2, Payment preview / hoàn tiền / hóa đơn, tra hồ sơ caregiver khi khách chọn người, CareRecipient `/access` + `/snapshot`) dùng
  token SERVICE: `client/ServiceTokenProvider` (cache, làm mới khi còn < 60s) + `client/ServiceTokenInterceptor`, env
  `BOOKING_SERVICE_CLIENT_ID` / `BOOKING_SERVICE_CLIENT_SECRET` (đăng ký ở `IDENTITY_SERVICE_CLIENTS`); thiếu → 503 `5501`.

## Trạng thái booking

```
PENDING ──accept──▶ AWAITING_DEPOSIT ──deposit-paid──▶ CONFIRMED ──check-in buổi đầu──▶ IN_PROGRESS ──hết buổi──▶ COMPLETED
   ├─reject──▶ REJECTED              ├─quá hạn cọc──▶ EXPIRED      │                         │
   ├─quá hạn duyệt──▶ EXPIRED         │                             │                         │
   └─hủy──▶ CANCELLED ◀───────────────┴─hủy (cả booking) ◀──────────┴─ không còn buổi, không buổi nào được phục vụ
```

- `BookingAction` là state machine duy nhất; mọi đổi trạng thái qua `BookingLifecycleService` (ghi `booking_status_history`).
- Hết buổi (`finalizeIfDone`): có buổi COMPLETED / CUSTOMER_NO_SHOW → COMPLETED + outbox hóa đơn REMAINING; ngược lại CANCELLED.
- Hủy / từ chối / hết hạn → các buổi chưa bắt đầu thành `CANCELLED` (`cancelled_at`, `cancelled_by_party`), giải phóng caregiver.
- Buổi: `SCHEDULED → ACKNOWLEDGED → IN_PROGRESS → COMPLETED`; chưa bắt đầu → `CANCELLED | MISSED | CUSTOMER_NO_SHOW`.

## API (path ở service = path ở gateway)

Một API cho mọi role (2026-10-22 — bỏ path tách theo role `/bookings/center/**`, `/bookings/admin/**`, `/internal/**`).
Phạm vi do service chọn theo role: ADMIN mọi booking; CENTER_MANAGER booking cùng `center_id` trong token; CUSTOMER booking
của mình; CAREGIVER buổi được giao. Ngoài phạm vi → 404.

| Role | Method + path | Ghi chú |
|---|---|---|
| CUSTOMER, CENTER_MANAGER, ADMIN | `GET /api/v1/bookings?status=&recipientId=&centerId=&customerId=&caregiverId=&from=&to=&keyword=&sort=&page=&size=` | BK-06. `sort` chỉ `createdAt` / `startDate` + `asc` / `desc` (mặc định `createdAt,desc`; khác → 400 `5112`); `keyword` = mã booking (chứa, không phân biệt hoa thường); `from`/`to` lọc khoảng giao với `[startDate, endDate]`; `caregiverId` khớp cả caregiver cấp booking lẫn caregiver được giao riêng một buổi. Mỗi dòng có `nextOccurrenceAt`, `completedCount`, `totalCount`. `centerId` / `customerId` chỉ có tác dụng với ADMIN |
| CUSTOMER, CENTER_MANAGER, ADMIN | `GET /api/v1/bookings/{id}` | |
| CUSTOMER, CENTER_MANAGER, ADMIN | `GET /api/v1/bookings/{id}/occurrences?status=&page=&size=` | **Phân trang** (`result.items`, mặc định size 50, tối đa 100) — BK-06 |
| CUSTOMER, CENTER_MANAGER, ADMIN | `GET /api/v1/bookings/{id}/history?page=&size=` | BK-05: gộp `booking_status_history` (`eventType = BOOKING_STATUS_CHANGED`) và `booking_events` (nhận ca, đổi trạng thái buổi, `CAREGIVER_CHANGED` kèm `oldCaregiverId` / `newCaregiverId`, `NO_SHOW_REPORTED`), sắp theo thời gian |
| CUSTOMER | `POST /api/v1/bookings` | Header tùy chọn `Idempotency-Key` (≤100 ký tự `[A-Za-z0-9._-]`): cùng key + cùng nội dung → trả booking cũ; khác nội dung → 409 `5102`. BK-02: khách nợ hóa đơn quá hạn → 409 `5157`; giữ quá `BOOKING_MAX_PENDING_PER_CUSTOMER` booking chờ → 409 `5228`; caregiver không có lịch làm việc / đang nghỉ → 409 `5226` |
| CUSTOMER | `POST /api/v1/bookings/quotes` (body như tạo booking) | Báo giá tạm tính (BK-01), không ghi DB |
| CUSTOMER | `POST /api/v1/bookings/{id}/cancellation-previews` `{occurrenceIds?}` | BK-03: tiền hoàn / phí từng buổi do PaymentService tính (`items[].rule`), `totalRefund`, `previewToken` (HMAC, hạn `BOOKING_CANCELLATION_PREVIEW_TTL` = 10 phút). Không ghi DB. Booking chưa cọc → toàn 0, `rule = NOT_PAID` |
| CUSTOMER, CENTER_MANAGER | `POST /api/v1/bookings/{id}/cancel` `{reason, occurrenceIds?, previewToken?}` | BK-03 / BK-04. `occurrenceIds` rỗng = mọi buổi chưa diễn ra. Chưa cọc → chỉ hủy cả booking (hủy lẻ → 409 `5151`). Đã cọc → buổi chọn CANCELLED + xếp hoàn tiền qua outbox (`CUSTOMER_CANCEL` / `CENTER_CANCEL`), không còn buổi nào → đóng booking. Buổi đã bắt đầu / có session / không thuộc booking → 409 `5150` / 400 `5153`. `previewToken` không khớp số tiền tính lại → 409 `5159`. Response kèm `refund {reason, occurrenceIds, totalRefund}`. Manager không hủy được PENDING (dùng reject) |
| CENTER_MANAGER | `POST /api/v1/bookings/{id}/accept` `{caregiverId?}` · `POST /api/v1/bookings/{id}/reject` `{reason}` | Accept kiểm lịch rảnh caregiver ở Provider (C5.2) |
| CENTER_MANAGER | `PATCH /api/v1/bookings/{id}/caregiver` `{caregiverId}` · `PATCH /api/v1/bookings/{id}/occurrences/{occurrenceId}/caregiver` | Chỉ buổi chưa bắt đầu; kiểm trùng + lịch rảnh; mỗi buổi đổi người ghi `CAREGIVER_CHANGED`; đổi từng buổi mà mọi buổi tương lai đã thuộc người mới → cập nhật luôn caregiver cấp booking; bỏ cờ `needsReassignment` |
| SERVICE | `POST /api/v1/bookings/system-cancellations` `{centerId?, caregiverId?, reason, note?}` | BK-04, Provider gọi. `CENTER_LOCKED` (cần `centerId`): hủy mọi buổi chưa check-in của booking còn giữ chỗ; đã cọc → hoàn `CENTER_LOCKED`. `CAREGIVER_DEACTIVATED` (cần `caregiverId`): không hủy, đánh dấu buổi tương lai `needsReassignment = true`. Mỗi booking một transaction, idempotent; trả `{affectedBookings, affectedOccurrences}` |
| SERVICE | `GET /api/v1/bookings/caregiver-access?caregiverUserId=&recipientId=` | `{allowed}` theo từng buổi (D5). Token người dùng (kể cả ADMIN) → 403 |
| SERVICE | `GET /api/v1/bookings/{id}/payment-context` | C3.2 cho PaymentService: tổng tiền, hạn cọc, từng buổi (`priceAmount`, `status`, `hasSession`) |
| SERVICE | `POST /api/v1/bookings/{id}/deposit-paid` `{paymentReference?}` | PaymentService xác nhận đã cọc (người dùng → 403); idempotent. TODO(PAY-08) — đổi tên thành `deposit-confirmations` |
| CAREGIVER | `GET /api/v1/booking-occurrences?from=&to=&status=&page=&size=` | **Phân trang**, `from` / `to` tùy chọn (bỏ giới hạn 62 ngày — BK-06). Buổi được giao cho chính buổi đó của booking đã xác nhận; không kèm snapshot |
| Mọi role (theo phạm vi) | `GET /api/v1/booking-occurrences/{id}` | ADMIN / SERVICE mọi buổi; manager buổi của trung tâm mình; khách buổi của booking mình; caregiver buổi được giao (snapshot người bệnh chỉ khi còn quyền — SEC-09). Kèm `services[]`, `specialNotes`, `customerPhone` (caregiver: che giữa trừ khi buổi bắt đầu trong 24h), `sessionId`, `missedReason`, `noShow*` |
| SERVICE | `POST /api/v1/booking-occurrences/busy-caregivers` `{caregiverIds ≤200, slots ≤60}` | C5.1 cho Provider: caregiver đã có buổi còn hoạt động trùng một trong các khoảng → `{busyCaregiverIds}` |
| CAREGIVER | `POST /api/v1/booking-occurrences/{id}/acknowledge` | Nhận ca (OPS-01) |
| CAREGIVER | `POST /api/v1/booking-occurrences/{id}/no-show-reports` `{note}` (10–1000 ký tự) | OPS-06: báo khách vắng trong `[startAt, endAt + 60 phút)`, buổi chưa check-in; buổi **chưa đổi trạng thái**, job MISSED bỏ qua buổi này. Lỗi `5812` / `5814` / `5817` |
| CENTER_MANAGER | `POST /api/v1/booking-occurrences/{id}/no-show-reports/decision` `{approved, note?}` | OPS-06: `true` → `CUSTOMER_NO_SHOW` (khách chịu phí, tính ở hóa đơn REMAINING); `false` → `MISSED` (`missedReason = CAREGIVER`) + hoàn tiền `CAREGIVER_MISSED`. Rồi chốt booking nếu hết buổi. Chưa báo → 409 `5813` |
| CAREGIVER | `POST /api/v1/care-sessions` `{occurrenceId, latitude?, longitude?}` | Check-in (OPS-02) |
| CAREGIVER | `POST /api/v1/care-sessions/{id}/end` `{latitude?, longitude?}` | Check-out (OPS-04) |
| CENTER_MANAGER | `POST /api/v1/care-sessions/{id}/force-end` `{reason}` | Buộc kết thúc ca caregiver quên đóng |
| CAREGIVER, CUSTOMER, CENTER_MANAGER, ADMIN, SERVICE | `GET /api/v1/care-sessions/{id}` | Theo phạm vi; ngoài phạm vi → 404 `5800` |

`caregiverId` trong request / response = `caregiver_profiles.caregiver_id` của ProviderService; `caregiverUserId` = user
Identity của caregiver (lấy từ Provider, không nhận từ client).

### Job nền

| Job | Chu kỳ | Việc |
|---|---|---|
| `BookingExpiryScheduler` | `BOOKING_EXPIRY_SCAN_INTERVAL` | PENDING quá hạn duyệt / AWAITING_DEPOSIT quá hạn cọc → EXPIRED |
| `OccurrenceMissedScheduler` | `BOOKING_MISSED_SCAN_INTERVAL` (5m) | Buổi chưa check-in sau `startAt + BOOKING_MISSED_GRACE` (30m), không có báo vắng, booking CONFIRMED / IN_PROGRESS → MISSED + hoàn tiền `CAREGIVER_MISSED` + chốt booking. Báo vắng chưa quyết định sau `endAt + BOOKING_NO_SHOW_DECISION_REMINDER_AFTER` (24h) → nhắc manager một lần |
| `CareSessionOverdueScheduler` | `BOOKING_SESSION_OVERDUE_SCAN_INTERVAL` | Session còn mở quá giờ kết thúc + 60m → nhắc một lần |
| `BookingOutboxScheduler` | `BOOKING_OUTBOX_SCAN_INTERVAL` | Gửi lại việc sang Payment (`REMAINING_INVOICE`, `REFUND`) với backoff |

Mỗi job lấy lô id rồi xử lý từng dòng trong transaction riêng: khóa dòng bằng `FOR UPDATE SKIP LOCKED` (BK-09,
`util/LockHints`) — dòng đang bị instance khác giữ thì bỏ qua ngay, lượt quét sau xét lại — rồi **kiểm lại điều kiện**
sau khi khóa (mỗi booking chỉ EXPIRE / MISSED một lần); outbox claim thêm lease. Hoàn tiền / hóa đơn khử trùng ở Payment theo `Idempotency-Key` (`REFUND:<lý do>:<bookingId|occurrenceId>…`,
`REMAINING_INVOICE:<bookingId>`), cột `booking_outbox.idempotency_key` UNIQUE.

## Bảng mã lỗi chính (5000–5999)

| Code | Key | Ý nghĩa |
|---|---|---|
| 5003 | `INVALID_PAGE` | page < 0, size ngoài 1–100 |
| 5007 | `CENTER_SCOPE_REQUIRED` | Token manager không có `center_id` (403) |
| 5100 / 5101 | `BOOKING_NOT_FOUND` / `INVALID_BOOKING_TRANSITION` | Không thấy (404) / sai trạng thái (409) |
| 5102 / 5103 | `IDEMPOTENCY_KEY_REUSED` / `INVALID_IDEMPOTENCY_KEY` | |
| 5112 | `INVALID_SORT` | Sort ngoài whitelist (400) |
| 5150 / 5151 / 5153 | `OCCURRENCE_NOT_CANCELLABLE` / `CANCEL_SCOPE_NOT_ALLOWED` / `OCCURRENCE_NOT_IN_BOOKING` | Hủy buổi (BK-03) |
| 5154 | `PAYMENT_SERVICE_UNAVAILABLE` | Payment lỗi khi kiểm nợ / preview hoàn tiền (503) |
| 5157 | `CUSTOMER_HAS_OUTSTANDING_DEBT` | Khách nợ hóa đơn quá hạn (409) |
| 5159 | `CANCELLATION_PREVIEW_EXPIRED` | previewToken hết hạn / số tiền đã đổi (409) |
| 5160 | `SYSTEM_CANCELLATION_REASON_REQUIRED` | Thiếu `reason` ở system-cancellations |
| 5200–5214 | lịch | Thiếu field, ngày quá khứ, ca qua đêm, thứ trong tuần sai, quá số buổi / số ngày, báo trước quá ngắn… |
| 5215 / 5216 | `CAREGIVER_SCHEDULE_CONFLICT` / `RECIPIENT_SCHEDULE_CONFLICT` | Trùng lịch (409) — 5215 cũng là kết quả khi exclusion constraint ở DB chặn (BK-09) |
| 5217 / 5218 / 5221 / 5223 | buổi | Không thấy / không đổi được / sai trạng thái / booking chưa xác nhận |
| 5226 / 5228 | `CAREGIVER_NOT_AVAILABLE` / `TOO_MANY_PENDING_BOOKINGS` | BK-02 |
| 5229–5233 | busy-caregivers | Thiếu / quá số caregiver, slot |
| 5400–5408 | phân công / trung tâm | Caregiver khác trung tâm / chưa xác minh / không hoạt động / không làm được dịch vụ, trung tâm không hoạt động… |
| 5500 / 5501 / 5600 / 5700 | `IDENTITY_UNAVAILABLE` / `SERVICE_TOKEN_UNAVAILABLE` / `CARE_RECIPIENT_UNAVAILABLE` / `PROVIDER_UNAVAILABLE` | 503 |
| 5800–5805 | ca chăm sóc | Không thấy, check-in trùng, ngoài giờ, còn việc chưa xong… |
| 5812–5817 | báo vắng | `NO_SHOW_NOTE_REQUIRED`, `NO_SHOW_NOT_REPORTED`, `NO_SHOW_ALREADY_REPORTED`, `NO_SHOW_DECISION_REQUIRED`, `NO_SHOW_WINDOW_INVALID` |

Danh sách đầy đủ: `exception/ErrorCode.java`.

## Biến môi trường

Xem `.env.example` (chỉ tên + chú thích): `BOOKING_SERVER_PORT`, `BOOKING_SWAGGER_ENABLED`, `BOOKING_OPENAPI_SERVER_URL` ·
`BOOKING_DB_*` · `BOOKING_JWT_ISSUER`, `BOOKING_JWT_JWKS_URI` · `BOOKING_IDENTITY_URL`, `BOOKING_CARE_RECIPIENT_URL`,
`BOOKING_PROVIDER_URL`, `BOOKING_CARE_CATALOG_URL`, `BOOKING_PAYMENT_URL` (đều là gateway) · `BOOKING_SERVICE_CLIENT_ID`,
`BOOKING_SERVICE_CLIENT_SECRET` · `BOOKING_TIMEZONE`, `BOOKING_MAX_OCCURRENCES`, `BOOKING_MAX_SPAN_DAYS`,
`BOOKING_MIN_LEAD_TIME`, `BOOKING_ACCEPTANCE_TIMEOUT`, `BOOKING_DEPOSIT_TIMEOUT`, `BOOKING_EXPIRY_BATCH_SIZE`,
`BOOKING_EXPIRY_SCAN_INTERVAL` · `BOOKING_MAX_PENDING_PER_CUSTOMER` · `BOOKING_SESSION_*`, `BOOKING_MISSED_GRACE`,
`BOOKING_MISSED_SCAN_INTERVAL`, `BOOKING_NO_SHOW_DECISION_REMINDER_AFTER` · `BOOKING_COMPLAINT_OPEN_WINDOW`,
`BOOKING_COMPLAINT_CUSTOMER_ESCALATE_AFTER`, `BOOKING_ADMIN_USER_IDS` (admin nhận thông báo vận hành) ·
`BOOKING_OUTBOX_*` · `BOOKING_CANCELLATION_PREVIEW_SECRET` (khóa HMAC chung cho mọi instance; trống = khóa ngẫu nhiên
theo tiến trình, chỉ dùng dev), `BOOKING_CANCELLATION_PREVIEW_TTL`. `BOOKING_MAX_CAREGIVER_RANGE_DAYS` đã bỏ (BK-06);
`BOOKING_NO_SHOW_REPORT_WINDOW` đã bỏ (D22: báo vắng dùng chung mốc `BOOKING_MISSED_GRACE`).

Vận hành (Đợt 4): `GET /api/v1/booking-outbox` + `POST /{id}/requeue|skip` (ADMIN) xử lý việc gửi sang service khác
đã FAILED; `GET /api/v1/bookings/recipient-activity` (SERVICE) cho CareRecipientService kiểm booking chưa kết thúc;
`POST /api/v1/complaints/{id}/withdraw` (khách rút khiếu nại, D20).

Swagger: `http://localhost:8085/swagger-ui.html` (hoặc tổng hợp ở gateway). Health: `/actuator/health`.
Access token nên giữ 10–15 phút: service này xác minh offline, không nạp lại quyền theo thời gian thực.
