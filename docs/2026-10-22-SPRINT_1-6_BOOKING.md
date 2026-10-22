# KẾT QUẢ — Người B (Booking & vận hành): Sprint 1 → 6

> Ngày làm: **2026-10-22**. Trạng thái: **HOÀN THÀNH** phần việc của [Người B] trong `docs/WORK_PLAN_B_BOOKING.md`
> (trừ phần phụ thuộc PaymentService / thông báo của người khác — §6). **Chưa commit** (chờ review).
> Quy ước: `AGENTS.md` gốc + `<Service>/AGENTS.md`. Checklist tiến độ: `docs/PLAN.md` (đã đánh `[x]` + ngày).

---

## 1. Tóm tắt

| Sprint | Hạng mục | Service chính | Trạng thái |
|---|---|---|---|
| S1 | IAM-01 token SERVICE (client-credentials) | Identity | ✅ |
| S1 | GW-01 route Payment, IPN / return VNPay public, SSE | ApiGateway | ✅ |
| S1 | BK-07 phạm vi occurrence + `caregiver-access` (SEC-03, SEC-09), BK-08 tách `BookingOccurrenceController` | Booking | ✅ |
| S1 | CAT-01 | CareCatalog | ✅ |
| Dọn | Hợp nhất API theo role (bỏ `/center/**`, `/admin/**`, `/caregiver/**`, `/internal/**`) ở **mọi service**; bỏ `@Operation`; đổi tên method theo quy ước | Tất cả | ✅ |
| S2–S3 | CAT-02, PRV-01, PRV-03, BK-01 (báo giá), OPS-01 (nhận ca), OPS-02 / OPS-04 (check-in / check-out, outbox, chốt booking) | Catalog, Provider, Booking | ✅ |
| S4 | PRV-02 lịch rảnh + ngày nghỉ, BK-02 kiểm tra khi đặt / duyệt, OPS-06 MISSED + báo khách vắng | Provider, Booking | ✅ |
| S5 | SRCH-01 tìm caregiver, SRCH-02 trung tâm cho khách, BK-03 khách hủy + preview | Provider, Booking | ✅ |
| S6 | BK-04 trung tâm / hệ thống hủy, BK-05 lịch sử, BK-06 lọc / sắp xếp / phân trang, BK-09 chống trùng tầng DB + scheduler nhiều instance | Booking | ✅ |
| S6 | INF-01 tài liệu chung, Docker, e2e | gốc repo | ✅ (§5) |

---

## 2. Chi tiết theo sprint

### 2.1 Sprint 1 + dọn quy ước
- **IAM-01** — `POST /api/v1/auth/service-token` (`clientId`, `clientSecret` → `accessToken`, `expiresInSeconds`;
  `roles=["SERVICE"]`, `sub` = `client_uuid`, không refresh). `ServiceClient extends BaseEntity` (bảng `service_clients`,
  unique một phần trên `client_id`), seed từ `IDENTITY_SERVICE_CLIENTS = <clientId>:<bcrypt>,...`
  (`scripts/New-ServiceClient.ps1`). Mẫu phía gọi: `client/ServiceTokenProvider` (cache tới `exp − 60s`) +
  `client/ServiceTokenInterceptor`, chỉ gắn vào Feign client cần quyền SERVICE.
- **GW-01** — route `/api/v1/payments/**`, `/refunds`, … tới PaymentService; GET IPN / return VNPay public; SSE qua
  `handler/SseProxyHandler`; xóa header `X-Service-*` client gửi.
- **Hợp nhất API theo role** (người dùng chọn "Tất cả service", "Xóa luôn" path cũ): Booking một bộ
  `/api/v1/bookings/**` + `/api/v1/booking-occurrences/**` phân phạm vi bằng `@CurrentRoles` / `@CurrentCenterId`;
  CareRecipient trả `careSnapshot` cho caregiver được phân công (hỏi Booking bằng token SERVICE).

### 2.2 Sprint 2–3
- **BK-01** — `POST /bookings/quotes`, giá từng buổi từ Catalog (`BookingPriceCalculator`), không tin giá từ client;
  `GET /bookings/{id}/payment-context` (SERVICE) cho Payment.
- **OPS-01** — `POST /booking-occurrences/{id}/acknowledge`; trạng thái buổi `ACKNOWLEDGED`, bảng `booking_events`.
- **OPS-02 / OPS-04** — `CareSession` + checklist `CareTask`; `/api/v1/care-sessions` (check-in, `/end`, `/force-end`);
  `BookingLifecycleService` (state machine duy nhất, `finalizeIfDone`); **transactional outbox** `booking_outbox`
  + `BookingOutboxScheduler` (retry backoff, claim lease) gửi hóa đơn REMAINING sang Payment.

### 2.3 Sprint 4
- **PRV-02** (Provider) — ngày nghỉ caregiver, `GET /caregivers/{id}/availability`, `POST /caregivers/availability-checks`
  (SERVICE, C5.2); hủy ca làm việc có booking → 409.
- **BK-02** — khi tạo / duyệt / đổi caregiver: khách nợ quá hạn → 409 `5157` (Payment lỗi → 503, fail-closed); quá
  `BOOKING_MAX_PENDING_PER_CUSTOMER` booking chờ → 409 `5228`; caregiver không có lịch / đang nghỉ → 409 `5226`;
  `POST /booking-occurrences/busy-caregivers` (SERVICE, C5.1) cho Provider.
- **OPS-06** — `POST /booking-occurrences/{id}/no-show-reports` (CAREGIVER, trong `[start, end + 60m)`, buổi chưa đổi
  trạng thái) và `/no-show-reports/decision` (CENTER_MANAGER: xác nhận → `CUSTOMER_NO_SHOW`; bác → `MISSED` + hoàn
  tiền `CAREGIVER_MISSED`). `OccurrenceMissedScheduler`: chưa check-in sau `start + 30m`, không báo vắng → `MISSED` +
  hoàn tiền + chốt booking; báo vắng chưa quyết định sau `end + 24h` → nhắc manager một lần.

### 2.4 Sprint 5
- **SRCH-01 / SRCH-02** (Provider) — `GET /caregivers/search` (giao tập dịch vụ, lọc, lịch trống qua C5.1, sắp xếp /
  phân trang trong bộ nhớ, > 500 ứng viên → 400), `GET /caregivers/{id}/public`, `GET /care-centers/public[/{id}]`.
- **BK-03** — `POST /bookings/{id}/cancellation-previews` (Payment C2.2 tính tiền hoàn từng buổi, `previewToken` HMAC
  hạn 10 phút, không ghi DB); `POST /bookings/{id}/cancel` `{reason, occurrenceIds?, previewToken?}` — hủy cả booking
  hoặc từng buổi, booking chưa cọc chỉ hủy toàn bộ, số tiền đổi sau preview → 409 `5159`; hoàn tiền qua outbox
  (`REFUND`, khóa idempotency theo tập buổi). Bỏ `RefundPolicy` cũ (Booking không tự tính %).

### 2.5 Sprint 6
- **BK-04** — manager hủy (hoàn `CENTER_CANCEL`); `POST /bookings/system-cancellations` (SERVICE):
  `CENTER_LOCKED` hủy + hoàn `CENTER_LOCKED`, mỗi booking một transaction, idempotent; `CAREGIVER_DEACTIVATED` không
  hủy, đánh dấu `needs_reassignment` (đổi caregiver thì tự bỏ cờ).
- **BK-05** — `GET /bookings/{id}/history` gộp `booking_status_history` + `booking_events`; mỗi buổi đổi caregiver ghi
  `CAREGIVER_CHANGED`; đổi từng buổi mà mọi buổi tương lai đã thuộc người mới → cập nhật caregiver cấp booking; lọc
  danh sách theo caregiver khớp cả caregiver được giao riêng một buổi.
- **BK-06** — `GET /bookings?keyword=&sort=` (whitelist `createdAt` / `startDate`, sai → 400 `5112`), mỗi dòng có
  `nextOccurrenceAt`, `completedCount`, `totalCount`; `/bookings/{id}/occurrences` và `/booking-occurrences` phân trang
  (bỏ giới hạn 62 ngày).
- **BK-09** — exclusion constraint `ex_booking_occurrences_caregiver_slot` (btree_gist, `tstzrange '[)'`) → 409 `5215`;
  scheduler hết hạn / MISSED / outbox lấy khóa `FOR UPDATE SKIP LOCKED` (`util/LockHints`) — instance khác đang giữ
  dòng thì bỏ qua, không chờ, không xử lý trùng.

---

## 3. Thay đổi schema (BookingService, áp theo thứ tự)

| Change script | Nội dung |
|---|---|
| `2026-10-22_occurrence_price.sql` | giá từng buổi |
| `2026-10-22_occurrence_acknowledge.sql` | `acknowledged_at`, `booking_events` |
| `2026-10-22_care_sessions_outbox.sql` | `care_sessions`, `care_tasks`, `booking_outbox` (+ `idempotency_key` UNIQUE, `REFUND`) |
| `2026-10-22_occurrence_no_show.sql` | `missed_reason`, `no_show_*`, sự kiện `NO_SHOW_REPORTED` |
| `2026-10-22_occurrence_cancellation.sql` | `cancelled_at`, `cancelled_by_party`, `needs_reassignment` |
| `2026-10-22_occurrence_slot_exclusion.sql` | extension `btree_gist` + exclusion constraint (dừng nếu dữ liệu cũ đã trùng) |

`schema/booking.sql` đã gộp đủ. Provider có thêm `2026-10-22_caregiver_time_offs.sql`,
`2026-10-22_caregiver_schedule_status_d11.sql`.

---

## 4. Sửa lỗi bảo mật / sai lệch phát hiện khi rà soát

| Vấn đề | Sửa |
|---|---|
| `POST /bookings/{id}/deposit-paid` cho mọi user đăng nhập → khách tự đánh dấu đã cọc | chỉ `hasRole('SERVICE')` |
| CareRecipient `GET /{id}/snapshot`, `/{id}/access` nhận id / userId tùy ý cho mọi user (IDOR hồ sơ người bệnh) | chỉ `SERVICE`; Booking gọi bằng token SERVICE |
| Khách chọn caregiver khi đặt lịch → 503 (Provider đã siết `GET /caregivers/{id}`) | Booking tra caregiver bằng token SERVICE (`ProviderServiceClient`), mở bọc lỗi circuit breaker để giữ 404 |
| `PROVIDER_MEDIA_URL` mặc định trỏ nhầm 8085 | `http://localhost:8080` (gateway) |
| Tài liệu cũ: `InternalPathBlockFilter`, "gateway cắt prefix", `*_INTERNAL_SECRET` | sửa ApiGateway AGENTS, Identity / CareRecipient README |

---

## 5. Kiểm thử

| Service | Kết quả |
|---|---|
| BookingService (H2) | 90 test pass (1 bỏ qua — chỉ PostgreSQL) |
| BookingService (PostgreSQL 17 thật, `-Dbooking.testing.postgres=true`) | **91/91 pass** — gồm exclusion constraint và SKIP LOCKED hai instance |
| ProviderService | 57/57 |
| CareRecipientService | 87/87 |
| e2e toàn hệ thống (`scripts/e2e/Run-E2E.ps1`, 7 app + PaymentService giả) | **84/84 pass** — phase a 19, b 14, c 15, e 30, d 4, f 2 (`E2E PASSED`) |

e2e chạy trên PostgreSQL / Redis thật trong compose project riêng (không đụng DB dev), client token SERVICE sinh mỗi lần
chạy. Sửa khi chạy thật: `payment_stub.py` nghe cả IPv4 + IPv6 loopback (Windows `localhost` thử `::1` trước);
phase b gửi `X-Client-Type: web` (Identity gộp luồng web / mobile từ 19/10 — thiếu header thì coi là mobile).

---

## 6. Chưa làm / thuộc người khác

- **PaymentService** (người A): C2.2 / C2.3 / C2.5 mới thử với stub; `deposit-paid` sẽ đổi tên `deposit-confirmations` (PAY-08).
- **Thông báo** (người C, NTF-01 / NTF-02): các chỗ `TODO(NTF-0x)`.
- **Người C**: Provider gọi `system-cancellations` khi khóa trung tâm / caregiver (vòng đời trung tâm), Provider
  `ddl-auto: validate` (PRV-06), CareRecipient `/batch` (CR-01), MediaService đổi dải lỗi 7xxx + tài liệu (MED-01).
- Không thêm cột `active_slot` (BK-09): booking rời trạng thái giữ chỗ luôn kéo buổi chưa kết thúc về `CANCELLED`, nên
  constraint chỉ cần trạng thái buổi.

## 7. Cấu hình khi triển khai

- Đăng ký client `booking`, `provider`, `care-recipient`, `payment` trong `IDENTITY_SERVICE_CLIENTS`; mỗi service đặt
  `<SERVICE>_SERVICE_CLIENT_ID/SECRET`.
- `BOOKING_CANCELLATION_PREVIEW_SECRET` dùng chung mọi instance (trống = khóa ngẫu nhiên từng tiến trình, chỉ dev).
- URL mới: `BOOKING_PAYMENT_URL`, `BOOKING_CARE_CATALOG_URL`, `PROVIDER_BOOKING_URL`, `PROVIDER_CARE_CATALOG_URL`
  (đều trỏ gateway).
- FE: danh sách buổi trả trang (`result.items`); body hủy có `occurrenceIds`, `previewToken`; manager hủy PENDING
  phải dùng reject; web login gửi `X-Client-Type: web`.
