# Doanh thu trung tâm / nền tảng + quyết toán booking (2026-11-11)

Phạm vi: PaymentService (chặn trung tâm rút, API quyết toán booking), web FE (trang Doanh thu thay ví trung tâm / ví nền
tảng, thẻ quyết toán ở chi tiết booking của quản lý và admin). Không đổi DB, không đổi cách tính tiền. Chưa commit.

## 1. Rà soát: tiền cuối cùng của booking đã vào ví đúng chưa

Có — cộng **theo từng buổi** (`WalletSettlementServiceImpl`, job 10 phút, sau `settlement.grace` từ giờ bắt đầu):

| Buổi | Xử lý |
|---|---|
| `COMPLETED` / `CUSTOMER_NO_SHOW` | thực thu = cọc + thu thêm − đã hoàn → trung tâm `SERVICE_SETTLEMENT` (đã trừ hoa hồng snapshot), nền tảng `COMMISSION_CREDIT` |
| `CAREGIVER_MISSED` | Booking gửi refund `CAREGIVER_MISSED` → hoàn 100 % tiền buổi vào ví khách, buổi `final_status = MISSED` → job bỏ qua |
| Hủy / hết hạn | hoàn theo chính sách; cọc bị giữ → caregiver hoặc trung tâm, không thu hoa hồng |

Đã kiểm bằng test (`WalletApiTests.bookingWithMissedAndCompletedVisitsSettlesOnlyServedVisits`): 3 buổi × 1.000.000,
buổi 1 caregiver vắng → khách nhận lại 1.000.000, trung tâm 1.800.000, nền tảng 200.000.

## 2. Quyết định của chủ dự án

| # | Chốt |
|---|---|
| 1 | Tiền đã vào thẳng tài khoản trung tâm / nền tảng → trung tâm không rút tiền; "Ví trung tâm" và "Ví nền tảng" đổi thành trang **Doanh thu** (biểu đồ, số liệu, hoa hồng) |
| 2 | Trang Yêu cầu rút tiền (khách / caregiver do trung tâm xử lý; admin) giữ nguyên; chỉ bỏ việc quản lý gửi yêu cầu rút |
| 3 | Có khối **Quyết toán booking** ở chi tiết booking của quản lý và admin |

## 3. Backend (PaymentService)

- `WalletServiceImpl.WITHDRAWABLE_OWNERS` = CUSTOMER, CAREGIVER: `lockOwnWallet` của trung tâm / nền tảng → 403 `6052`,
  `withdrawable = false`. `POST /wallet-withdrawals` chỉ `CUSTOMER`, `CAREGIVER`. Yêu cầu cũ của ví trung tâm vẫn hiện ở
  hàng đợi admin để chuyển khoản / từ chối như trước.
- `GET /api/v1/wallets/bookings/{bookingId}/settlement` (ADMIN; CENTER_MANAGER của trung tâm trong token — booking trung
  tâm khác hoặc chưa có `booking_financials` → 404 `6017`) → `BookingSettlementResponse`: `paidAmount`, `refundedAmount`,
  `centerServiceAmount`, `centerKeptDepositAmount`, `caregiverKeptDepositAmount`, `complaintDebitAmount`,
  `centerNetAmount` (= tiền buổi + cọc giữ − trừ khiếu nại), `commissionAmount` (đã trừ hoa hồng trả lại khi hoàn khiếu
  nại), `commissionPercent`, `settledCount`, `pendingCount`, `occurrences[]` (giá, đã trả, đã hoàn, cọc giữ, trung tâm
  nhận, hoa hồng, `finalStatus`, `settledAt`). Gateway đã route `/api/v1/wallets/**`.
- Sửa 2026-11-12: khách mở phiên VNPay rồi bỏ (phiên hết hạn, booking hủy / hết hạn) thì `booking_financials` vẫn ở
  `AWAITING_DEPOSIT` nhưng `occurrence_financials.deposit_amount` đã có cọc phân bổ → quyết toán từng báo
  "khách đã trả" số tiền chưa từng thu. Nay booking chưa thu cọc trả 404 `BOOKING_NOT_PAID` 6056 (FE hiện "chưa thanh
  toán"); đã thu cọc thì `paidAmount` = cọc thu được + phần còn lại đã trả. Test
  `WalletApiTests.settlementCountsOnlyTheDepositActuallyReceived`.

## 4. Web FE

- `/center-manager/revenue` (menu "Doanh thu", thay `/center-manager/wallet`), `/admin/revenue` (Tài chính → "Doanh thu",
  thay `/admin/wallet` và trang doanh thu mẫu cũ). `WalletDashboard` với chủ ví CENTER / PLATFORM: không số dư, không nút
  rút, không danh sách yêu cầu rút của mình; KPI bỏ "Đã rút" thêm "Số lịch đặt", biểu đồ chỉ doanh thu / hoa hồng. Thẻ
  "Tiền trung tâm đang giữ hộ" + link hàng đợi rút giữ nguyên.
- Menu tài khoản của quản lý / admin bỏ thẻ số dư ví (khách, caregiver giữ).
- `entities/wallet/booking-settlement-card.tsx` ở `/center-manager/bookings/:id` và `/admin/bookings/:id`.
- Chân trang quản lý: "CareConnect • Trang quản lý trung tâm" (bỏ "bản xem thử").

## 5. Kiểm thử

- PaymentService `mvnw -o test` (H2): 58/58 xanh (thêm 1 test).
- FE `tsc` sạch; `vitest` wallet + admin + center-manager + shared: 174/175 — lỗi còn lại
  `center-manager/policy/__tests__/manager-policy-page.test.tsx` (nhãn "Tỷ lệ cọc (RECURRING…)"), không thuộc phần này.
- `eslint` các file đã sửa sạch.

## 6. Việc của chủ dự án

1. Rebuild `payment-service` (không có SQL).
2. Kiểm tra trên trình duyệt: `/center-manager/revenue`, `/admin/revenue`, chi tiết một booking đã thanh toán (quản lý +
   admin), quản lý không còn thấy nút "Rút tiền".

## 7. File đã sửa

- BE: `PaymentService/.../controller/WalletController.java`, `WalletWithdrawalController.java`,
  `service/WalletService.java`, `WalletSettlementService.java`, `service/Impl/WalletServiceImpl.java`,
  `WalletSettlementServiceImpl.java`, `WalletWithdrawalServiceImpl.java`, `repository/WalletTransactionRepository.java`,
  mới `repository/WalletBookingTotalView.java`, `dto/response/BookingSettlementResponse.java`; test `WalletApiTests.java`;
  `PaymentService/AGENTS.md`.
- FE: `openapi/openapi.yaml` + `app/shared/api/generated/**` (đã chạy `pnpm api:generate`), `app/routes.ts`, mới
  `routes/center-manager/revenue.tsx` (xóa `routes/admin/wallet.tsx`, `routes/center-manager/wallet.tsx`),
  `features/admin/revenue/admin-revenue-page.tsx` (viết lại; xóa components / hooks / preview / test của trang mẫu,
  `features/admin/wallet`, `shared/i18n/admin/revenue-resources.ts`), `features/center-manager/revenue/manager-revenue-page.tsx`
  (chuyển từ `wallet/`), `entities/wallet/` (`wallet-dashboard`, `wallet-statistics`, `wallet-summary`, `wallet-api`,
  `wallet-balance-link`, mới `booking-settlement-card` + test, `wallet-dashboard.test`), chi tiết booking admin + manager,
  `features/shared/auth/portal-account-menu.tsx`, `features/shared/layout/ops-topbar.tsx`, menu admin + manager,
  `platform-finance-section.tsx`, `center-finance-section.tsx`, i18n `wallet-resources.ts`, `resources.ts`,
  `admin/layout-resources.ts`, `center-manager/layout-resources.ts`, `docs/ARCHITECTURE.md`.

## 8. Rà soát lại 2026-11-13

- Trả đủ (100 % ngay, hoặc cọc 30 % + phần còn lại 70 % — `markPaid` chia `charged_amount` cho từng buổi) và làm xong
  buổi → mỗi buổi: trung tâm `SERVICE_SETTLEMENT` = giá − hoa hồng snapshot (10 %), nền tảng `COMMISSION_CREDIT`.
  Cộng **khoảng 24 giờ sau khi buổi kết thúc** (BookingService giữ buổi COMPLETED trong cửa sổ khách phản ánh,
  `customer-report-window`) + tối đa một lượt quét 10 phút. Ca chờ trung tâm xác nhận thì chờ tới khi trung tâm kết luận
  (không tự hết hạn). Test mới `WalletApiTests.depositThenRemainingPaidAndAllVisitsDoneSettlesTheFullPrice`.
- Dữ liệu dev: buổi `d56922b6…` (250.000, trả 100 %) → trung tâm 225.000 + nền tảng 25.000, khớp.
- Sửa: job chỉ đọc trang đầu 100 booking ứng viên, không thứ tự; booking còn chờ (đang làm, trong 24 h, chờ xác nhận)
  lượt nào cũng là ứng viên → quá 100 booking như vậy thì booking xếp sau không bao giờ được quyết toán. Nay
  `findSettlementCandidateBookingIds` phân trang theo `bookingId` (`after`), `WalletSettlementScheduler` đi hết các trang.
  Test `settleDue()` chạy scheduler thật với trang 1 booking. PaymentService 60/60 xanh. Cần rebuild `payment-service`.
- Doanh thu hiển thị cho quản lý (`/center-manager/revenue`, `/center-manager/statistics`, cùng `GET /wallets/me/statistics`):
  "Trung tâm thực nhận" = tiền buổi (đã trừ hoa hồng) + cọc giữ − trừ khiếu nại, khớp sổ ví. Sửa "Hoa hồng nền tảng đã
  trừ": trước chỉ cộng `COMMISSION_CREDIT`, không trừ phần nền tảng trả lại khi hoàn khiếu nại (`COMPLAINT_DEBIT` trên ví
  nền tảng) → lệch với khối quyết toán booking. Nay `sumByCenterAndOwnerType` (lọc ví PLATFORM) thay `sumByCenter`.
  Test `completedVisitsAreSettled…` sửa kỳ vọng 200.000 → 0. Doanh thu vào kỳ theo ngày quyết toán (~24 h sau buổi).
