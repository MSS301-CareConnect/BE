# Dashboard thống kê + rút tiền do trung tâm xử lý (2026-11-10)

Phạm vi: BookingService (thống kê, loại thông báo), PaymentService (rút tiền theo trung tâm, chuyển khoản giả lập, thông
báo kết quả, thống kê rút tiền), web FE (Tổng quan admin, Thống kê trung tâm, Ví / Yêu cầu rút tiền của admin và trung
tâm, gộp menu Khách hàng + Tài khoản, trang Hồ sơ admin). Chưa commit.

## 1. Quyết định của chủ dự án

| # | Chốt |
|---|---|
| 1 | Yêu cầu rút của **caregiver** và **khách hàng** do **quản lý trung tâm** tương ứng xử lý. Ví khách có thể có tiền hoàn từ nhiều trung tâm → khách **chọn trung tâm** (tự chọn sẵn khi chỉ có một); số rút tối đa = số dư của khoản tiền tại trung tâm đó |
| 2 | Theo cơ chế thật, tiền cọc / hoàn nằm ở tài khoản trung tâm; ví của khách / caregiver phản ánh số tương ứng. Trung tâm có nơi xem **tiền đang giữ hộ**; khi trung tâm chuyển cho khách / caregiver thì số giữ hộ giảm. **Ví trung tâm không đổi** khi chi trả |
| 3 | Chuyển khoản **giả lập**: người xử lý bấm chuyển khoản, thành công thì yêu cầu **tự** sang `COMPLETED` — không còn đổi trạng thái tay (bỏ duyệt / xác nhận). Người rút nhận thông báo |
| 4 | (Suy ra cho nhất quán) Yêu cầu của **ví trung tâm** và yêu cầu cũ chưa gắn trung tâm vẫn do **admin** xử lý, cùng cơ chế chuyển khoản giả lập; admin xem được mọi yêu cầu nhưng chỉ đọc với yêu cầu của trung tâm |

## 2. Backend

### PaymentService

- `wallet_transactions.center_id` (đã có từ 2026-11-09) là nguồn gắn tiền với trung tâm: hoàn tiền / cọc giữ mang
  trung tâm của booking. `WalletService.findCenterBalances(walletId)` cộng theo `center_id` (khoản giữ / trả lại / đã trả
  của yêu cầu rút cũ không gắn trung tâm được trừ vào trung tâm lớn nhất để tổng khớp số dư khả dụng).
- `POST /wallet-withdrawals` nhận `centerId?` (ví khách / caregiver). Bỏ trống: phần không gắn trung tâm nếu đủ (admin),
  không thì trung tâm duy nhất; nhiều trung tâm → `6053`; vượt số dư tại trung tâm → `6054`. App mobile caregiver không
  gửi `centerId` vẫn chạy.
- Người xử lý: `ownerType ∈ {CUSTOMER, CAREGIVER}` + `centerId` → manager của trung tâm đó; còn lại → ADMIN. Sai người →
  403 `6055`.
- `POST /wallet-withdrawals/{id}/transfers` (mới) thay `/approve` + `/complete` (đã xóa): `PENDING|APPROVED → COMPLETED`,
  mã `SIM<yyMMddHHmmss><6 ký tự id>`, `WITHDRAWAL_PAID`. `/reject` giữ nguyên + báo người rút.
- `GET /wallet-withdrawals?scope=OWN|REVIEW`, `GET /wallet-withdrawals/statistics?scope&from&to&groupBy` (đang chờ, gửi /
  đã chuyển / từ chối trong kỳ, thời gian xử lý TB, theo loại người rút, chuỗi thời gian; manager `REVIEW` thêm
  `custody*` = tiền khách / caregiver gắn trung tâm còn trong ví). `GET /wallets/me/center-balances`.
- Thông báo: `payment_outbox` thêm `WITHDRAWAL_COMPLETED` / `WITHDRAWAL_REJECTED` (reference `withdrawal=<id>`, không có
  booking / payment) → `POST /api/v1/notifications` của BookingService, `refType = WALLET_WITHDRAWAL`.
- Schema: `schema/changes/2026-11-10-withdrawals-by-center.sql` (+ `payment.sql`).

### BookingService

- `/statistics/centers` và `/statistics/system` thêm (chỉ ở số tổng của kỳ): `occurrencesByStatus`, `bookingsByType`,
  `ratingDistribution` ("1".."5"), `trendGroupBy` (`DAY` khi kỳ < 62 ngày, còn lại `MONTH`), `trend[]`
  (`period, bookings, completedVisits, cancelledVisits, missedVisits` — booking theo ngày tạo, buổi theo giờ bắt đầu,
  đủ mọi mốc).
- `NotificationType` thêm `WALLET_WITHDRAWAL_COMPLETED`, `WALLET_WITHDRAWAL_REJECTED` —
  `schema/changes/2026-11-10_wallet_withdrawal_notifications.sql` (+ `booking.sql`).

## 3. Web FE

- `shared/ui/charts/` (không thêm thư viện): `ColumnChart`, `LineChart` (crosshair + tooltip, ←/→), `DonutChart`,
  `BarList`, `KpiTile`, `ChartCard`; bảng màu `--color-chart-1..5` trong `app.css`, đã chạy validator màu (CVD ΔE ≥ 8).
- `/admin/dashboard`: chọn kỳ (`?period=month|last30|year`), "Việc cần xử lý" (trung tâm chờ duyệt, caregiver chờ thẩm
  định, yêu cầu rút của trung tâm, khiếu nại chuyển lên), 6 số vận hành, xu hướng, kết quả buổi, top trung tâm / người
  chăm sóc, trạng thái & loại lịch đặt, đánh giá, khiếu nại, dòng tiền nền tảng, tài khoản theo vai trò, trung tâm,
  thẩm định hồ sơ.
- `/center-manager/statistics`: cùng bộ vận hành + doanh thu & ví trung tâm (biểu đồ, cơ cấu thu nhập) + chất lượng.
- `/center-manager/withdrawals` (mới, menu "Yêu cầu rút tiền") và `/admin/withdrawals` dùng chung
  `entities/wallet/withdrawal-queue.tsx`: dashboard rút tiền, bộ lọc, bảng, hộp xử lý (chuyển khoản giả lập / từ chối).
  Ví trung tâm có thẻ "Tiền trung tâm đang giữ hộ"; ví trung tâm / nền tảng có biểu đồ cơ cấu biến động.
- Form rút tiền của khách / caregiver có ô chọn trung tâm theo `center-balances`.
- Admin: menu "Khách hàng" + "Tài khoản" gộp thành một mục "Tài khoản" trong nhóm Khách hàng; menu tài khoản có "Hồ sơ"
  → `/admin/profile` (Thông tin cá nhân · Bảo mật: đổi mật khẩu + thiết bị đăng nhập). Hai panel hồ sơ chuyển từ
  `features/customer/my-profile` sang `features/shared/profile` (khách hàng dùng lại).

## 4. Việc của chủ dự án

1. Áp SQL cho DB dev: `PaymentService/schema/changes/2026-11-10-withdrawals-by-center.sql`,
   `BookingService/schema/changes/2026-11-10_wallet_withdrawal_notifications.sql`; rebuild PaymentService + BookingService.
2. Kiểm tra trên trình duyệt (chưa làm trong phiên này — backend đang chạy là bản cũ):
   - Khách hủy lịch (tiền hoàn vào ví) → `/customer/wallet` → Rút tiền: chọn trung tâm, gửi.
   - Quản lý trung tâm `/center-manager/withdrawals` → Xem → Chuyển khoản → yêu cầu "Đã chuyển khoản", mã `SIM…`; khách
     nhận thông báo "Rút tiền thành công", bấm mở ví. Từ chối → thông báo kèm lý do, tiền về lại ví.
   - `/admin/withdrawals`: yêu cầu của khách hiện "do Trung tâm … xử lý — bạn chỉ xem"; yêu cầu của ví trung tâm chuyển
     khoản được.
   - `/admin/dashboard`, `/center-manager/statistics`: đổi kỳ, xem biểu đồ ở 1440 px và 375 px.

## 5. Tồn đọng

- Chưa báo quản lý trung tâm khi có yêu cầu rút mới (BookingService chưa có cách gửi theo trung tâm từ PaymentService).
- Số "giữ hộ" của trung tâm tính theo `center_id` của sổ ví; khoản giữ của yêu cầu rút cũ (không gắn trung tâm) không
  trừ vào trung tâm nào.
- App mobile caregiver chưa có ô chọn trung tâm (caregiver thường chỉ có tiền của một trung tâm nên BE tự chọn).
- Mở `?withdrawal=<id>` chỉ hiện khi yêu cầu nằm trong trang danh sách đang xem (như trước).
