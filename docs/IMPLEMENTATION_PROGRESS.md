# CareConnect — Tiến độ triển khai sau khảo sát 2026-10-28

Nguồn: `docs/2026-10-28-FULLSTACK_GAP_ANALYSIS.md` (§7 mã vấn đề, §8.1 quyết định D15…D30, §9 kế hoạch).
Thứ tự đã duyệt: 0 → 1a → 1b → 1c → 2 → 3 → 4 → 5 → 6. Mỗi đợt: kế hoạch → BE → FE → test → commit (không push) → cập nhật file này.
Code FE nằm ở repo `FE-careconnect` (commit riêng repo đó).

---

## Đợt 0 — Gỡ rủi ro nhanh

### Kế hoạch

| Hạng mục | File chính | Ghi chú |
|---|---|---|
| M-01 Lọc bản nháp care report khỏi khách / manager | `BookingService/.../CareReportServiceImpl.java` (+ test) | Không đổi schema |
| M-02 `GET /caregivers` trả DTO công khai | `ProviderService/.../CaregiverController`, `CaregiverServiceImpl` (+ test, snapshot FE) | Đổi kiểu response của một endpoint đọc; FE chỉ dùng field công khai |
| H-14 Payment: handler `AccessDeniedException`, lỗi parse / thiếu tham số | `PaymentService/.../GlobalExceptionHandler.java` | |
| C-03 (hạ tầng) Bật PaymentService trong `compose.yaml`, volume trong `start.bat` | `compose.yaml`, `start.bat`, `DOCKER.md` | Không mở path Stripe (D16) |
| H-01 Xóa trang cọc giả `/customer/booking/deposit` | FE `features/customer/booking-deposit/*`, route, i18n | |
| D25 / H-03 Gỡ trang mock Caregiver (dashboard, history, shift-detail, active-session, care-report mock) + code chết `caregiver/schedule`, `caregiver/profile` | FE `features/caregiver/*`, `entities/care-session/*`, routes, i18n | Trang chủ caregiver → `/caregiver/visits` |
| H-02 Bỏ nút Bắt đầu / Hoàn thành ở lịch caregiver | FE `entities/provider/provider-schedule-page.tsx` | Lịch = lịch rảnh (D11), chỉ hủy |
| H-03 Nhãn mẫu dashboard admin | FE `features/admin/dashboard/*` | |
| D26 Ẩn menu Doanh thu, Chính sách, Chất lượng | FE `admin-navigation.constants.ts` | Route giữ nguyên |
| L-01 Link chết | FE `patient-sidebar.tsx`, customer bell, caregiver bell | Thông báo thật làm ở Đợt 3 |
| L-02 Code chết customer caregivers cũ, `AdminCentersPage`, `ManagerCaregiversPage` | FE | |

Rủi ro: xóa i18n đang được trang khác dùng (typecheck bắt được vì key có kiểu); test cũ khóa hành vi mock → xóa cùng feature.
Không có migration.

### Kết quả (2026-10-28)

> Theo yêu cầu của chủ dự án (2026-10-28): **không commit / push** — mọi thay đổi để ở working tree; commit message đề
> xuất ở cuối mục. Các file FE bị xóa đã được `git rm` (nằm trong index, chưa commit).

**Đã đóng:** M-01, M-02, H-14, H-01, H-02, H-03, L-01 (phần link chết), L-02 (phần customer caregivers cũ), C-03 (phần hạ
tầng Docker), D25, D26.

BE (`BE/`):
- `BookingService/.../CareReportServiceImpl.java` — `canReadReport`: bản nháp chỉ caregiver viết nó đọc được; danh sách
  của khách / manager / admin chỉ chứa report `SUBMITTED` (+ bản nháp của chính caregiver). Test bổ sung trong
  `BookingApiTests` (khách nhận 404 / danh sách rỗng khi report còn nháp, thấy sau khi nộp).
- `ProviderService` — `GET /api/v1/caregivers` trả `PageResponse<CaregiverCardResponse>` (thẻ công khai, thêm `centerId`,
  `centerName`, `bio`; ảnh lấy ngoài transaction). Test `publicProfileShows…` kiểm không lộ `userId`, ngày sinh, lý do
  xác minh, `createdBy`. README cập nhật.
- `PaymentService` — `GlobalExceptionHandler` theo mẫu Booking: `AccessDeniedException` → 403, body/tham số/header sai → 400,
  khóa lạc quan / vi phạm unique → 409 `CONCURRENT_UPDATE` (6014), lỗi DB → 503 (6015), key validation lạ → `INVALID_KEY`
  (6016); bỏ `APIResponse.error(...)` (rule #5 error handling). Test API của Payment chưa có hạ tầng (H2) → bổ sung ở Đợt 1a.
- `compose.yaml` bật `PaymentService/compose.stack.yaml`; `start.bat` tạo volume `paymentservice_payment_postgres`, kiểm
  `.env` của 8 service, in URL Payment; `DOCKER.md` cập nhật. Không mở path Stripe ở gateway (D16).

FE (`FE-careconnect/`):
- Xóa trang cọc giả `/customer/booking/deposit` (feature, route, i18n, test).
- Portal Caregiver chỉ còn trang thật (D25): xóa dashboard, history, shift-detail, active-session, care-report mẫu,
  `entities/care-session`, feature chết `caregiver/schedule`, `caregiver/profile` + i18n; trang chủ `/caregiver` →
  `/caregiver/visits`; menu: Buổi được giao · Lịch rảnh · Ca làm việc · Hồ sơ; bỏ chuông giả có chấm đỏ.
- Lịch rảnh: bỏ nút Bắt đầu / Hoàn thành (BE chỉ cho hủy) — test mới kiểm chỉ còn hủy kèm version + lý do.
- Dashboard admin: biểu đồ mẫu nằm dưới ghi chú `role='note'` "Bản xem thử · Kỳ báo cáo…". Menu admin ẩn Doanh thu,
  Chất lượng, Chính sách (route giữ nguyên).
- Customer: bỏ chuông no-op; link "Bảo mật" → `/account/security`; bỏ link `/customer/notifications` (404). Xóa trang
  tìm caregiver mock cũ (`caregivers-page.tsx`, `mock-caregivers.ts`, component/hook đi kèm).
- Contract: `GET /v1/caregivers` → `ProviderCaregiverCardPageResponse`; `ProviderCaregiverCardRecord` thêm `centerId`,
  `centerName`, `bio`; `pnpm api:generate`; danh mục khách và form đặt lịch dùng `caregiverId`.
- `ARCHITECTURE.md` cập nhật mục Caregiver portal, Admin menu, dashboard.

**Kiểm thử:** `mvnw test` BookingService ✅, ProviderService ✅, PaymentService ✅ · FE `pnpm check` ✅ (prettier, lint,
typecheck, vitest 66 file / 244 test, `node --test` 11/11, build). Số test FE giảm từ 290 do xóa test của các trang mock.

**Kiểm tay:**
1. Đăng nhập caregiver → vào `/caregiver` thấy "Buổi được giao"; menu 4 mục; `/caregiver/dashboard` → trang 404.
2. Caregiver tạo report nháp cho một phiên → khách của booking gọi `GET /api/v1/care-reports/{id}` nhận 404; nộp xong → 200.
3. Khách `GET /api/v1/caregivers?centerId=…` → item không có `userId`, `dateOfBirth`.
4. Khách gọi `GET /api/v1/payments/revenue` (ADMIN-only) → 403 thay vì 500.
5. `start.bat` → `payment-service` healthy (cần `PaymentService/.env` + client `payment` trong `IDENTITY_SERVICE_CLIENTS`).
6. Admin dashboard: ghi chú "Bản xem thử" hiện trên biểu đồ; menu không còn Doanh thu / Chất lượng / Chính sách.

**Còn treo / ghi chú:**
- `AdminCentersPage`, `ManagerCaregiversPage` (code chết trong `*-live-page.tsx`) đan với trang đang dùng → dọn ở Đợt 6.
- Trang care report mẫu của khách `/customer/bookings/:code/report` giữ lại, thay bằng luồng thật ở Đợt 3.
- **Cần quyết định:** sidebar hồ sơ người bệnh hiển thị "Đường dây nóng y tế 24/7" với số `1900 6868` (`tel:19006868`) — số
  minh họa nhưng trông như thật; người dùng có thể gọi nhầm khi khẩn cấp. Đề xuất gỡ hoặc thay bằng số thật của dự án.

**Commit đề xuất:**
- BE: `fix(aftercare): hide draft care reports and expose public caregiver cards` · `fix(payment): map access denied and bad requests to 4xx; add payment-service to docker stack`
- FE: `refactor(caregiver): remove sample-only caregiver screens and fake deposit page` · `fix: label admin dashboard samples, hide preview-only admin menus, remove dead links`

---

## Đợt 1a — Payment: hợp đồng Booking đang gọi

### Kế hoạch (2026-10-29)

**Nguyên tắc hợp đồng:** phía Booking là chuẩn (`BookingService/.../client/PaymentServiceClient.java`, `PaymentClient.java`,
`client/dto/*`, stub `src/test/.../support/TestPlatformServer.java`). Booking gọi `refunds`, `remaining-invoices`,
`refunds/complaints` kiểu `void` (chỉ cần 2xx, header `Idempotency-Key`); `refund-quotes` phải trả đúng
`items[{occurrenceId, priceAmount, paidAmount, feeAmount, refundAmount, rule}]`, `totalPaid`, `totalFee`, `totalRefund`,
`policyId`; `GET /payments?bookingId&status=SUCCESS` phải trả `items[{id, bookingId, status, amount, refundedAmount}]` +
`totalPages`; `GET /payments/me/outstanding` → `hasDebt`; `GET /payment-policies/effective` → `policyId`, `depositPercent`.

**Lệch có chủ đích so với WORK_PLAN_A (ghi lý do):**
- Payment **giữ `SUCCESS`** sau khi hoàn một phần / toàn phần, tiền đã hoàn nằm ở `refundedAmount` — vì Booking lọc
  `status=SUCCESS` và từ chối mọi trạng thái khác khi tính trần hoàn khiếu nại (`ComplaintServiceImpl.findRefundableAmount`).
- **Giữ `POST /bookings/{id}/deposit-paid`** (không đổi sang `deposit-confirmations`): Payment tự lưu phân bổ cọc theo buổi ở
  `occurrence_financials`, Booking không cần số này để chạy luồng → không bắt buộc đổi phía Booking.
- Giữ path checkout hiện có `POST /payments/bookings/{bookingId}/checkout` (FE nối ở Đợt 1c); mã lỗi giữ dải 6000–6016 đã có và
  thêm tiếp, không đánh số lại theo plan.
- `refund-quotes` có controller riêng (`RefundQuoteController`) vì khác resource path, dùng chung `RefundService`.

**Schema (`schema/changes/2026-10-29-payment-refunds.sql` + cập nhật `schema/payment.sql`, chỉ additive):**
- `payments`: + `payment_type` (DEPOSIT/REMAINING, mặc định DEPOSIT — backfill dữ liệu cũ), `refunded_amount` (mặc định 0),
  `invoice_id`; unique cũ `booking_id` thay bằng unique `(booking_id, payment_type)` — chỉ nới ràng buộc, không mất dữ liệu.
- Bảng mới: `platform_policies` (seed GLOBAL cọc 30 %, phí hủy 0/10/30 % theo mốc 72h/24h, cooling-off 60' khi buổi đầu còn
  ≥ 24h, khách vắng 100 %, hạn REMAINING 48h, phiên 15'), `booking_financials` (snapshot chính sách lúc cọc),
  `occurrence_financials` (giá, cọc, đã thu, đã hoàn từng buổi), `refunds`, `remaining_invoices`, `payment_transactions`
  (append-only).

**Code:** viết lại PaymentService theo `AGENTS.md` (L-09): `service/Impl`, controller mỏng, MapStruct, `@Repository`,
`findForUpdate`, import tường minh, không `@Operation`; Stripe sau cờ `STRIPE_ENABLED` (mặc định false). Tiện ích thuần
`util/VndMoney` (làm tròn VND một chỗ), `util/DepositAllocator`, `util/RefundCalculator` (bảng §1.2). Dispatcher hoàn tiền
`PAYMENT_REFUND_MODE` = `SIMULATED` (mặc định) | `VNPAY` (adapter `vnp_Command=refund`, test bằng response giả lập),
retry có giới hạn + backoff; job OVERDUE hóa đơn REMAINING.

**Test:** H2 (thêm `h2` + `spring-boot-starter-data-jpa-test` scope test như các service khác) + `HttpServer` giả JWKS / Identity
/ Booking; test hợp đồng hai đầu bằng file JSON mẫu dùng chung (`src/test/resources/contracts/payment/*.json` ở cả Booking và
Payment). Booking: chỉ thêm test hợp đồng, không đổi code chính nếu không bắt buộc.

**Rủi ro:** Payment chưa từng chạy với DB thật có dữ liệu → change script phải chạy được trên DB hiện có (backfill
`payment_type`); dispatcher VNPay thật chưa kiểm sandbox (để sau cờ).

### Kết quả (2026-10-29) — XONG, chưa commit

> Theo yêu cầu chủ dự án: **không commit / push**. Đã `git mv service/impl → service/Impl` và `git rm` các file thay thế
> (nằm trong index); file mới chưa `git add`.

**Đã đóng:** C-01 (phía Payment), PAY-01/02 (tối thiểu)/03/04/05/06/09/10/11/12, PAY-15 (trừ tiền hoàn), PAY-16 (BE),
L-09 (PaymentService theo quy ước).

PaymentService (viết lại theo `AGENTS.md`, đặc tả `docs/2026-10-29-PAYMENT_1A_SPEC.md`):
- Schema additive: `schema/changes/2026-10-29-payment-refunds.sql` + `schema/payment.sql` — `payments` (+ `payment_type`,
  `refunded_amount`, `invoice_id`, unique `(booking_id, payment_type)`), `platform_policies` (seed GLOBAL 30 %),
  `booking_financials`, `occurrence_financials`, `refunds`, `remaining_invoices`, `payment_transactions` (append-only).
- `util/VndMoney` (làm tròn một chỗ), `util/DepositAllocator`, `util/RefundCalculator` (bảng §1.2, dùng chung cho
  preview và lệnh thật).
- Checkout cọc: payment-context đọc ngoài transaction, snapshot chính sách, phân bổ cọc theo buổi, một phiên sống,
  `expiresAt = min(now + 15', depositDueAt)`, 404 khi không phải chủ booking, 409 `BOOKING_PRICE_MISSING`.
- IPN: idempotent (`02`), sai chữ ký `97`, sai số tiền `04`; DEPOSIT → `booking_financials` DEPOSIT_PAID; REMAINING →
  hóa đơn PAID + CLOSED + `charged_amount`; báo Booking `deposit-paid` sau commit (Booking tạm lỗi → `99`).
- `POST /refund-quotes`, `POST /refunds` (idempotent, bất biến 409 `REFUND_EXCEEDS_PAID` trong transaction khóa
  `booking_financials`), `POST /refunds/complaints` (giảm hóa đơn trước), `GET /refunds`, `POST /refunds/{id}/retry`.
- Dispatcher `RefundDispatchScheduler` (30s): `SIMULATED` | `VNPAY` (`gateway/VnPayRefundGateway`, HMAC-SHA512 chuỗi `|`),
  SKIP LOCKED + thuê dòng, backoff lũy thừa, trần số lần → FAILED + `log.error`; Stripe → FAILED.
- `POST /remaining-invoices` (vắng 100 %, hủy / MISSED không tính, 0 → `NOT_REQUIRED`), job OVERDUE 10',
  `GET /remaining-invoices`, `POST /remaining-invoices/{id}/payments`, `PATCH /remaining-invoices/{id}/waive`,
  `GET /payments/me/outstanding` thật.
- `GET /payments` theo phạm vi (SERVICE/ADMIN tất cả, CUSTOMER của mình, CENTER_MANAGER theo claim `center_id`, thiếu →
  403 `CENTER_SCOPE_REQUIRED`), đọc lẻ ngoài phạm vi → 404; `refundedAmount` thật. Revenue trừ tiền hoàn.
- Quy ước: `service/Impl`, controller mỏng (gộp callback vào `PaymentController`), MapStruct + `IgnoreServerManagedFields`,
  `@Repository`, `findForUpdate…`, không `@Operation`, import tường minh, `spring.sql.init.mode: never`,
  `fail-on-unknown-properties: true`, Swagger sorter. `.env.example` (`PAYMENT_DEPOSIT_PERCENT=30`, `PAYMENT_REFUND_MODE`,
  `VNPAY_API_URL`, chu kỳ job, `STRIPE_ENABLED=false`). Mới: `PaymentService/AGENTS.md`, `README.md`; sửa `BE/AGENTS.md`.

BookingService: **không đổi code chính** — chỉ thêm `client/PaymentContractTests` + fixture
`src/test/resources/contracts/payment/*.json` (giống hệt bản ở PaymentService).

**Lệch so với đặc tả (ghi lý do):**
- `PAYMENT_DEPOSIT_PERCENT` (mặc định 30) không dùng để tính — chỉ để cảnh báo lúc khởi động khi lệch dòng GLOBAL
  (`config/PolicyStartupCheck`), vì đặc tả chốt % thật lấy từ DB. `.env` thật của máy dev vẫn còn `=100` (không sửa file
  `.env`) — app ghi `WARN` và dùng 30 từ DB.
- `payment_transactions` thêm cột `idempotency_key` (additive) để khử trùng hoàn khiếu nại khi toàn bộ số tiền chỉ giảm
  hóa đơn (không có dòng `refunds` nào làm dấu). Thêm loại sự kiện `PAYMENT_EXPIRED`, `REFUND_RETRIED`, `INVOICE_OVERDUE`.
- Tiền giữ chỗ trong bất biến gồm cả refund FAILED (vẫn nợ khách, admin có thể thử lại) — tránh lệnh mới chiếm phần đó.
- `DepositAllocator`: nếu giá lẻ bất thường làm buổi cuối âm thì lấy bớt từ các buổi trước (tổng giữ nguyên, không buổi
  nào âm) — đặc tả không nói tới trường hợp này; giá VND thực tế (bội số 1.000) không chạm tới.
- IPN tới phiên đã EXPIRED / bị thay (trả trễ) trả `02` và `log.warn` — hoàn LATE_PAYMENT thuộc Đợt 1b.

**Kiểm thử:** PaymentService `mvnw -q -B test` ✅ 35 test (18 API, 1 hợp đồng, 8 DepositAllocator, 6 RefundCalculator,
2 VnPaySigner) trên H2; **cùng 35 test pass trên PostgreSQL 17 thật** (`-Dpayment.testing.postgres=true`, schema tay +
`ddl-auto: validate` + seed). Change script chạy 2 lần trên DB cũ có dữ liệu: backfill `DEPOSIT`, gỡ unique cũ, không lỗi.
BookingService `mvnw -q -B test` ✅ (thêm 2 test hợp đồng; 2 test chỉ-PostgreSQL vẫn skip như trước).

**Kiểm tay:**
1. DB dev đang có: chạy change script, khởi động `payment-service` → log không có WARN "No effective GLOBAL payment policy".
2. Khách đặt booking → manager duyệt → `POST /api/v1/payments/bookings/{id}/checkout {"provider":"VNPAY"}` → số tiền = 30 %
   tổng; trả trên sandbox (cần tunnel cho IPN) → booking CONFIRMED.
3. Khách hủy một buổi còn ~48h → preview Booking hiện phí 10 %; xác nhận hủy → sau ≤ 30s `GET /api/v1/refunds` (khách)
   thấy REFUNDED (SIMULATED).
4. Booking COMPLETED → `GET /api/v1/remaining-invoices` có hóa đơn PENDING hạn 48h; quá hạn → `/payments/me/outstanding`
   `hasDebt=true`, Booking chặn đặt lịch mới.

**Còn treo:** kiểm chữ ký / API refund VNPay trên sandbox thật; FE thanh toán (Đợt 1c).

**Commit đề xuất:** BE `feat(payment): policy snapshot, deposit allocation, refunds, remaining invoices per Booking contract`
· `test(booking): payment contract fixtures`

---

## Đợt 1b — Payment: an toàn thu tiền

### Kế hoạch (2026-10-30)

| Hạng mục | Mã | Cách làm |
|---|---|---|
| Một phiên hiệu lực / payment, hạn ≤ `depositDueAt`, TTL 15' | C-02, PAY-05 | Đã có từ 1a; thêm: thu tiền xong → các phiên PENDING khác CANCELLED |
| Outbox xác nhận cọc sang Booking | PAY-08, H-15 | Bảng `payment_outbox`, ghi cùng transaction IPN, gửi `afterCommit` + scheduler 30s, backoff, Booking 4xx → hoàn `BOOKING_NOT_CONFIRMABLE` |
| Trả trễ / trả trùng | PAY-06 | `refunds.attempt_id`; `LATE_PAYMENT` / `DUPLICATE` hoàn qua đúng phiên |
| Job phiên quá hạn + querydr | PAY-07 | `PaymentExpiryScheduler` 5', querydr sau cờ `VNPAY_QUERYDR_ENABLED` |
| Chữ ký VNPay (`%20` vs `+`) | — | Theo mẫu chính thức 2.1.0 (`URLEncoder` US_ASCII, khoảng trắng `+`) |

Schema additive `schema/changes/2026-10-30-payment-safety.sql` (+ `schema/payment.sql`). Không đổi BookingService.

### Kết quả (2026-10-30) — XONG, chưa commit

- IPN không còn phụ thuộc Booking: thành công → payment SUCCESS + dòng outbox trong cùng transaction, trả `00`. Bỏ mã `99`.
- `PaymentOutboxService` + `scheduler/PaymentOutboxScheduler`: claim SKIP LOCKED + thuê dòng, `30s × 2^n` (trần 30'), tối đa
  10 lần → FAILED + `log.error`. Booking từ chối (409 / 404) → FAILED + `RefundService.refundWholePayment`
  (`BOOKING_NOT_CONFIRMABLE`, booking CLOSED, mọi buổi coi như đã hoàn). Nhật ký `BOOKING_CONFIRMED` / `BOOKING_REJECTED`.
- Trả trễ / trùng (`PaymentServiceImpl.completeLatePayment`): phiên không còn PENDING mà VNPay báo đã thu → nhận nếu payment
  chưa thu + cùng số tiền + booking còn chờ cọc; ngược lại `RefundService.refundSeparateAttempt` (`DUPLICATE` / `LATE_PAYMENT`,
  `attempt_id`). Dispatcher hoàn qua đúng phiên (TxnRef / TransactionNo của phiên đó, `02` khi bằng đúng số tiền phiên),
  không cộng `payments.refunded_amount`, không tính vào bất biến của payment. REMAINING trả sau khi hóa đơn đã miễn →
  hoàn `DUPLICATE` cả payment.
- `PaymentService.findStaleAttemptIds / reconcileAttempt` + `scheduler/PaymentExpiryScheduler`: phiên quá `expiresAt + 5'`
  → querydr (`gateway/VnPayQueryGateway`, chữ ký `|`) nếu bật: đã thu → ghi nhận như IPN; chưa thu → EXPIRED (payment
  EXPIRED khi không còn phiên mở); không rõ → chờ tới `max-unknown` (24h) rồi EXPIRED. Nhật ký `QUERYDR`, `PAYMENT_EXPIRED`.
- Chữ ký: `VnPaySigner` mã hóa như mẫu chính thức VNPay 2.1.0 (khoảng trắng `+`); `vnp_CreateDate` = `createdAt` phiên,
  dùng lại làm `vnp_TransactionDate` cho querydr / refund.
- Change script 1b tự xếp outbox cho payment cọc SUCCESS cũ chưa báo Booking (`booking_notified_at IS NULL`).

**Lệch / rủi ro còn lại (ghi lý do):**
- Giữ path `POST /bookings/{id}/deposit-paid` (body chỉ `paymentReference`). Booking kiểm hạn cọc theo **thời điểm nhận**
  chứ không theo lúc khách trả: nếu Booking sập lúc IPN và outbox gửi lại sau `depositDueAt` thì Booking từ chối → Payment
  hoàn toàn bộ cọc. Sửa triệt để cần Booking nhận `paidAt` (đổi DTO + `fail-on-unknown`) — để chủ dự án quyết, chưa làm.
- Chưa kiểm trên sandbox VNPay thật (pay / IPN / refund / querydr) — cần TMN + secret + tunnel.

**Kiểm thử:** PaymentService ✅ 43 test (18 API, 7 an toàn thu tiền mới trong `PaymentSafetyTests`, 1 hợp đồng, 8 + 6 unit,
3 chữ ký) trên H2 và **trên PostgreSQL 17 thật** (`-Dpayment.testing.postgres=true`). Chuỗi change script 1a → 1b chạy trên
DB cũ có dữ liệu, chạy lại 2 lần không lỗi, outbox được xếp cho payment cũ.

**Kiểm tay:**
1. Tắt `booking-service`, trả cọc sandbox → IPN `00`, `payment_outbox` PENDING; bật lại → trong ≤ 30s booking CONFIRMED.
2. Mở hai tab thanh toán cùng booking, trả cả hai → tab trả sau có refund `DUPLICATE` (`GET /api/v1/refunds`, admin).
3. Mở phiên rồi bỏ; sau ~20' phiên EXPIRED (`GET /api/v1/payments/by-booking/{id}` → `EXPIRED`).

**Commit đề xuất:** BE `feat(payment): deposit confirmation outbox, late/duplicate refunds, session expiry with querydr`

---

## Đợt 1c — FE thanh toán thật

### Kết quả (2026-10-30) — XONG, chưa commit

FE (`FE-careconnect/`, làm chồng lên 110 file giao diện chưa commit của phiên khác — không hoàn tác gì của phiên đó):
- Snapshot `openapi/openapi.yaml`: bỏ contract giả `POST /payments/deposits` + tag `PaymentDemo`; thêm contract thật
  `POST /payments/bookings/{bookingId}/checkout`, `GET /payments`, `/payments/{id}`, `/payments/by-booking/{bookingId}`,
  `/payments/me/outstanding`, `/payments/vnpay/return` (các tham số `vnp_*` tường minh, public), tag `Refund`
  (`GET /refunds`), `RemainingInvoice` (`GET /remaining-invoices`, `POST /remaining-invoices/{id}/payments`).
  `pnpm api:generate`.
- `entities/payment/payment-api.ts` viết lại theo API thật; bảng mã lỗi FE đổi từ mã stub 62xx sang `ErrorCode` thật
  (6004–6025).
- Trang cọc `/customer/bookings/:id/deposit`: mở checkout VNPay rồi chuyển hẳn sang `checkoutUrl` (bỏ chọn phương thức giả —
  VNPay tự cho chọn QR / ATM / thẻ). Mới `/customer/payments/vnpay-return`: gửi query có chữ ký sang Payment kiểm, rồi mở
  trang kết quả; trang kết quả poll `GET /payments/{id}` khi còn PENDING (IPN mới là nguồn ghi nhận tiền); thất bại của
  khoản REMAINING dẫn về hóa đơn.
- Mới `/customer/payments` (menu tài khoản "Thanh toán & hoàn tiền"): hóa đơn phần còn lại + nút trả qua VNPay, cảnh báo nợ
  quá hạn, lịch sử thanh toán (refundedAmount thật), lịch sử hoàn tiền (lý do / phí giữ lại / trạng thái).
- Gỡ `scripts/payment-demo.mjs` (+ test), plugin trong `vite.config.ts`, trang gateway giả + route; `.env.example` bỏ
  `PAYMENT_DEMO_*`. `ARCHITECTURE.md` cập nhật mục Configuration.
- BE: mặc định `VNPAY_RETURN_URL` → `http://localhost:5173/customer/payments/vnpay-return` (`application.yaml`,
  `.env.example` của PaymentService).

**Kiểm thử:** `pnpm check` ✅ (prettier, lint, typecheck, vitest 67 file / 250 test, `node --test`, build). Test mới:
trang cọc chuyển sang VNPay, lỗi 6018, kiểm return + poll tới SUCCESS, return giả chữ ký, thất bại → thử lại cọc / trả lại
hóa đơn; trang thanh toán (nợ quá hạn + trả hóa đơn, lịch sử, trạng thái rỗng).

**Kiểm tay:** đặt `VNPAY_RETURN_URL` trỏ trang FE, trả cọc sandbox → về `/customer/payments/vnpay-return` → "Đang chờ xác
nhận…" rồi "Thanh toán thành công" khi IPN tới.

---

## Đợt 2 — Bảo mật & phân quyền dữ liệu

### Kết quả (2026-10-30) — XONG (trừ M-03), chưa commit

- **C-04 (Critical) — lời mời người cùng quản lý (D19)** — CareRecipientService: bảng `recipient_manager_invitations`
  (`schema/changes/2026-10-30-manager-invitations.sql`), `POST /{id}/invitations` (chỉ quản lý chính), `GET /{id}/invitations`,
  `DELETE /{id}/invitations/{invitationId}`, `GET /invitations/me`, `POST /invitations/{id}/accept|decline`. **Gỡ**
  `POST /{id}/managers` (thêm thẳng + trả tên / SĐT người được thêm). Phản hồi mời giống hệt nhau dù email có tài khoản,
  bị khóa, không phải khách hay không tồn tại; không có tên / SĐT / userId. Hết hạn 7 ngày (tính lười). Gỡ người khác chỉ
  quản lý chính. Người được mời có tài khoản → thông báo in-app qua `POST /api/v1/notifications` của Booking (best-effort
  sau commit; Booking thêm `NotificationType.RECIPIENT_INVITATION`, `schema/changes/2026-10-30_notification_recipient_invitation.sql`).
  Sai method trên path có thật (vd `POST .../managers`) trả 404 thay vì 500. FE: khung "Người cùng quản lý" trên trang hồ sơ
  (mời, thu hồi, gỡ — chỉ hiện với quản lý chính) và trang `/customer/invitations` (chấp nhận / từ chối), snapshot OpenAPI +
  generate.
- **H-13 — `/auth/service-token` bị người ngoài khóa** — IdentityService đếm sai secret theo `(clientId, IP)` (IP qua
  `security/ClientIpResolver`, chỉ tin `X-Forwarded-For` từ proxy tin cậy; dùng chung với login). Đoán sai từ IP lạ không
  khóa được client của service thật.
- **H-12 — hai nguồn `center_id`** — ProviderService chỉ gắn caregiver / manager vào trung tâm khi `center_id` của tài khoản ở
  Identity trùng đúng trung tâm đó (`4503 IDENTITY_CENTER_MISMATCH`); tạo trung tâm không gán manager được (gán khi sửa, sau
  khi tạo tài khoản manager cho trung tâm). FE: form tạo trung tâm bỏ ô manager, ô chọn manager khi sửa chỉ liệt kê manager
  thuộc đúng trung tâm, map lỗi 4503.
- **M-03 (tùy chọn) — chưa làm**: phân quyền token SERVICE theo client cần catalog quyền theo client (đổi Identity + mọi
  `@PreAuthorize("hasRole('SERVICE')")`) — để chủ dự án quyết; C-05 (đổi secret `booking`) vẫn là việc vận hành.

**Lệch / ghi chú:**
- H-12 làm theo hướng **kiểm khớp khi ghi** thay vì đồng bộ hai chiều qua outbox: Identity chưa có API đổi `center_id` và
  Provider chưa có outbox; kiểm khớp chặn được lệch mới phát sinh. Dữ liệu lệch cũ (nếu có) cần rà tay.
- Thông báo lời mời là best-effort (Booking lỗi chỉ mất thông báo, lời mời vẫn hiện ở "Lời mời của tôi").

**Kiểm thử:** CareRecipientService ✅ 92 test (mới: lời mời không lộ email, chấp nhận / từ chối / thu hồi / hết hạn, chỉ quản
lý chính mời / gỡ, path cũ 404). IdentityService ✅ 86 (mới: khóa theo IP). ProviderService ✅ 63 (mới: caregiver / manager
lệch trung tâm). BookingService ✅ 101 (2 skip chỉ-PostgreSQL). FE: test khách hàng 108/108; `pnpm check` toàn bộ ✅ chạy chung
lượt kiểm Đợt 3 (68 file / 253 test).

**Kiểm tay:**
1. Khách A mời email của B trên hồ sơ → A chỉ thấy email + "Đang chờ"; B thấy lời mời ở menu tài khoản → chấp nhận → thấy hồ
   sơ. Mời email không tồn tại → phản hồi y hệt.
2. Gửi 6 lần secret sai cho client `booking` từ máy khác → máy đó bị 429, các service vẫn xin token bình thường.
3. Admin sửa trung tâm chọn manager thuộc trung tâm khác → báo lỗi 4503.

**Commit đề xuất:** BE `fix(care-recipient): replace direct co-manager add with invitations` · `fix(identity): rate-limit
service token per client and IP` · `fix(provider): require matching Identity center for managers and caregivers` · FE
`feat(customer): real VNPay payments, payments page, co-manager invitations`

## Đợt 3 — Hậu mãi trên FE

### Kết quả (2026-10-30) — XONG, chưa commit

- **H-04 — care report** — `entities/aftercare`: caregiver lưu nháp / nộp báo cáo trong trang chi tiết lượt thăm (form khóa sau
  khi nộp); khách và manager chỉ xem báo cáo đã nộp (Booking đã ẩn bản nháp — M-01). Gỡ trang care report mẫu cũ.
- **M-17 — ghi chú phiên** — khung "Ghi chú phiên chăm sóc" (caregiver thêm, người khác xem) trong chi tiết lượt thăm.
- **Đánh giá** — khách đánh giá caregiver đủ điều kiện ngay trên booking đã hoàn tất (gỡ modal đánh giá mẫu).
- **H-05 — khiếu nại 3 cấp** — khách gửi / chuyển cấp trên booking; manager có trang "Khiếu nại" (nhận xử lý, giải quyết, chuyển
  admin); admin `/admin/reviews-complaints` thay bản snapshot minh họa bằng hàng chờ thật (giải quyết + số tiền hoàn).
- **H-06 — thông báo** — chuông thông báo + SSE (`entities/notification`) trên header bốn cổng, đếm chưa đọc, đọc tất cả.
- **H-07 — 401 → refresh** — `shared/api/session-refresh.ts`: TanStack Query gặp 401 thì refresh cookie một lần (gộp các
  request đồng thời) rồi thử lại; refresh hỏng → về `/login`.

**Kiểm thử:** `pnpm check` ✅ (prettier, lint, typecheck, vitest 68 file / 253 test, `node --test`, build). Test mới:
`aftercare.test.tsx` (nộp báo cáo, khóa báo cáo đã nộp, ghi chú, manager / admin xử lý khiếu nại, khách đánh giá + khiếu nại +
chuyển cấp), `notification-bell.test.tsx`, `session-refresh.test.ts`.

**Commit đề xuất:** FE `feat(aftercare): care reports, session notes, reviews, complaints and notifications on the web`

## Đợt 4 — Vận hành & toàn vẹn BE

### Kết quả (2026-10-30) — XONG, chưa commit

- **H-10 / D21 — tự quyết khi không ai xử lý** — BookingService: báo khách vắng không được trung tâm quyết sau giờ kết thúc
  + 24h → hệ thống tự chấp nhận (`CUSTOMER_NO_SHOW`, khách chịu phí buổi, chốt booking / hóa đơn phần còn lại, báo khách +
  trung tâm `NO_SHOW_AUTO_ACCEPTED`); ca quên check-out quá giờ kết thúc + 4h → tự đóng (`ENDED`, buổi `COMPLETED`, việc chưa
  làm → SKIPPED, cột `care_sessions.auto_closed`, báo trung tâm `SESSION_AUTO_CLOSED`). Cấu hình
  `BOOKING_SESSION_AUTO_CLOSE_AFTER` (4h). Gọi lại nhiều lần an toàn.
- **H-09 / D20 — quy tắc khiếu nại** — chỉ mở khiếu nại cho buổi đã bắt đầu, trong 7 ngày sau giờ kết thúc (cấp booking: tính
  theo buổi đã diễn ra gần nhất, booking hủy trước khi có buổi nào thì tính từ lúc hủy) — `5926`; mỗi buổi (hoặc mỗi booking
  khi không gắn buổi) tối đa một khiếu nại đang mở (`OPEN` / `IN_PROGRESS` / `ESCALATED`) — `5927`, kiểm dưới khóa booking +
  unique index một phần trên PostgreSQL; khách chuyển admin khiếu nại chưa giải quyết khi trung tâm để quá 48h — `5928`;
  khách rút khiếu nại chưa giải quyết `POST /complaints/{id}/withdraw` → `WITHDRAWN`. Khiếu nại chuyển cấp (và khiếu nại
  nhắm thẳng trung tâm) báo admin.
- **Thông báo admin** — Identity chưa có API tra người dùng theo vai trò → `BOOKING_ADMIN_USER_IDS` (UUID admin, dấu phẩy).
  Để trống thì chỉ ghi log cảnh báo; admin vẫn thấy hàng chờ khiếu nại / sự cố trên web.
- **H-08 — outbox FAILED có đường ra** — Booking `GET /api/v1/booking-outbox`, `POST /{id}/requeue` (FAILED → PENDING, đếm
  lại lượt thử), `POST /{id}/skip` (FAILED → `SKIPPED` kèm ghi chú "đã xử lý tay"); outbox hết lượt thử báo admin
  `OUTBOX_FAILED`. Provider `GET|POST /api/v1/provider-outbox...` tương tự; sự kiện khóa / mở trung tâm chỉ chờ
  SUSPEND / RESUME trước đó chưa `SENT` / `SKIPPED` → bỏ qua sự kiện FAILED đã xử lý tay là gỡ chặn các sự kiện sau. Gateway
  thêm route. FE admin `/admin/incidents` ("Sự cố đồng bộ", menu Hệ thống): chọn nguồn Booking / Provider, lọc trạng thái,
  gửi lại / đánh dấu đã xử lý tay (bắt buộc ghi chú).
- **H-11 — caregiver ngừng hoạt động** — Provider: caregiver đang ACTIVE chuyển INACTIVE / SUSPENDED / bị từ chối xác minh /
  lưu trữ → provider_outbox `CAREGIVER_DEACTIVATED` → Booking `system-cancellations` gắn cờ `needsReassignment` cho các buổi
  sắp tới, báo manager **và khách**; caregiver cũ không check-in được buổi đang chờ đổi người (`5234`).
- **M-06 — trung tâm ngưng hoạt động** — ACTIVE → INACTIVE bị chặn khi còn booking chưa kết thúc (`4121`), như lưu trữ;
  muốn dừng gấp thì dùng SUSPENDED (đã hủy hộ booking).
- **M-07 — hồ sơ người bệnh** — CareRecipient hỏi Booking `GET /bookings/recipient-activity` (SERVICE) trước khi tạm ngưng /
  lưu trữ: còn booking chưa kết thúc → `2415`; Booking lỗi → 503 (fail-closed). Kiểm ngoài transaction ghi.
- **M-08 / D22 — một mốc start + 30'** — check-in chỉ từ start − 30' tới start + 30' (trước đây tới giờ kết thúc); báo khách
  vắng chỉ từ start tới start + 30' (trước đây tới giờ kết thúc + 60', trong khi job MISSED đã chạy ở start + 30'). Bỏ
  `BOOKING_NO_SHOW_REPORT_WINDOW`.
- **M-09 — trần hoàn khiếu nại** — Payment trả thêm `reservedRefundAmount` (REFUND_PENDING + FAILED, gộp một query cho cả
  trang) ở `GET /payments...`; Booking trừ khoản này khi kiểm trần → không còn tạo outbox `COMPLAINT_REFUND` chắc chắn bị
  Payment từ chối. Fixture hợp đồng hai bên cập nhật.
- **M-10 — thông báo** — booking quá hạn duyệt / cọc báo `BOOKING_EXPIRED` (trước đây "bị từ chối"); hủy nhiều buổi một lần
  gửi **một** thông báo gộp mỗi người thay vì N thông báo; loại mới `OUTBOX_FAILED`, `NO_SHOW_AUTO_ACCEPTED`,
  `SESSION_AUTO_CLOSED` (schema CHECK cập nhật).
- **FE** — snapshot OpenAPI (withdraw, `WITHDRAWN`, outbox hai service, loại thông báo mới, `autoClosed`,
  `reservedRefundAmount`) + generate; khách rút khiếu nại, gợi ý hạn 7 ngày / 48h, map lỗi `5923/5926/5927/5928`.

**Schema (chạy tay trên DB có sẵn, kiểm tên CHECK trước):** Booking `schema/changes/2026-10-30_care_session_auto_close.sql`,
`2026-10-30_complaint_rules.sql`, `2026-10-30_outbox_admin.sql`; Provider `schema/changes/2026-10-30-provider-outbox-skip.sql`.

**Lệch / ghi chú:**
- D21 "tự quyết" làm đúng nghĩa tự chấp nhận báo vắng (khách phản đối qua khiếu nại); bản trước chỉ nhắc manager.
- M-07 còn khe hở nhỏ: booking tạo giữa lúc kiểm và lúc ghi vẫn lọt — Booking đã chặn đặt lịch cho hồ sơ không ACTIVE nên
  chỉ còn booking tạo trong vài mili-giây đó. Chưa có API khôi phục hồ sơ đã lưu trữ (để Đợt 6 / chủ dự án quyết).
- Provider không tự báo admin khi outbox FAILED (chưa có danh sách admin bên Provider) — admin thấy ở `/admin/incidents`.
- Đồng hồ `sequence_no` của `CAREGIVER_DEACTIVATED` là mốc ms (duy nhất theo trung tâm + loại); sự kiện này không xếp hàng
  sau SUSPEND / RESUME.

**Kiểm thử:** BookingService ✅ 108 (BookingApiTests 51, 2 skip chỉ-PostgreSQL; mới: tự chấp nhận báo vắng, tự đóng ca,
quy tắc khiếu nại D20, trần hoàn trừ khoản đang chờ, outbox admin + thông báo admin, mốc check-in D22, `recipient-activity`,
check-in bị chặn khi chờ đổi người). ProviderService ✅ 65 (mới: requeue / skip gỡ chặn RESUME, cascade caregiver, chặn
INACTIVE). CareRecipientService ✅ 93 (mới: chặn tạm ngưng / lưu trữ, Booking lỗi → 503). PaymentService ✅ 43 (hợp đồng
`reservedRefundAmount`). ApiGateway ✅ 20. FE `pnpm check` ✅ (prettier, lint, typecheck, vitest 69 file / 256 test, `node --test`, build; mới: `admin-incidents-page.test.tsx`, rút khiếu nại).

**Kiểm tay:**
1. Đặt `BOOKING_ADMIN_USER_IDS`; khách mở khiếu nại buổi đã diễn ra → chuyển admin trước 48h bị chặn; sửa `created_at` lùi
   49h → chuyển được, admin nhận thông báo; rút khiếu nại → mở lại được.
2. Tắt PaymentService, hủy booking đã cọc → outbox REFUND hết lượt → admin nhận "Có việc đồng bộ… bị lỗi" → bật lại Payment →
   `/admin/incidents` bấm "Gửi lại" → hoàn tiền xuất hiện ở Payment.
3. Manager tạm khóa caregiver có lịch tuần sau → booking hiện "cần đổi người", khách nhận thông báo, caregiver không check-in được.
4. Khách tạm ngưng hồ sơ người bệnh còn booking CONFIRMED → báo lỗi `2415`.

**Commit đề xuất:** BE `feat(booking): auto-decide no-shows, auto-close sessions and complaint rules (D20–D22)` ·
`feat(booking,provider): admin requeue/skip for failed outbox events` · `fix(provider): flag visits for reassignment when a
caregiver is deactivated` · `fix(care-recipient): block suspend/archive while bookings are running` · `fix(payment): expose
reserved refunds for complaint refund caps` · `chore(gateway): route outbox admin endpoints` · FE `feat(admin): sync incidents
page; complaint withdraw and D20 hints`

## Đợt 5 — Màn hình quản trị còn thiếu

### Kết quả (2026-10-30) — XONG phần chính, chưa commit

- **M-11 — admin can thiệp booking** — BookingService cho ADMIN `POST /bookings/{id}/cancel`: hủy thay nền tảng mọi booking
  còn giữ lịch (kể cả PENDING), các buổi chưa diễn ra ghi `cancelledByParty = ADMIN`, hoàn như trung tâm hủy
  (`CENTER_CANCEL`, 100 % phần đã trả cho buổi đó). FE `/admin/bookings/:id` (nút "Hủy thay nền tảng" + lý do).
- **M-13 — admin màn hình thật** — `/admin/bookings` (mọi lịch đặt, phân trang backend, lọc trạng thái / từ khóa trên URL),
  `/admin/caregivers` (hàng chờ xác minh người chăm sóc toàn nền tảng — duyệt / từ chối kèm lý do),
  `/admin/customers` chuyển sang danh sách tài khoản Identity lọc CUSTOMER (gỡ snapshot khách hàng minh họa + i18n). Menu
  admin không còn mục bị khóa (gỡ nhánh "disabled" trong sidebar).
- **Chứng chỉ 2 bước (lỗi chặn quy trình, thuộc M-12)** — snapshot OpenAPI sai so với ProviderService: trạng thái chứng chỉ là
  `PENDING → CENTER_VERIFIED → ADMIN_VERIFIED / REJECTED` và API duyệt nhận `{ approved, reason, version }` chứ không phải
  `{ status }` → nút "Duyệt" của admin gửi sai body, và không màn hình nào làm bước trung tâm. Sửa snapshot + generate; manager
  "Xác nhận chứng chỉ" / "Từ chối" (bước 1) trên chi tiết caregiver; admin chỉ duyệt được chứng chỉ trung tâm đã xác nhận.
- **M-12 — manager** — đổi trạng thái caregiver thật sự lưu (PUT hồ sơ bỏ qua `caregiverStatus`; nay gọi thêm
  `PATCH /caregivers/{id}/status` — cũng kích hoạt cascade H-11); khung "Ngày nghỉ" (xem / thêm / xóa). Tạm dừng dịch vụ
  (offering INACTIVE) đã có sẵn.
- **M-16 — chọn caregiver theo lịch trống** — hộp chọn caregiver khi duyệt / đổi người đánh dấu "rảnh" / "bận hoặc không làm đủ
  dịch vụ" (xếp người rảnh lên đầu) bằng `GET /caregivers/search` (trung tâm + dịch vụ của booking + khung giờ các buổi sắp
  tới; ProviderService đã trừ ca làm, ngày nghỉ và booking khác). Backend vẫn kiểm lại khi lưu.
- **M-15 — KPI** — thẻ KPI trung tâm của admin dùng số toàn nền tảng từ `/care-centers/statistics` (nhãn "Toàn nền tảng"),
  chỉ rơi về đếm trang khi chưa tải được; KPI caregiver của manager đã ghi rõ "trên trang này" (phiên UI trước).

**Chưa làm (để chủ dự án quyết):** kỹ năng caregiver (`PUT /caregivers/{id}/services`) và sửa hồ sơ trung tâm phía manager;
Chính sách / Doanh thu / Đối soát admin (chờ PAY-02/13–15, D15 không làm).

**Kiểm thử:** BookingService ✅ (mới: admin hủy booking đã cọc + PENDING, `cancelledByParty = ADMIN`, hoàn `CENTER_CANCEL`).
FE `pnpm check` ✅ (71 file / 264 test; mới: `admin-bookings.test.tsx`, `admin-caregiver-queue-page.test.tsx`,
`admin-center-caregivers-page.test.tsx` (chứng chỉ 2 bước), test manager đổi trạng thái / xác nhận chứng chỉ / ngày nghỉ /
caregiver rảnh, KPI toàn nền tảng).

**Kiểm tay:** caregiver tải chứng chỉ → manager "Xác nhận chứng chỉ" → admin `/admin/centers/{id}/caregivers` "Duyệt" →
khách thấy chứng chỉ. Manager mở duyệt booking → danh sách caregiver có nhãn rảnh / bận. Admin `/admin/bookings` → mở một
lịch CONFIRMED → hủy thay nền tảng → Payment có lệnh hoàn `CENTER_CANCEL`.

**Commit đề xuất:** BE `feat(booking): let admins cancel bookings on behalf of the platform` · FE `feat(admin): bookings,
caregiver verification queue and live customers` · `fix(provider-web): two-step certificate review and caregiver status
changes` · `feat(center-manager): time off and free-caregiver hints when assigning`

## Đợt 6 — Hoàn thiện & dọn nợ

### Kết quả (2026-10-30) — XONG phần chính, chưa commit

- **M-04 — catalog mất cập nhật** — CareCatalogService thêm `@Version` (cột `version` ở `care_services`, `center_services`,
  `price_plans`); request sửa nhận `version` tùy chọn — khác bản đang lưu → 409 `3006`; response trả `version`. FE gửi
  `version` khi sửa danh mục chuẩn / dịch vụ trung tâm / gói giá và báo "vừa được người khác cập nhật".
  `schema/changes/2026-10-30_catalog_version.sql`. (Phần "đổi `catalogItemId` làm lệch kỹ năng caregiver" chưa làm.)
- **M-05 — unique xung đột xóa mềm** — Provider: email trung tâm và `user_id` hồ sơ caregiver đổi sang partial unique index
  `WHERE deleted_at IS NULL` (`schema/changes/2026-10-30-soft-delete-uniques.sql`), trung tâm / hồ sơ đã lưu trữ không còn
  chặn tạo lại.
- **L-07** — danh sách đánh giá công khai không trả `bookingId`; khách gọi `GET /care-centers/{id}` của trung tâm không ACTIVE
  → 404 (như `/public/{id}`).
- **L-08 — giới hạn đặt lịch** — buổi đầu bắt đầu trong 90 ngày tới (`5235`, `BOOKING_MAX_ADVANCE_DAYS`), mỗi buổi ≥ 1 giờ
  (`5236`, `BOOKING_MIN_OCCURRENCE_DURATION`). FE map lỗi.
- **L-09 — quy ước Payment** — bỏ 44 import wildcard (`lombok.*`, `jakarta.persistence.*`, …); các mục còn lại (`@Operation`,
  `@Tag` trùng, controller parse `X-Forwarded-For`, entity dựng tay) đã sửa từ Đợt 1a.
- **L-03** — gỡ `meta` title / description tiếng Việt cứng ở 4 route khách (trang đã đặt tiêu đề dịch qua `useDocumentTitle`).
- **L-06** — FE `ARCHITECTURE.md` (manager booking thật, menu admin, chứng chỉ 2 bước, KPI, Node), BE `AGENTS.md` (Media đã
  dùng `7xxx`). **L-10** — `engines.node >= 22.22.0` + `.nvmrc` (máy hiện tại Node 22.19 chỉ cảnh báo, chưa nâng được bằng code).
- Thông điệp lỗi FE `5222` / `5817` cập nhật theo mốc D22 (start + 30').
- **L-04** đã xong ở Đợt 0 (gỡ dashboard caregiver mẫu).

**Chưa làm:** M-19 (N+1: giá từng offering ở trang dịch vụ khách, thống kê hệ thống, media provider) — cần API gộp ở Catalog /
Media; L-05 (CSRF mỗi mutation lấy token mới) — đổi hành vi bảo mật, để chủ dự án quyết; M-04 phần đồng bộ kỹ năng caregiver khi
đổi / tắt dịch vụ.

**Kiểm thử:** BookingService ✅ 110 (mới: giới hạn L-08) · ProviderService ✅ 65 · CareCatalogService ✅ 18 (mới: sửa bằng
version cũ → 409) · PaymentService ✅ 43 · FE `pnpm check` ✅ 71 file / 264 test.

**Commit đề xuất:** BE `feat(catalog): optimistic locking for catalog items, offerings and price plans` · `fix(provider):
soft-delete aware unique email and caregiver account` · `fix(booking): booking horizon and minimum visit length; hide booking id
in public reviews` · `refactor(payment): explicit imports` · `docs: update AGENTS and ARCHITECTURE` · FE `chore: catalog
version, error copy, route meta cleanup, Node engine`
