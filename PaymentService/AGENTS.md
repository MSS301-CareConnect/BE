# AGENTS.md — PaymentService

> Chỉ ghi phần **khác / bổ sung** so với `../AGENTS.md`. Đọc file gốc trước. Đặc tả Đợt 1a:
> `../docs/2026-10-29-PAYMENT_1A_SPEC.md`; tiến độ: `../docs/IMPLEMENTATION_PROGRESS.md`.

## Thông số

| Field | Value |
|---|---|
| Thư mục / artifact | `CareConnect/PaymentService/` · `mss301:PaymentService:0.0.1-SNAPSHOT` |
| Base package | `mss301.paymentservice` |
| Port / env prefix | `8087` / `PAYMENT_` (+ `VNPAY_`, `STRIPE_`) |
| Dải `ErrorCode` | **6000–6999** — 6000–6016 giữ nguyên số cũ, mã mới đánh tiếp từ 6017 |
| DB | PostgreSQL riêng `127.0.0.1:5447`, DB `CARECONNECT_PAYMENT`, `schema/payment.sql` + `schema/changes/` (áp tay, không Flyway). Không Redis |

## Khác với BookingService

- **Hợp đồng phía Booking là chuẩn.** Mọi DTO nhận từ Booking (`RefundQuoteRequest`, `RefundCreateRequest`,
  `ComplaintRefundRequest`, `RemainingInvoiceRequest`) trùng tên field với `BookingService/.../client/dto/*`
  (`fail-on-unknown-properties: true`). Đổi field ở một bên → sửa fixture `src/test/resources/contracts/payment/*.json`
  ở **cả hai** service (file giống hệt nhau) và chạy `PaymentContractTests` hai đầu.
- **Payment giữ `SUCCESS` sau khi hoàn** (một phần / toàn phần); tiền đã hoàn ở `payments.refunded_amount` (Booking lọc
  `status=SUCCESS` khi tính trần hoàn khiếu nại).
- Endpoint cho service khác dùng `hasRole('SERVICE')`; Payment gọi Booking bằng token SERVICE riêng
  (`client/ServiceTokenProvider` + `ServiceTokenInterceptor`, chỉ gắn vào `BookingClient`).
- Ngoại lệ rule #3: `GET /api/v1/payments/vnpay/ipn` trả JSON thô `{RspCode, Message}`. Callback (IPN, return, Stripe)
  public, xác thực bằng chữ ký — có trong `SecurityConfig.PUBLIC_PATHS` + `@SecurityRequirements({})`.
- Không xóa mềm: dữ liệu tiền giữ vĩnh viễn (`BaseEntity` chỉ có `created_at`, `updated_at`). `payment_transactions` là
  nhật ký **append-only** (`@Immutable`), ghi qua `PaymentTransactionService.record` (propagation MANDATORY — luôn cùng
  transaction với biến động tiền). **Mọi biến động tiền ghi một dòng**; `detail` không chứa secret / `vnp_SecureHash`.
- jsonb: `booking_financials.policy_snapshot` → `dto/response/PolicySnapshot`, `platform_policies.refund_tiers` →
  `List<RefundTier>` (`@JdbcTypeCode(SqlTypes.JSON)`).
- `Clock` bean (`config/AppConfig`) — mọi "bây giờ" lấy từ Clock; test thay bằng `MutableClock`.
- Stripe sau cờ `STRIPE_ENABLED` (mặc định false, D16): không hoàn tiền qua Stripe (dispatcher đánh FAILED).

## Quy tắc nghiệp vụ (bắt buộc giữ)

1. **Làm tròn VND một chỗ:** `util/VndMoney` (scale 0, HALF_UP). Không `setScale` / chia % rải rác.
2. **Chính sách:** số liệu (% cọc, mốc phí hủy, cooling-off, % vắng mặt, hạn REMAINING, TTL phiên) lấy từ
   `platform_policies` (CENTER mới nhất có `effective_from ≤ at`, không có thì GLOBAL), chụp vào
   `booking_financials.policy_snapshot` lúc mở phiên cọc. Hoàn tiền / hóa đơn **luôn** dùng snapshot, không đọc lại
   chính sách hiện hành. Chưa có API ghi chính sách (D15) — đổi bằng SQL.
3. **Phân bổ cọc** chỉ ở `util/DepositAllocator`; **phí hủy / tiền hoàn** chỉ ở `util/RefundCalculator` (bảng
   `GAP_ANALYSIS.md` §1.2). Preview (`refund-quotes`) và lệnh thật (`refunds`) dùng cùng hàm — Payment không tin số
   tiền Booking gửi.
4. **Khóa:** mọi lệnh tiền của một booking khóa `booking_financials` (`findForUpdate`) trước. Bất biến
   `payment.refunded_amount + Σ refund (REFUND_PENDING, FAILED) + mới ≤ payment.amount` → vi phạm 409
   `REFUND_EXCEEDS_PAID`. `occurrence_financials.refunded_amount` tăng ngay khi tạo lệnh (kể cả đang chờ) để không hoàn
   trùng một buổi qua key khác.
5. **Idempotent:** refund theo `(Idempotency-Key, occurrence, payment)`; hoàn khiếu nại theo key
   `COMPLAINT_REFUND:<id>` (kể cả khi chỉ giảm hóa đơn — dấu là dòng `INVOICE_REDUCED` mang key); hóa đơn REMAINING theo
   `booking_id`. Gửi lại trả kết quả cũ, 200.
6. **Một phiên sống / payment:** mở phiên mới → phiên PENDING cũ EXPIRED. `expiresAt = min(now + TTL, depositDueAt)`.
7. **Không I/O mạng trong transaction:** đọc payment-context, gọi cổng (checkout, refund, querydr) ngoài transaction;
   ghi DB bằng `TransactionTemplate` ngắn. Báo Booking `deposit-paid` **chỉ** qua `payment_outbox`
   (`PaymentOutboxService`, ghi cùng transaction IPN, gửi `afterCommit` + scheduler) — IPN không bao giờ phụ thuộc Booking.
8. **Dispatcher hoàn tiền** (`RefundDispatchService.process`, `scheduler/RefundDispatchScheduler`): claim SKIP LOCKED +
   thuê dòng (`claim-lease`), `SIMULATED` | `VNPAY`; tạm thời (timeout, 5xx, 94) → backoff lũy thừa tới
   `max-attempts` rồi FAILED; lỗi nghiệp vụ → FAILED ngay + `log.error`. Admin `POST /refunds/{id}/retry` chỉ từ FAILED.
9. **Trả trễ / trùng:** tiền của phiên không còn PENDING mà vẫn được thu → nhận nếu còn hợp lệ, nếu không hoàn qua đúng
   phiên đó (`refunds.attempt_id`, lý do `LATE_PAYMENT` / `DUPLICATE`); refund có `attempt_id` không tính vào bất biến
   của payment và không cộng `refunded_amount`.

## Test

- `PaymentApiTests` + `PaymentContractTests` kế thừa `support/PaymentIntegrationTest` (một Spring context chung): H2
  create-drop, `support/PaymentTestServer` (JWKS RS256, token SERVICE, Booking giả, API refund VNPay giả chỉnh được mã /
  độ trễ / HTTP status), scheduler 1h — test gọi `RefundDispatchService` / `RemainingInvoiceService` trực tiếp.
- `-Dpayment.testing.postgres=true` (+ `-Dpayment.testing.postgres-url=...`, mặc định `localhost:55497`, user `postgres`,
  password `payment_test_only`): chạy toàn bộ trên PostgreSQL thật với `schema/payment.sql` + `ddl-auto: validate`.
- Unit test thuần: `DepositAllocatorTests`, `RefundCalculatorTests`, `VnPaySignerTest`.
