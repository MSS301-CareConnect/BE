# PaymentService

Đặt cọc booking, thanh toán phần còn lại (REMAINING), hoàn tiền theo chính sách, hóa đơn / công nợ — cổng VNPay
(Stripe tắt sau cờ, D16). Port `8087`, mã lỗi `6000–6999`. Quy ước riêng: `AGENTS.md`; đặc tả Đợt 1a:
`../docs/2026-10-29-PAYMENT_1A_SPEC.md`.

## Chạy

```powershell
Copy-Item .env.example .env        # điền DB, client SERVICE "payment", VNPay sandbox
docker compose up -d               # PostgreSQL :5447 riêng (hoặc start.bat ở BE/ cho cả stack)
Get-Content -Raw schema/payment.sql | docker compose exec -T postgres psql -U postgres -d CARECONNECT_PAYMENT -v ON_ERROR_STOP=1   # DB mới
.\scripts\Start-Dev.ps1
.\mvnw.cmd -q -B test              # H2 + server giả; -Dpayment.testing.postgres=true để chạy trên PostgreSQL thật
```

**DB đã có dữ liệu:** chạy lần lượt `schema/changes/2026-10-29-payment-refunds.sql` (thêm `payment_type` /
`refunded_amount` / `invoice_id`, đổi unique `booking_id` → `(booking_id, payment_type)`, bảng tài chính / hoàn tiền /
hóa đơn / nhật ký, seed GLOBAL) rồi `schema/changes/2026-10-30-payment-safety.sql` (`payment_outbox`, `refunds.attempt_id`,
loại sự kiện mới; tự xếp outbox cho payment cọc SUCCESS chưa báo Booking). Cả hai additive, chạy lại được.

IPN VNPay cần URL public (Cloudflare Tunnel / ngrok) trỏ `GET /api/v1/payments/vnpay/ipn` — hướng dẫn chi tiết sandbox:
`README_PAYMENT_TEST.md`.

## Cấu hình chính (`.env.example`)

| Biến | Mặc định | Ghi chú |
|---|---|---|
| `PAYMENT_SERVICE_CLIENT_ID` / `_SECRET` | — | client-credentials gọi Booking (`payment-context`, `deposit-paid`) |
| `PAYMENT_DEPOSIT_PERCENT` | `30` | chỉ để cảnh báo lúc khởi động; % thật ở `platform_policies` (D17) |
| `VNPAY_ENABLED`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL`, `VNPAY_RETURN_URL` | | thanh toán |
| `VNPAY_API_URL`, `VNPAY_SERVER_IP`, `VNPAY_CONNECT_TIMEOUT`, `VNPAY_READ_TIMEOUT` | sandbox merchant_webapi | API refund |
| `PAYMENT_REFUND_MODE` | `SIMULATED` | `SIMULATED` (đánh REFUNDED ngay, không gọi VNPay) \| `VNPAY` |
| `PAYMENT_REFUND_SCAN_INTERVAL` / `_MAX_ATTEMPTS` / `_BACKOFF_BASE` / `_BACKOFF_MAX` | `30s` / `5` / `1m` / `1h` | dispatcher |
| `PAYMENT_INVOICE_OVERDUE_SCAN_INTERVAL` | `10m` | job PENDING quá hạn → OVERDUE |
| `PAYMENT_OUTBOX_SCAN_INTERVAL` / `_MAX_ATTEMPTS` / `_BACKOFF_BASE` / `_BACKOFF_MAX` | `30s` / `10` / `30s` / `30m` | outbox xác nhận cọc sang Booking |
| `PAYMENT_EXPIRY_SCAN_INTERVAL` / `PAYMENT_EXPIRY_GRACE` / `_MAX_UNKNOWN` | `5m` / `5m` / `24h` | job phiên quá hạn |
| `VNPAY_QUERYDR_ENABLED` | `false` | job hết hạn hỏi lại VNPay (querydr) trước khi đánh EXPIRED |
| `STRIPE_ENABLED` | `false` | D16 |

## API

Mọi response (trừ IPN) là `APIResponse<T>` `{code, message, result}`. Trang: `{items, page, size, totalElements, totalPages}`.

| Method | Path | Role | Mô tả |
|---|---|---|---|
| GET | `/api/v1/payment-policies/effective?centerId&at` | mọi token | Chính sách hiệu lực (CENTER → GLOBAL); không có → 503 `NO_EFFECTIVE_POLICY` |
| POST | `/api/v1/payments/bookings/{bookingId}/checkout` | CUSTOMER (chủ booking) | Body `{provider}`. Mở phiên cọc: snapshot chính sách, phân bổ cọc theo buổi, phiên cũ → EXPIRED, hạn `min(now+15', depositDueAt)` |
| GET | `/api/v1/payments` `?bookingId&status&type&page&size` | SERVICE, ADMIN: tất cả · CUSTOMER: của mình · CENTER_MANAGER: trung tâm (claim `center_id`, thiếu → 403) | Item: `id, bookingId, customerId, centerId, invoiceId, paymentType, status, amount, refundedAmount, currency, paidAt, createdAt` |
| GET | `/api/v1/payments/{id}` · `/by-booking/{bookingId}?type=DEPOSIT` | như trên | Ngoài phạm vi → 404 |
| GET | `/api/v1/payments/me/outstanding` | CUSTOMER | `{hasDebt, overdueInvoices[], pendingInvoices[]}` — `hasDebt` = có hóa đơn OVERDUE |
| GET | `/api/v1/payments/revenue?from&to&centerId` | ADMIN | `successfulPaymentCount, grossRevenue, totalRefunded, totalRevenue` (= thu − hoàn), `averagePayment` |
| GET | `/api/v1/payments/vnpay/ipn` | public (HMAC) | JSON thô `{RspCode, Message}`: `00` ok, `02` đã xác nhận, `01` không thấy, `04` sai tiền, `97` sai chữ ký. Booking lỗi **không** làm IPN lỗi (outbox) |
| GET | `/api/v1/payments/vnpay/return` | public (HMAC) | Trình duyệt quay về; không phải nguồn xác nhận |
| POST | `/api/v1/refund-quotes` | SERVICE | Body = `RefundQuoteRequest` của Booking. Không ghi DB. `{items[{occurrenceId, priceAmount, paidAmount, feeAmount, refundAmount, rule}], totalPaid, totalFee, totalRefund, policyId}` |
| POST | `/api/v1/refunds` | SERVICE | Header `Idempotency-Key` (ưu tiên) hoặc body `idempotencyKey`. Body = `RefundCreateRequest`. `{items[{refundId, occurrenceId, amount, feeAmount, status}], totalRefund}`; booking chưa cọc → 200 rỗng |
| POST | `/api/v1/refunds/complaints` | SERVICE | Body `ComplaintRefundRequest`, key `COMPLAINT_REFUND:<id>`. Giảm hóa đơn REMAINING đang mở trước, phần còn lại hoàn (REMAINING rồi DEPOSIT) |
| GET | `/api/v1/refunds?bookingId&status&page&size` | SERVICE, ADMIN: tất cả · CUSTOMER: của mình | Lịch sử hoàn |
| POST | `/api/v1/refunds/{id}/retry` | ADMIN | Chỉ từ FAILED → REFUND_PENDING |
| POST | `/api/v1/remaining-invoices` | SERVICE | Body `RemainingInvoiceRequest`, idempotent theo booking. `{invoiceId, bookingId, amount, status, dueAt}`; không phải thu → `{invoiceId: null, amount: 0, status: "NOT_REQUIRED"}` |
| GET | `/api/v1/remaining-invoices?status&customerId&page&size` | CUSTOMER: của mình · ADMIN: tất cả (+ lọc `customerId`) | |
| POST | `/api/v1/remaining-invoices/{id}/payments` | CUSTOMER (chủ) | Body `{provider}` — phiên VNPay cho payment REMAINING (hóa đơn PENDING / OVERDUE) |
| PATCH | `/api/v1/remaining-invoices/{id}/waive` | ADMIN | Body `{reason}` bắt buộc (`WAIVE_REASON_REQUIRED`) |

### Bảng phí hủy (`util/RefundCalculator`, GAP_ANALYSIS §1.2)

`paid_i = cọc_i + đã thu thêm_i − đã hoàn_i`. Booking PENDING / AWAITING_DEPOSIT hoặc Payment chưa có dữ liệu → mọi buổi
`NOT_PAID` (0/0). `CENTER_REJECT / CENTER_CANCEL / CENTER_LOCKED` → hoàn đủ (`FULL_REFUND_CENTER`); `CAREGIVER_MISSED` →
`FULL_REFUND_MISSED`; lý do khác (EXPIRED…) → `FULL_REFUND_SYSTEM`. `CUSTOMER_CANCEL`: buổi có session / đã bắt đầu →
`NOT_CANCELLABLE`; trong 60' sau khi cọc **và** buổi sắp tới gần nhất còn ≥ 24h → `COOLING_OFF` (hoàn đủ); còn lại theo
mốc `min ≤ lead < max` (giờ thực): `[72h, ∞)` 0 % · `[24h, 72h)` 10 % · `[0, 24h)` 30 % giá buổi, phí ≤ `paid_i`, rule
`TIER_<min>_<max|INF>`.

### Hóa đơn REMAINING

COMPLETED tính đủ giá; CUSTOMER_NO_SHOW tính `% vắng mặt × giá` (seed 100 %); CANCELLED / MISSED không tính.
`remaining = Σ max(0, charge_i − cọc_i)`; 0 → booking CLOSED, `NOT_REQUIRED`; ngược lại hóa đơn PENDING hạn
`now + 48h` (snapshot). Quá hạn → OVERDUE (= nợ, Booking chặn đặt lịch mới).

## Schema

`platform_policies` (seed GLOBAL `00000000-0000-0000-0000-000000000001`: cọc 30, hoa hồng 10, phiên 15', hạn cọc 24h,
cooling-off 60' / 24h, tiers 30/10/0, vắng 100, REMAINING 48h) · `payments` (+ `payment_type`, `refunded_amount`,
`invoice_id`; unique `(booking_id, payment_type)`) · `payment_attempts` · `booking_financials` · `occurrence_financials` ·
`refunds` · `remaining_invoices` · `payment_transactions` (append-only).

## An toàn thu tiền (Đợt 1b)

- **Outbox xác nhận cọc** (`payment_outbox`): IPN thành công ghi payment SUCCESS + dòng outbox trong một transaction, gửi
  `POST /bookings/{id}/deposit-paid` ngay sau commit; Booking lỗi tạm → backoff `30s × 2^n` (trần 30'), tối đa 10 lần rồi
  FAILED. Booking từ chối (4xx: booking hết hạn / đã hủy) → FAILED + hoàn toàn bộ cọc `BOOKING_NOT_CONFIRMABLE`.
- **Trả trễ / trả trùng:** VNPay báo đã thu cho phiên không còn PENDING → nếu payment chưa thu, cùng số tiền và booking
  vẫn chờ cọc thì nhận như bình thường; ngược lại hoàn **đúng phiên đó** (`refunds.attempt_id`): `DUPLICATE` khi payment
  đã thu, `LATE_PAYMENT` khi chưa (giá đã đổi / booking không còn chờ). Tiền phiên trùng nằm ngoài `payments.amount` nên
  không đổi `refundedAmount`. REMAINING trả sau khi hóa đơn đã miễn → hoàn `DUPLICATE` cả payment. Đã thu tiền → các phiên
  PENDING khác của payment bị CANCELLED.
- **Job phiên quá hạn** (`PaymentExpiryScheduler`, PAY-07): phiên PENDING quá `expiresAt + grace` → nếu bật querydr hỏi lại
  VNPay (`vnp_Command=querydr`): đã thu → ghi nhận như IPN; chưa thu → EXPIRED (payment EXPIRED khi không còn phiên mở);
  VNPay không trả lời → để lượt sau, quá `max-unknown` thì EXPIRED.
- **Chữ ký VNPay** theo mẫu chính thức 2.1.0: `URLEncoder` US_ASCII (khoảng trắng = `+`) cho cả chuỗi ký và query.
  `vnp_CreateDate` = `createdAt` của phiên để querydr / refund gửi lại đúng `vnp_TransactionDate`.

Chưa kiểm trên sandbox VNPay thật (cần TMN / secret + tunnel): chữ ký pay / IPN, API refund, querydr.
