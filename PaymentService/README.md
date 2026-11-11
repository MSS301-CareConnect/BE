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
| `PAYMENT_SERVICE_CLIENT_ID` / `_SECRET` | — | client-credentials gọi Booking (`payment-context`, `deposit-paid`, `remaining-paid`, `payment-overdue`) |
| `PAYMENT_DEPOSIT_PERCENT` | `30` | chỉ để cảnh báo lúc khởi động; % thật ở `platform_policies` (D17) |
| `VNPAY_ENABLED`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL`, `VNPAY_RETURN_URL` | | thanh toán |
| `VNPAY_API_URL`, `VNPAY_SERVER_IP`, `VNPAY_CONNECT_TIMEOUT`, `VNPAY_READ_TIMEOUT` | sandbox merchant_webapi | API refund |
| `PAYMENT_REFUND_MODE` | `SIMULATED` | `SIMULATED` (đánh REFUNDED ngay, không gọi VNPay) \| `VNPAY` |
| `PAYMENT_REFUND_SCAN_INTERVAL` / `_MAX_ATTEMPTS` / `_BACKOFF_BASE` / `_BACKOFF_MAX` | `30s` / `5` / `1m` / `1h` | dispatcher |
| `PAYMENT_INVOICE_OVERDUE_SCAN_INTERVAL` | `10m` | job hóa đơn PENDING quá hạn: trước buổi đầu → EXPIRED + giữ cọc + báo Booking; booking cũ → OVERDUE |
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
| GET | `/api/v1/payments/revenue?from&to&centerId` | ADMIN · CENTER_MANAGER (spec §9.3: chỉ trung tâm trong token, `centerId` bị bỏ qua; thiếu claim → 403 `6027`) | `successfulPaymentCount, grossRevenue, totalRefunded, totalRevenue` (= thu − hoàn), `averagePayment` |
| GET | `/api/v1/payments/vnpay/ipn` | public (HMAC) | JSON thô `{RspCode, Message}`: `00` ok, `02` đã xác nhận, `01` không thấy, `04` sai tiền, `97` sai chữ ký. Booking lỗi **không** làm IPN lỗi (outbox) |
| GET | `/api/v1/payments/vnpay/return` | public (HMAC) | Trình duyệt quay về; không phải nguồn xác nhận |
| POST | `/api/v1/refund-quotes` | SERVICE | Body = `RefundQuoteRequest` của Booking. Không ghi DB. `{items[{occurrenceId, priceAmount, paidAmount, feeAmount, refundAmount, rule}], totalPaid, totalFee, totalRefund, policyId}` |
| POST | `/api/v1/refunds` | SERVICE | Header `Idempotency-Key` (ưu tiên) hoặc body `idempotencyKey`. Body = `RefundCreateRequest`. `{items[{refundId, occurrenceId, amount, feeAmount, status}], totalRefund}`; booking chưa cọc → 200 rỗng |
| POST | `/api/v1/refunds/complaints` | SERVICE | Body `ComplaintRefundRequest`, key `COMPLAINT_REFUND:<id>`. Giảm hóa đơn REMAINING đang mở trước, phần còn lại hoàn (REMAINING rồi DEPOSIT) |
| GET | `/api/v1/refunds?bookingId&status&page&size` | SERVICE, ADMIN: tất cả · CUSTOMER: của mình | Lịch sử hoàn |
| POST | `/api/v1/refunds/{id}/retry` | ADMIN | Chỉ từ FAILED → REFUND_PENDING |
| POST | `/api/v1/remaining-invoices` | SERVICE | Body `RemainingInvoiceRequest`, idempotent theo booking. `{invoiceId, bookingId, amount, status, dueAt}`; không phải thu → `{invoiceId: null, amount: 0, status: "NOT_REQUIRED"}` |
| GET | `/api/v1/remaining-invoices?status&customerId&page&size` | CUSTOMER: của mình · ADMIN: tất cả (+ lọc `customerId`) | |
| POST | `/api/v1/remaining-invoices/{id}/payments` | CUSTOMER (chủ) | Body `{provider}` — phiên VNPay cho payment REMAINING (hóa đơn PENDING / OVERDUE). Hóa đơn trước buổi đầu: chỉ trả trước `dueAt`, phiên hết hạn không quá `dueAt` |
| PATCH | `/api/v1/remaining-invoices/{id}/waive` | ADMIN | Body `{reason}` bắt buộc (`WAIVE_REASON_REQUIRED`) |
| GET | `/api/v1/wallets/me` | CUSTOMER, CAREGIVER (ví của mình) · CENTER_MANAGER (ví trung tâm trong token) · ADMIN (ví nền tảng) | `{id, ownerType, ownerId, availableBalance, heldBalance, withdrawable, updatedAt}`; chưa có biến động → `id: null`, số dư 0 |
| GET | `/api/v1/wallets/me/transactions?from&to&type&page&size` | như trên | Sổ ví mới nhất trước: `type, amount, availableBefore/After, heldBefore/After, centerId, bookingId, occurrenceId, refundId, withdrawalId, note, createdAt` |
| GET | `/api/v1/wallets/me/statistics?from&to&groupBy=DAY\|MONTH` | như trên | Dashboard: số dư, thu nhập / đã rút / số booking, `byType[]`, `series[]` (giờ VN, tối đa 400 mốc); thêm theo loại ví — khách: `totalPaid, refundedToWallet, refundedToGateway, netSpent`; caregiver: `forfeitIncome`; trung tâm: `serviceIncome, commissionDeducted, forfeitIncome, complaintDebits`; nền tảng: `commissionIncome, grossCollected, totalRefunded, refundedToWallet, liabilities[], pendingWithdrawalCount/Amount` |
| GET | `/api/v1/wallets/me/center-balances` | CUSTOMER, CAREGIVER | 2026-11-10: số dư khả dụng theo trung tâm đã tạo ra khoản tiền (`centerId` null = tiền cũ không gắn trung tâm, admin xử lý) — chọn nơi gửi yêu cầu rút |
| GET | `/api/v1/wallets/{walletId}` · `/{walletId}/transactions` | ADMIN | Xem ví / sổ của người yêu cầu rút |
| GET | `/api/v1/wallet-withdrawals?scope=OWN|REVIEW&status&ownerType&page&size` · `/{id}` | chủ ví (`OWN`, của mình; ví khác → 404) · ADMIN (`OWN` = tất cả) · người xử lý (`REVIEW`) | 2026-11-10 `REVIEW`: CENTER_MANAGER → yêu cầu của khách / caregiver gắn trung tâm mình; ADMIN → ví trung tâm + yêu cầu cũ không gắn trung tâm; role khác → 403 `6055`. 2026-11-11: danh sách và chi tiết có `requesterName`, `requesterPhoneNumber` (Identity `/users/batch`, token SERVICE; Identity lỗi → null) |
| GET | `/api/v1/wallet-withdrawals/statistics?scope&from&to&groupBy` | CENTER_MANAGER, ADMIN | Đang chờ (mọi thời điểm), gửi / đã chuyển / từ chối trong kỳ, thời gian xử lý TB, `byOwnerType[]`, `series[]`; `REVIEW` của manager thêm `custody*` = tiền khách / caregiver gắn trung tâm còn trong ví (trung tâm đang giữ hộ) |
| POST | `/api/v1/wallet-withdrawals` | CUSTOMER, CAREGIVER, CENTER_MANAGER | Body `{amount, bankName, bankAccountNumber, accountHolderName, centerId?}`; số tiền ≤ khả dụng (409 `6041`) → tạm giữ (`WITHDRAWAL_HOLD`, gắn `centerId`), `PENDING`. Ví khách / caregiver: `centerId` = trung tâm giữ khoản tiền (vượt số dư tại đó → 409 `6054`); bỏ trống → tiền không gắn trung tâm nếu đủ, không thì trung tâm duy nhất, nhiều trung tâm → 400 `6053`. Ví trung tâm: admin xử lý |
| POST | `/api/v1/wallet-withdrawals/{id}/transfers` · `/reject {reason}` | người xử lý: manager của trung tâm giữ tiền (khách / caregiver); ADMIN (ví trung tâm, yêu cầu cũ không gắn trung tâm) — người khác 403 `6055` | 2026-11-10: bỏ duyệt / xác nhận tay (`/approve`, `/complete` đã xóa). `transfers` = chuyển khoản giả lập: `PENDING\|APPROVED → COMPLETED` tự động, sinh `transferReference` `SIM…`, `WITHDRAWAL_PAID`; `reject` → `REJECTED` + `WITHDRAWAL_RELEASE`. Cả hai báo người rút qua outbox (`WITHDRAWAL_COMPLETED` / `WITHDRAWAL_REJECTED` → BookingService `/notifications`, `refType = WALLET_WITHDRAWAL`). Sai trạng thái 409 `6043` |

### Khoản thanh toán đầu (spec booking v1.0 §9.1, `../docs/2026-11-02-BOOKING_FLOW_REVIEW.md` §9)

`% khoản đầu` = `initialPaymentPercent` trong `payment-context` (Booking chốt từ center booking policy: SHIFT 100,
RECURRING / CUSTOM cọc %); booking cũ chưa có trường này → `platform_policies.deposit_percent`. Snapshot lúc mở phiên chụp
thêm mốc / % hoàn và % giữ cọc khi quá hạn từ `payment-context` (Q4a). Khoản đầu phủ toàn bộ tiền → `deposit-paid`
gửi `fullyPaid = true`.

### Bảng tiền hoàn (`util/RefundCalculator`, spec §10.2 — Q5, M4)

`paid_i = cọc_i + đã thu thêm_i − đã hoàn_i`. Booking chưa trả khoản đầu hoặc Payment chưa có dữ liệu → mọi buổi
`NOT_PAID` (0/0). `CENTER_REJECT / CENTER_CANCEL / CENTER_LOCKED` → hoàn đủ (`FULL_REFUND_CENTER`); `CAREGIVER_MISSED` →
`FULL_REFUND_MISSED`; lý do khác (EXPIRED…) → `FULL_REFUND_SYSTEM`. `CUSTOMER_CANCEL`: buổi có session / đã bắt đầu →
`NOT_CANCELLABLE`; còn ≥ `refundThresholdMinutes` (mặc định 24h) → `BEFORE_THRESHOLD` hoàn `refundPercentBeforeThreshold`
(100 %) của `paid_i`; ít hơn → `AFTER_THRESHOLD` hoàn `refundPercentAfterThreshold` (70 %); phần giữ lại là phí. Không còn
cooling-off và bậc 72h. Snapshot cũ thiếu mốc (change script đã bổ sung) → hoàn đủ. Buổi đã trả đủ: tiền hoàn chia qua
payment REMAINING rồi DEPOSIT theo chỗ còn hoàn được.

### Hóa đơn REMAINING

- **Trước buổi đầu (booking spec v1, `booking_financials.remaining_due_at` có giá trị):** trả khoản đầu thành công →
  hóa đơn PENDING `= Σ (giá − cọc)` các buổi, hạn = `remainingPaymentDueAt` của Booking; không phải nợ. Trả xong → buổi
  được ghi đủ giá, outbox `REMAINING_CONFIRMATION` → Booking `/remaining-paid`. Hủy buổi trước khi trả → hóa đơn giảm
  theo buổi còn lại, về 0 → CANCELLED. Quá hạn (job, khóa hóa đơn cùng IPN) → EXPIRED, giữ
  `overduePaymentForfeitPercent` (mặc định 100 %) số đã trả mỗi buổi, hoàn phần dư `PAYMENT_OVERDUE`, outbox
  `PAYMENT_OVERDUE` → Booking `/payment-overdue` (EXPIRED); lệnh hủy tới sau không hoàn thêm.
- **Sau hoàn tất (booking cũ, `remaining_due_at` NULL):** COMPLETED tính đủ giá; CUSTOMER_NO_SHOW tính
  `% vắng mặt × giá` (seed 100 %); CANCELLED / MISSED không tính. `remaining = Σ max(0, charge_i − cọc_i)`; 0 → booking
  CLOSED, `NOT_REQUIRED`; ngược lại hóa đơn PENDING hạn `now + 48h` (snapshot). Quá hạn → OVERDUE (= nợ, Booking chặn đặt
  lịch mới).

## Schema

`platform_policies` (seed GLOBAL `00000000-0000-0000-0000-000000000001`: cọc 30, hoa hồng 10, phiên 15', hạn cọc 24h,
cooling-off 60' / 24h, tiers 30/10/0, vắng 100, REMAINING 48h) · `payments` (+ `payment_type`, `refunded_amount`,
`invoice_id`; unique `(booking_id, payment_type)`) · `payment_attempts` · `booking_financials` · `occurrence_financials` ·
`refunds` (+ `channel` GATEWAY / WALLET) · `remaining_invoices` · `payment_transactions` (append-only) · ví (2026-11-09):
`wallets` (unique `(owner_type, owner_id)`, chỉ ví CENTER được âm) · `wallet_transactions` (append-only, unique
`(wallet_id, reference_key)`) · `wallet_withdrawals`; `occurrence_financials.settled_at`. DB cũ: `schema/changes/2026-11-09-wallets.sql`. 2026-11-10:
`payment_outbox.booking_id/payment_id` nullable + loại `WITHDRAWAL_*`, index hàng đợi trung tâm —
`schema/changes/2026-11-10-withdrawals-by-center.sql`.

## An toàn thu tiền (Đợt 1b)

- **Outbox báo Booking** (`payment_outbox`): IPN / job ghi tiền + dòng outbox trong một transaction, gửi ngay sau commit:
  `DEPOSIT_CONFIRMATION` → `/deposit-paid`, `REMAINING_CONFIRMATION` → `/remaining-paid`, `PAYMENT_OVERDUE` →
  `/payment-overdue`. Booking lỗi tạm → backoff `30s × 2^n` (trần 30'), tối đa 10 lần rồi FAILED. Booking từ chối (4xx:
  booking hết hạn / đã hủy) → FAILED + hoàn toàn bộ khoản vừa thu `BOOKING_NOT_CONFIRMABLE` (quá hạn: chỉ ghi lỗi).
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
