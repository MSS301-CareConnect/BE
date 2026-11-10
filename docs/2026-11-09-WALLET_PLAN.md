# Ví 4 role + rút tiền + dashboard doanh thu (2026-11-09)

Phạm vi: PaymentService (ví, sổ giao dịch, rút tiền, cộng tiền buổi hoàn tất), ApiGateway (route), web FE (4 portal),
app mobile caregiver. **Không** sửa BookingService (luồng đặt / cọc / giữ chỗ 5 phút / tự phân công do phiên khác làm).

## 1. Quyết định của chủ dự án

| # | Chốt |
|---|---|
| 1 | Khách **luôn** cọc sau khi đặt (có hay chưa có caregiver) — phiên khác làm. "Đã phân công" xét **theo từng buổi**: lúc hủy, buổi có `caregiverUserId` trong `RefundCreateRequest.caregivers` (Booking gửi) thì đã phân công. Đã trả đủ rồi hủy từng buổi → hoàn theo chính sách của buổi đó **vào ví khách** |
| 2 | I1: buổi chưa phân công → khách nhận phần hoàn theo % chính sách vào ví, phần bị giữ cộng **ví trung tâm**; `RefundCalculator` giữ nguyên |
| 3 | A: mọi khoản hoàn lý do `CUSTOMER_CANCEL` → ví khách (`refunds.channel = WALLET`). Lý do khác (trung tâm hủy, caregiver vắng, khiếu nại, quá hạn, trả trễ / trùng) vẫn qua cổng (VNPay / SIMULATED) |
| 4 | Ví trung tâm (một ví / trung tâm, mọi manager dùng chung): tiền buổi `COMPLETED` / `CUSTOMER_NO_SHOW` − hoa hồng + cọc bị giữ của buổi chưa phân công. Ví nền tảng (ADMIN): hoa hồng = `policy_snapshot.commissionPercent` (seed 10 %), **chỉ xem, không rút**. Không thu hoa hồng trên cọc bị giữ. Caregiver: chỉ cọc bị giữ của buổi mình được giao |
| 5 | S1: `WalletSettlementScheduler` của Payment hỏi `GET /bookings/{id}/payment-context` (client + token SERVICE có sẵn) |
| 6 | Hoàn khiếu nại sau khi buổi đã cộng cho trung tâm → `COMPLAINT_DEBIT` trừ ví trung tâm, được âm; số dư ≤ 0 không rút được |
| 7 | Change script chuyển cọc bị giữ cũ của caregiver vào ví; buổi hoàn tất trước ngày triển khai **không** cộng cho trung tâm (đánh `settled_at`) |
| 8 | Có ví trên app mobile caregiver (số dư, lịch sử, tạo / xem yêu cầu rút, dashboard). Không làm: rút tối thiểu, tự hủy yêu cầu rút, dùng ví thanh toán |
| 9 | Khóa cộng trùng theo từng buổi trong sổ ví + `RefundServiceImpl.create` bỏ qua buổi đã có `final_status` |

## 2. Dữ liệu (`schema/changes/2026-11-09-wallets.sql`, khối cuối `payment.sql`)

- `wallets` — `owner_type` (`CUSTOMER` / `CAREGIVER` / `CENTER` / `PLATFORM`), `owner_id` (userId / centerId / UUID 0),
  `available_balance`, `held_balance`, `version`; UNIQUE `(owner_type, owner_id)`; CHECK `held ≥ 0`, `available ≥ 0` trừ CENTER.
- `wallet_transactions` — append-only: `type`, `amount` (> 0), `available_before/after`, `held_before/after`, `booking_id`,
  `occurrence_id`, `refund_id`, `withdrawal_id`, `reference_key`, `note`, `created_at`; **UNIQUE `(wallet_id, reference_key)`**.
  Khóa: `REFUND:<refundId>`, `FORFEIT:<occurrenceId>`, `SETTLE:<occurrenceId>`, `COMMISSION:<occurrenceId>`,
  `COMPLAINT:<refundId>`, `WD_HOLD|WD_RELEASE|WD_PAID:<withdrawalId>`.
- `wallet_withdrawals` — `wallet_id`, `requester_user_id`, `owner_type`, `center_id`, `amount`, `bank_name`,
  `bank_account_number`, `account_holder_name`, `status` (`PENDING` → `APPROVED` → `COMPLETED`; `PENDING|APPROVED` →
  `REJECTED`), `reject_reason`, `reviewed_by/at`, `transfer_reference`, `completed_by/at`, `version`.
- `refunds.channel` (`GATEWAY` | `WALLET`, mặc định `GATEWAY`); `occurrence_financials.settled_at`.

Mọi biến động số dư qua `WalletService.post` (MANDATORY, cùng transaction với sự kiện): khóa dòng ví, kiểm trùng
`reference_key`, kiểm số dư, ghi giao dịch kèm số dư trước / sau. Ví tạo lười (insert transaction riêng, trùng thì đọc lại).
Thứ tự khóa: `booking_financials` → `payments` → ví; `wallet_withdrawals` → ví.

## 3. Luồng

1. **Khách hủy** — `RefundServiceImpl.create`: lý do `CUSTOMER_CANCEL` → `channel = WALLET`. Dispatcher gặp `WALLET` không
   gọi cổng: trong transaction ghi kết quả đánh `REFUNDED`, cộng `payment.refunded_amount`, ghi `REFUND_CREDIT` ví khách.
2. **Cọc bị giữ** — `DepositForfeitureServiceImpl.record` (cùng transaction lệnh hoàn): CAREGIVER → `FORFEIT_CREDIT` ví
   caregiver; CENTER → ví trung tâm. Áp cho cả quá hạn phần còn lại.
3. **Cộng tiền buổi hoàn tất** — scheduler (mặc định 10 phút): buổi chưa `final_status`, chưa `settled_at`, đã bắt đầu quá
   `settlement.grace` → gọi payment-context ngoài transaction → buổi `COMPLETED` / `CUSTOMER_NO_SHOW`: net = cọc + thu thêm −
   đã hoàn; hoa hồng = `percentOf(commissionPercent, net)`; `SERVICE_SETTLEMENT` ví trung tâm (net − hoa hồng),
   `COMMISSION_CREDIT` ví nền tảng; đặt `final_status`, `settled_at`.
4. **Hoàn khiếu nại** — `createComplaintRefund`: nếu booking đã có buổi được cộng cho trung tâm → `COMPLAINT_DEBIT` ví trung
   tâm bằng số hoàn (không vượt tổng đã cộng cho trung tâm của booking).
5. **Rút tiền** — tạo: số tiền ≤ khả dụng → `WITHDRAWAL_HOLD`, `PENDING`. Admin duyệt → `APPROVED`; xác nhận đã chuyển
   (`transferReference`) → `COMPLETED` + `WITHDRAWAL_PAID`; từ chối (lý do) → `REJECTED` + `WITHDRAWAL_RELEASE`.

## 4. API

| Endpoint | Role |
|---|---|
| `GET /api/v1/wallets/me` | CUSTOMER, CAREGIVER, CENTER_MANAGER (ví trung tâm theo token), ADMIN (ví nền tảng) |
| `GET /api/v1/wallets/me/transactions?from&to&type&page&size` | như trên |
| `GET /api/v1/wallets/me/statistics?from&to&groupBy=DAY\|MONTH` | như trên — bộ số theo role |
| `GET /api/v1/wallets/{walletId}` · `GET /api/v1/wallets/{walletId}/transactions` | ADMIN |
| `GET /api/v1/wallet-withdrawals?status&ownerType&page&size` · `GET /{id}` | chủ ví (của mình), ADMIN (tất cả) |
| `POST /api/v1/wallet-withdrawals` | CUSTOMER, CAREGIVER, CENTER_MANAGER |
| `POST /api/v1/wallet-withdrawals/{id}/approve` · `/reject {reason}` · `/complete {transferReference}` | ADMIN |

Mã lỗi 6040+. `RefundResponse.channel` mới.

## 5. Màn hình

- Web: `/customer/wallet`, `/caregiver/wallet`, `/center-manager/wallet`, `/admin/wallet`, `/admin/withdrawals`.
- Mobile caregiver: màn "Ví" (số dư, dashboard, lịch sử, yêu cầu rút + tạo yêu cầu) từ Hồ sơ.

## 6. Kết quả (2026-11-09, chưa commit)

### Kiểm thử đã chạy

| Phần | Kết quả |
|---|---|
| PaymentService `mvnw -o test` (H2) | 53 test, **4 lỗi có sẵn từ trước** không liên quan ví: `RefundCalculatorTests` ×2, `PaymentApiTests.refundQuoteAppliesThresholdOfTheCenterPolicy`, `keptDepositGoesToTheCaregiverOfEachOccurrenceOrTheCenter` — do luật "< 2h hoàn 0 %" của commit 3e63947, test chưa cập nhật. `WalletApiTests` 3/3 xanh |
| PaymentService `-Dpayment.testing.postgres=true` (PostgreSQL 17 container tạm) | `payment.sql` + `ddl-auto: validate` khớp entity; cùng 4 lỗi có sẵn, ví xanh |
| `2026-11-09-wallets.sql` | Chạy 2 lần liên tiếp trên schema HEAD + dữ liệu mẫu: backfill ví caregiver đúng số dư chạy dồn, lần 2 không ghi trùng |
| ApiGateway `mvnw -o test` | 22/22 (lần chạy đầu có lỗi 500 ngẫu nhiên của HttpClient như ghi chú cũ, chạy lại xanh) |
| FE `tsc`, `vitest` | tsc sạch; 484 test, 2 lỗi có sẵn (`booking-reason-dialog` labels — vùng phiên khác), test ví 6/6 |
| FE `eslint` | lỗi chỉ ở file booking / customer của phiên khác; file ví sạch. `pnpm build` ok |
| Mobile `tsc`, `jest` | tsc sạch; 31 suite / 248 test xanh (ví: `tests/wallet.test.ts`, `tests/wallet-ui.test.tsx`) |

### Việc của chủ dự án

1. ~~Áp SQL + rebuild~~ — đã làm 2026-11-09: script áp vào DB payment dev (không có dữ liệu cọc cũ / buổi cần đánh dấu), PaymentService + ApiGateway rebuild, healthy; `/api/v1/wallets/me`, `/api/v1/wallet-withdrawals` qua gateway trả 401 khi không có token.
2. E2E trên trình duyệt / thiết bị (chưa làm): xem "Cách test" dưới.

### Cách test từng luồng

1. **Hủy chưa phân công** — booking đã cọc, buổi không có caregiver, khách hủy sau mốc: Payment tạo refund `channel=WALLET`
   (70 %), sau ≤ 30 s ví khách `+` tiền hoàn (`/customer/wallet`), ví trung tâm `+` 30 % (`/center-manager/wallet`).
2. **Hủy đã phân công** — như trên nhưng buổi có caregiver: phần giữ vào `/caregiver/wallet` (web) và Hồ sơ → Ví (mobile).
3. **Hủy từng buổi sau khi trả đủ** — hoàn theo mốc của buổi, chia qua REMAINING rồi DEPOSIT, toàn bộ vào ví khách.
4. **Trung tâm hủy** — hoàn 100 % qua cổng (`channel=GATEWAY`), ví khách không đổi.
5. **Buổi hoàn tất** — sau khi buổi `COMPLETED` (≥ 1h sau giờ bắt đầu, job 10'): ví trung tâm `+` 90 %, `/admin/wallet` `+` 10 %.
6. **Khiếu nại** — hoàn khiếu nại booking đã cộng: ví trung tâm `−` (có thể âm, nút rút bị khóa).
7. **Rút tiền** — tạo yêu cầu (vượt số dư → lỗi dưới ô số tiền), số dư giảm / "đang chờ rút" tăng; `/admin/withdrawals`:
   duyệt → xác nhận chuyển (nhập mã) → hoàn tất; hoặc từ chối có lý do → tiền về lại số dư.
8. **Gửi lại sự kiện** — gọi lại `POST /refunds` cùng / khác key, chạy lại dispatcher / job: số dư không đổi lần hai.

### File DÙNG CHUNG đã sửa (merge với phiên khác)

- BE: `PaymentService/.../service/Impl/RefundServiceImpl.java` (kênh hoàn, bỏ qua buổi đã có kết cục, trừ ví khiếu nại),
  `RefundDispatchServiceImpl.java`, `DepositForfeitureServiceImpl.java`, `entity/Refund.java`, `entity/OccurrenceFinancial.java`,
  `mapper/RefundMapper.java`, `mapper/FinancialMapper.java`, `dto/response/RefundResponse.java`, `exception/ErrorCode.java`,
  `repository/PaymentRepository.java`, `RefundRepository.java`, `OccurrenceFinancialRepository.java`, `config/PaymentProperties.java`,
  `application.yaml`, `.env.example`, `schema/payment.sql` (khối cuối), `ApiGateway/.../config/GatewayPaths.java` (mảng
  `PAYMENT_ROUTES`) + `ApiGatewayTests.java`, `BE/AGENTS.md` (một ô bảng). **Không** sửa BookingService / entity Booking.
- FE: `openapi/openapi.yaml` + `app/shared/api/generated/**` (chạy lại `pnpm api:generate` sau khi merge), `app/routes.ts`,
  `app/shared/i18n/resources.ts`, `shared/i18n/admin/layout-resources.ts`, `shared/i18n/customer/layout-resources.ts`,
  menu: `admin-navigation.constants.ts` (+ test `admin-dashboard-page.test.tsx`), `manager-navigation.constants.ts`,
  `caregiver-navigation.constants.ts`, `caregiver-account-menu.tsx`, `account-shell.tsx`, `customer-header.tsx`,
  `customer/payments/components/refund-history-section.tsx`, `docs/ARCHITECTURE.md`.
- Mobile: `src/app/routes.ts`, `src/app/navigation.tsx`, `src/core/l10n.ts`, `src/features/profile/screens.tsx`, `ARCHITECTURE.md`.

### Tồn đọng

- Booking cũ (trước spec v1, thu phần còn lại sau hoàn tất) không được cộng tiền buổi vào ví trung tâm tự động.
- Job cộng tiền chọn tối đa 100 booking / lượt; booking có buổi kẹt trạng thái chưa kết thúc sẽ được hỏi lại mỗi lượt.
- Không có thông báo (notification) khi yêu cầu rút được duyệt / từ chối (thông báo thuộc BookingService — ngoài phạm vi).
- Mở `?withdrawal=<id>` của admin chỉ hiện khi yêu cầu nằm trong trang danh sách đang xem.
- Màn xác nhận hủy phía khách (vùng phiên khác) chưa nói "tiền hoàn vào ví".

