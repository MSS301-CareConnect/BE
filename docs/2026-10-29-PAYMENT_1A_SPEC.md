# Đặc tả triển khai Đợt 1a — PaymentService theo hợp đồng Booking (2026-10-29)

> Bản giao việc chi tiết cho phiên / agent triển khai Đợt 1a. Đọc cùng `docs/IMPLEMENTATION_PROGRESS.md` (mục Đợt 1a).
> Không commit / push (chủ dự án tự commit). Không đụng FE, `.env` thật, lịch sử git.

## Đọc trước (bắt buộc)

1. `BE/AGENTS.md` — toàn bộ quy ước (package, Lombok, `service/Impl`, controller mỏng trả `xxxService.method(...)`,
   service trả `APIResponse<T>`, MapStruct §3, `@Repository`, `findForUpdate` + comment, không `@Operation`, chỉ `@Tag`,
   import tường minh, `if` luôn có `{}`, comment tiếng Việt giải thích *vì sao*, không I/O mạng trong transaction (rule #24),
   ErrorCode + AppException, schema SQL tay + `schema/changes/`, `ddl-auto: validate`).
2. Mẫu code tốt nhất: BookingService — `exception/GlobalExceptionHandler.java`, `client/*` (ServiceTokenProvider,
   ServiceTokenInterceptor, *ClientConfig, ErrorDecoder), `scheduler/*`, `service/Impl/BookingOutboxServiceImpl.java`
   (claim SKIP LOCKED + lease + backoff), test `BookingApiTests.java` + `support/TestPlatformServer.java` (H2, JWKS RSA trên
   HttpServer, token `bearer(...)`).
3. Nghiệp vụ: `docs/GAP_ANALYSIS.md` §1.2 (bảng phí hủy), `docs/WORK_PLAN_A_PAYMENT.md` PAY-02…PAY-12, PAY-16, quyết định
   D15–D17 và "Yêu cầu riêng cho hoàn tiền" ở `docs/2026-10-28-FULLSTACK_GAP_ANALYSIS.md` §8.1.
4. **Hợp đồng (phía Booking là chuẩn — không đổi code chính của Booking trừ khi bắt buộc, phải ghi lý do):**
   `BookingService/.../client/PaymentServiceClient.java`, `PaymentClient.java`, `PaymentServiceClientConfig.java`
   (404/4xx → `REMOTE_COMMAND_REJECTED` → outbox FAILED ngay), `client/dto/*` (RefundQuoteRequest/Response,
   RefundCreateRequest, RemainingInvoiceRequest, ComplaintRefundRequest, PaymentPageResponse, PaymentSummaryResponse,
   PaymentPolicyResponse, OutstandingDebtResponse), enum `RefundReason`, `CancelledBy`, `BookingStatus`, `OccurrenceStatus`;
   nơi gọi `BookingMapper.toRefundQuoteRequest`, `BookingLifecycleServiceImpl`, `ComplaintServiceImpl.findRefundableAmount`,
   `BookingOutboxServiceImpl.send` (header `Idempotency-Key`), `BookingServiceImpl.markDepositPaid`, payment-context
   (`PaymentContextResponse` + `PaymentContextOccurrenceResponse`: `priceAmount`, `startAt`, `status`, `hasSession`). Stub test
   `TestPlatformServer.handleRefundQuote / handleRefund / handleRemainingInvoice / handlePayments / handleOutstanding /
   handlePaymentPolicy` cho thấy đúng hình dạng Booking cần.
5. Code PaymentService hiện tại (~1,8k dòng, lệch quy ước) và `schema/payment.sql`.

## Quyết định cố định

- Payment **giữ `SUCCESS`** sau khi hoàn một phần / toàn phần; tiền đã hoàn ở `refundedAmount` (Booking lọc `status=SUCCESS`).
  Trạng thái payment: PENDING, SUCCESS, FAILED, CANCELLED, EXPIRED.
- Giữ `POST /api/v1/bookings/{id}/deposit-paid` (body `{paymentReference}`); Payment tự lưu phân bổ cọc theo buổi.
- Giữ path: `POST /api/v1/payments/bookings/{bookingId}/checkout` (CUSTOMER), `GET /payments/{paymentId}`,
  `GET /payments/by-booking/{bookingId}`, `GET /payments`, `GET /payments/me/outstanding`, `GET /payments/revenue`, VNPay
  `GET /payments/vnpay/ipn` (JSON thô `{RspCode, Message}` theo rule #3) và `/vnpay/return`, Stripe webhook / return (chỉ dùng
  khi `STRIPE_ENABLED=true`, mặc định false — D16; không refund Stripe: dispatcher đánh FAILED kèm lý do). Gộp endpoint
  callback vào `PaymentController` (một controller / aggregate), `@SecurityRequirements({})` mức method.
- Giữ ErrorCode 6000–6016 (key + số); mã mới đánh tiếp sau 6016 trong 6000–6999, message tiếng Việt.
- Cọc mặc định 30 % (D17): đổi mặc định `PAYMENT_DEPOSIT_PERCENT` = 30 trong `application.yaml`, `.env.example`,
  `PaymentProperties`; giá trị thật lấy từ dòng GLOBAL của `platform_policies` (seed 30). Kiểm `scripts/e2e/payment_stub.py`.
- Làm tròn VND **một chỗ**: `util/VndMoney` (scale 0, HALF_UP). BigDecimal mọi nơi.

## Schema (chỉ additive)

`PaymentService/schema/changes/2026-10-29-payment-refunds.sql` (chạy được trên DB đang có: IF NOT EXISTS, backfill) **và**
cập nhật `schema/payment.sql`:

- `payments`: + `payment_type varchar(20) NOT NULL DEFAULT 'DEPOSIT'` CHECK DEPOSIT|REMAINING, `refunded_amount numeric(14,2)
  NOT NULL DEFAULT 0` CHECK `0 ≤ refunded_amount ≤ amount`, `invoice_id uuid NULL`; thay unique `booking_id` (inline, tên PG mặc
  định `payments_booking_id_key`, `DROP CONSTRAINT IF EXISTS`) bằng unique `(booking_id, payment_type)`.
- `platform_policies`: policy_id PK, scope GLOBAL|CENTER, center_id null, effective_from, deposit_percent, commission_percent,
  payment_session_ttl_minutes, deposit_due_hours, cooling_off_minutes, cooling_off_min_lead_hours, refund_tiers jsonb
  (`[{minLeadHours, maxLeadHours|null, feePercent}]`), customer_no_show_charge_percent, remaining_due_hours, note, created_at,
  updated_at, version; CHECK GLOBAL ⇔ center_id null; unique (scope, coalesce(center_id, uuid 0), effective_from). Seed
  idempotent id `00000000-0000-0000-0000-000000000001`: GLOBAL, từ 2026-01-01T00:00:00Z, cọc 30, hoa hồng 10, phiên 15', hạn cọc
  24h, cooling-off 60' khi buổi đầu còn ≥ 24h, tiers `[0,24)→30`, `[24,72)→10`, `[72,∞)→0`, vắng mặt 100, REMAINING 48h. Không có
  API ghi chính sách (D15) — chỉ `GET /api/v1/payment-policies/effective?centerId&at` (mọi token hợp lệ kể cả SERVICE): bản
  CENTER mới nhất có `effective_from ≤ at`, không có thì GLOBAL mới nhất; không có → 503 `NO_EFFECTIVE_POLICY`. Response tối thiểu
  `policyId`, `depositPercent` (number).
- `booking_financials`: booking_id PK, customer_id, center_id, policy_id, policy_snapshot jsonb, total_amount, deposit_amount,
  deposit_payment_id, deposit_paid_at, status AWAITING_DEPOSIT|DEPOSIT_PAID|CLOSED, created_at, updated_at, version.
- `occurrence_financials`: occurrence_id PK, booking_id, center_id, start_at, price_amount, deposit_amount, charged_amount (0),
  refunded_amount (0), final_status null (COMPLETED|CANCELLED|MISSED|CUSTOMER_NO_SHOW), updated_at, version; CHECK
  `refunded_amount ≤ deposit_amount + charged_amount`.
- `refunds`: refund_id PK, booking_id, occurrence_id null, payment_id, customer_id, center_id, complaint_id null, amount > 0,
  fee_amount, reason (RefundReason của Booking + DUPLICATE), cancelled_by, requested_by, status REFUND_PENDING|REFUNDED|FAILED,
  idempotency_key NOT NULL, provider_request_id, provider_response_code, attempts, next_attempt_at, last_error, refunded_at,
  created_at, updated_at, version; UNIQUE (idempotency_key, coalesce(occurrence_id, uuid 0), payment_id).
- `remaining_invoices`: invoice_id PK, booking_id UNIQUE, customer_id, center_id, amount, due_at, status
  PENDING|PAID|OVERDUE|WAIVED, payment_id, paid_at, waived_by, waive_reason, created_at, updated_at, version; index
  (customer_id, status), (status, due_at).
- `payment_transactions` (append-only): id, booking_id, payment_id, refund_id, invoice_id, event_type (CHECKOUT_CREATED, IPN,
  RETURN, PAYMENT_SUCCEEDED, PAYMENT_FAILED, REFUND_REQUESTED, REFUND_CALL, REFUND_SUCCEEDED, REFUND_FAILED, INVOICE_CREATED,
  INVOICE_PAID, INVOICE_WAIVED, INVOICE_REDUCED…), amount, detail (không chứa hash secret / `vnp_SecureHash`),
  signature_valid, created_at. Không update / delete. **Mọi biến động tiền ghi một dòng.**
- Entity theo AGENTS (không `@Data`); jsonb dùng `@JdbcTypeCode(SqlTypes.JSON)` (H2 hỗ trợ JSON khi test).

## Nghiệp vụ

1. **Checkout cọc** (endpoint có sẵn): đọc payment-context Booking bằng token SERVICE (ngoài tx); người gọi phải là
   customerId (khác → 404, không lộ tồn tại); status AWAITING_DEPOSIT; now < depositDueAt; mọi buổi chưa bắt đầu có
   `priceAmount` (thiếu → 409 `BOOKING_PRICE_MISSING`). Lấy chính sách hiệu lực. `DepositAllocator`: sắp theo startAt rồi
   occurrenceNo; chỉ buổi SCHEDULED/ACKNOWLEDGED; tổng = round(pct × Σgiá); mỗi buổi trừ buổi cuối = round(pct × giá_i);
   buổi cuối = tổng − Σ còn lại. Transaction ngắn: khóa / tạo booking_financials (snapshot, tổng, AWAITING_DEPOSIT), upsert
   occurrence_financials, tìm / tạo Payment DEPOSIT (đã SUCCESS → 409; PENDING khác số tiền → cập nhật). Attempt PENDING cũ →
   EXPIRED (một phiên sống). `expiresAt = min(now + ttl chính sách (15'), depositDueAt)`. Ghi transaction. Gọi cổng ngoài tx.
   `vnp_IpAddr`: controller truyền `HttpServletRequest` xuống service (controller mỏng).
2. **IPN / webhook thành công:** khóa attempt, idempotent; payment SUCCESS + paidAt; DEPOSIT → booking_financials
   DEPOSIT_PAID + deposit_payment_id + deposit_paid_at; REMAINING → invoice PAID, booking_financials CLOSED, cập nhật charged.
   Ghi transaction. Sau commit báo Booking `deposit-paid` (DEPOSIT) như hiện tại — outbox thuộc Đợt 1b. Mã RspCode giữ như cũ.
3. **`POST /api/v1/refund-quotes`** (SERVICE, không ghi): body = `RefundQuoteRequest` của Booking. `RefundCalculator` (thuần)
   dùng `policy_snapshot` + occurrence_financials: `paid_i = deposit_i + charged_i − refunded_i` (0 nếu chưa DEPOSIT_PAID / không
   có dữ liệu). Quy tắc §1.2: booking PENDING / AWAITING_DEPOSIT hoặc chưa có dữ liệu → mọi buổi 0/0 `NOT_PAID`;
   CENTER_REJECT/CENTER_CANCEL/CENTER_LOCKED → hoàn paid `FULL_REFUND_CENTER`; CAREGIVER_MISSED → hoàn paid
   `FULL_REFUND_MISSED`; CUSTOMER_CANCEL: có session hoặc status ∉ {SCHEDULED, ACKNOWLEDGED} → 0/0 `NOT_CANCELLABLE`;
   cooling-off (`cancelledAt − depositPaidAt ≤ coolingOffMinutes` **và** `firstUpcomingStartAt − cancelledAt ≥
   coolingOffMinLeadHours`) → hoàn paid `COOLING_OFF`; còn lại lead = (startAt − cancelledAt) giờ thực, chọn tier
   `min ≤ lead < max` (24h thuộc tier 10 %, 72h thuộc tier 0 %), `fee = min(round(tier% × giá), paid)`, `refund = paid − fee`,
   rule `TIER_<min>_<max|INF>`. Lý do khác (EXPIRED…) → chưa trả `NOT_PAID`, đã trả hoàn đủ `FULL_REFUND_SYSTEM`. Response:
   `{items[{occurrenceId, priceAmount, paidAmount, feeAmount, refundAmount, rule}], totalPaid, totalFee, totalRefund, policyId}`.
4. **`POST /api/v1/refunds`** (SERVICE): body = `RefundCreateRequest` + header `Idempotency-Key` (ưu tiên header). Tính lại bằng
   RefundCalculator (không tin số từ Booking). Một transaction khóa booking_financials (`findForUpdate`): mỗi buổi refund > 0 →
   `Refund` REFUND_PENDING gắn payment DEPOSIT (đã có (key, occurrence) → bỏ qua); cập nhật occurrence_financials
   `refunded_amount`, `final_status` (CANCELLED, hoặc MISSED với CAREGIVER_MISSED). **Bất biến:** `payment.refunded_amount +
   Σ pending + mới ≤ payment.amount`, sai → 409 `REFUND_EXCEEDS_PAID`. Booking chưa trả → 200, items rỗng. Ghi transaction. Trả
   `{items[{refundId, occurrenceId, amount, feeAmount, status}], totalRefund}`.
5. **Dispatcher** (`scheduler/RefundDispatchScheduler`, mặc định 30s; worker là bean riêng trong `service/Impl`, mỗi refund một
   tx ngắn qua TransactionTemplate, claim SKIP LOCKED như outbox Booking, gọi mạng ngoài tx): `PAYMENT_REFUND_MODE=SIMULATED`
   (mặc định) → REFUNDED ngay (đủ state machine, transaction, `payment.refunded_amount +=`); `VNPAY` → adapter
   `gateway/VnPayRefundGateway` POST `VNPAY_API_URL`, `vnp_Command=refund`, `vnp_TransactionType` 02 (toàn phần) / 03 (một
   phần), TxnRef / Amount×100 / TransactionNo / TransactionDate / CreateBy / RequestId / CreateDate / IpAddr / OrderInfo,
   `vnp_SecureHash` = HMAC-SHA512 trên chuỗi nối `|` theo đặc tả; mã 00 → REFUNDED; timeout / 5xx / mã 94 → giữ pending,
   attempts++, backoff lũy thừa (trần cấu hình); mã nghiệp vụ hoặc vượt số lần (mặc định 5) → FAILED + last_error + `log.error`.
   STRIPE → FAILED. `POST /api/v1/refunds/{id}/retry` (ADMIN) chỉ từ FAILED. `GET /api/v1/refunds?bookingId&status&page&size`
   (ADMIN, SERVICE: tất cả; CUSTOMER: của mình). Test adapter VNPay bằng server giả.
6. **`POST /api/v1/refunds/complaints`** (SERVICE): body `ComplaintRefundRequest`, key `COMPLAINT_REFUND:<id>`, idempotent.
   refundable = Σ(amount − refunded − pending) các payment SUCCESS của booking; vượt → 409 `REFUND_EXCEEDS_PAID`. Hóa đơn
   REMAINING PENDING/OVERDUE → giảm hóa đơn trước (transaction INVOICE_REDUCED; về 0 → WAIVED, lý do "complaint refund"); phần
   còn lại tạo refund, ưu tiên payment REMAINING rồi DEPOSIT (reason COMPLAINT_ACCEPTED, complaint_id).
7. **`POST /api/v1/remaining-invoices`** (SERVICE): body `RemainingInvoiceRequest`, key `REMAINING_INVOICE:<bookingId>`,
   idempotent theo booking_id. COMPLETED → charge = giá; CUSTOMER_NO_SHOW → round(% vắng × giá); CANCELLED / MISSED → không
   tính. remaining = Σ max(0, charge − deposit). 0 → không tạo, booking_financials CLOSED, trả `{invoiceId: null, amount: 0,
   status: "NOT_REQUIRED"}`; ngược lại hóa đơn PENDING hạn now + remainingDueHours (snapshot). Booking chưa trả → 200
   NOT_REQUIRED (`log.warn`). Job 10' PENDING quá hạn → OVERDUE (SKIP LOCKED, tx từng dòng).
   Thêm: `GET /api/v1/remaining-invoices?status&page&size` (CUSTOMER của mình; ADMIN tất cả + lọc `customerId`),
   `POST /api/v1/remaining-invoices/{id}/payments` (CUSTOMER chủ; PENDING/OVERDUE) → checkout VNPay payment REMAINING
   (`invoice_id`), `PATCH /api/v1/remaining-invoices/{id}/waive` (ADMIN, bắt buộc `reason` → `WAIVE_REASON_REQUIRED`).
8. **`GET /api/v1/payments/me/outstanding`** (CUSTOMER): `{hasDebt, overdueInvoices[], pendingInvoices[]}`, hasDebt = có OVERDUE.
9. **`GET /api/v1/payments`**: SERVICE / ADMIN tất cả (lọc bookingId / status / type); CUSTOMER của mình; CENTER_MANAGER theo
   claim `center_id` (thêm `security/CurrentCenterId` như Booking; thiếu claim → 403 `CENTER_SCOPE_REQUIRED`). Đọc lẻ ngoài
   phạm vi → 404. Trang `{items, page, size, totalElements, totalPages}`, item có `id, bookingId, paymentType, status, amount,
   refundedAmount (thật), currency, paidAt`. `GET /payments/{id}`: chủ, manager cùng trung tâm, admin; khác → 404.
10. **`GET /api/v1/payments/revenue`** (ADMIN): trừ tiền đã hoàn (PAY-15 chỉ sửa cho đúng).
11. **Quy ước (L-09):** `service/Impl`, import tường minh, bỏ `@Operation` + `@Tag` trùng, controller mỏng, `@Repository`,
    `findForUpdate…` + comment, không literal enum trong JPQL, MapStruct + `IgnoreServerManagedFields`, `@Slf4j`,
    `spring.sql.init.mode: never`, `fail-on-unknown-properties: true` (DTO nhận từ Booking phải trùng tên field), Swagger
    sorters. Thêm `PaymentService/AGENTS.md`, `PaymentService/README.md`; sửa các dòng "dự kiến / chưa có" về Payment trong
    `BE/AGENTS.md`; `.env.example` thêm `PAYMENT_REFUND_MODE`, `VNPAY_API_URL`, chu kỳ job, `STRIPE_ENABLED=false` (không secret thật).

## Test (phải pass, không xóa / skip test)

- Thêm `com.h2database:h2` và `spring-boot-starter-data-jpa-test` (scope test) như BookingService — dependency test chuẩn của
  dự án. Không thêm thư viện khác.
- `PaymentApiTests` (SpringBootTest + MockMvc, H2 create-drop, HttpServer giả JWKS / Identity / Booking, SIMULATED, gọi
  scheduler thủ công): 403 (không 500) sai role, 400 body sai; phân bổ cọc + một phiên sống; IPN idempotent (lần 2 `02`), sai
  chữ ký `97`, sai số tiền `04`; refund-quote mọi dòng bảng §1.2 kèm biên 24h / 72h, biên cooling-off, booking chưa trả; refund
  idempotent + bất biến 409; dispatcher SIMULATED → REFUNDED, `refundedAmount` cập nhật, status vẫn SUCCESS; adapter VNPay
  thành công / timeout / lỗi nghiệp vụ; trần hoàn khiếu nại + giảm hóa đơn trước; hóa đơn REMAINING (vắng 100 %, hủy / MISSED
  không tính, 0 → NOT_REQUIRED), job OVERDUE, outstanding hasDebt; phạm vi (khách A đọc payment khách B → 404, manager khác
  trung tâm → 404); có dòng `payment_transactions`. Unit test `DepositAllocator` (≥ 6 ca: giá lẻ, 60 buổi, 0 %, 100 %, 1 buổi)
  và `RefundCalculator`.
- **Test hợp đồng hai đầu:** file JSON giống hệt nhau ở `PaymentService/src/test/resources/contracts/payment/` và
  `BookingService/src/test/resources/contracts/payment/` (refund-quote-request/response, refund-create-request,
  remaining-invoice-request, complaint-refund-request, payments-page-response, outstanding-response, policy-response). Booking
  (test mới, không đổi code chính): serialize DTO client → so tên field / hình dạng với fixture; deserialize fixture response
  vào DTO client không lỗi. Payment: gửi fixture → 200, response có đủ field của fixture.
- Chạy `mvnw.cmd -q -B test` cho PaymentService **và** BookingService (và service khác nếu đụng). Sau 3 lần sửa thật sự vẫn
  lỗi → dừng và báo.

## Dừng và hỏi chủ dự án khi

Schema phải xóa cột / bảng hoặc mất dữ liệu; cần thư viện mới (ngoài test chuẩn); quy tắc nghiệp vụ không có trong tài liệu
trên; cần secret hoặc lịch sử git.

## Ngoài phạm vi 1a (để Đợt 1b)

Hoàn tiền trả trễ / trả trùng (`LATE_PAYMENT` / `DUPLICATE`), outbox xác nhận cọc sang Booking, querydr, job hết hạn phiên,
kiểm chữ ký VNPay trên sandbox (`%20` vs `+`).
