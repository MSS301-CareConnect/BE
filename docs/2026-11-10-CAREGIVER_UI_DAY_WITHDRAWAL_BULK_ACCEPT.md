# 2026-11-10 — Caregiver: hủy cả ngày, nhận nhanh nhiều buổi, làm lại giao diện portal caregiver

Phiên chạy song song với phiên admin / center manager: không sửa `features/admin/**`, `features/center-manager/**`,
`entities/booking/caregiver-absence-queue.tsx` (manager vẫn dùng). Chưa commit (chủ repo tự commit).

## 1. BookingService

| Thay đổi | Chi tiết |
|---|---|
| Hủy cả ngày | `POST /api/v1/caregiver-withdrawals`: với mỗi ngày (giờ VN) có buổi trong lệnh, mọi buổi khác của caregiver trong cùng booking, cùng ngày, còn `SCHEDULED` / `ACKNOWLEDGED` và chưa xin hủy phải nằm trong lệnh — thiếu → `409 5265 WITHDRAWAL_WHOLE_DAY_REQUIRED`. Ngày chỉ một buổi vẫn hủy lẻ được. Mỗi buổi vẫn phải còn > 24h (`5260`) nên ngày có buổi dưới 24h không hủy được |
| Nhận nhanh | `POST /api/v1/booking-occurrences/acknowledgements` `{occurrenceIds[1..100]}` (CAREGIVER): cùng một booking, tất cả hoặc không; khác booking / không được giao → 404, buổi không còn SCHEDULED hoặc đã tới giờ → 409 `INVALID_OCCURRENCE_TRANSITION`. Dùng chung helper `markAcknowledged` với nhận từng buổi |
| Test | `BookingApiTests.caregiverAcknowledgesManyVisitsAndWithdrawsWholeDays` |

Không đổi schema. Cần build lại image `booking-service` để FE gọi được API mới trên Docker dev.

## 2. FE caregiver (`FE-careconnect/app/features/caregiver`)

- **Hôm nay**: đầu trang có chip tóm tắt (số buổi + giờ hôm nay, còn lại, việc chờ trả lời); "Tuần này" vẽ cột theo
  giờ, chồng đã xong / chưa làm / vắng ca, viền đứt nét là khung giờ làm việc; rê chuột / focus / chạm cột hiện tooltip
  số liệu; chuyển tuần; thanh "mức kín khung giờ làm việc" (giờ có buổi / giờ đăng ký). "Cần bạn xử lý" thêm buổi vắng
  ca chưa giải trình.
- **Buổi chăm sóc**: khối vắng ca gộp theo booking, thu gọn được, 3 booking / trang (`absence-panel.tsx`); thẻ booking
  có "N buổi vắng ca", nút "Nhận tất cả", 8 thẻ / trang; buổi `CAREGIVER_MISSED` mang huy hiệu trạng thái hồ sơ vắng
  (chưa giải trình / chờ xét / có lý do / vi phạm) ở thẻ buổi, dòng buổi và chi tiết buổi (thẻ giải trình).
- **Trang booking** (`/caregiver/visits/bookings/:id`): hero forest (thời gian, địa chỉ, dịch vụ, 4 số liệu, Nhận tất
  cả, Xin hủy các buổi còn lại); xem "Theo ngày" (mỗi ngày một khối: ngày, số buổi, khung giờ, dịch vụ trong ngày,
  Nhận cả ngày, Xin hủy cả ngày / buổi; 5 ngày / trang, sắp tới / đã qua) hoặc "Lịch tháng" (`?view=calendar`, mỗi
  ngày một chip tóm tắt booking tô màu theo trạng thái, chọn ngày mở danh sách buổi bên cạnh).
- **Xin hủy**: popup mới (buổi theo ngày, buổi giữ lại, lý do dạng thẻ); ở chi tiết buổi, ngày có nhiều buổi chỉ cho
  "Xin hủy cả ngày (N buổi)"; lệnh dài chia lô ≤ 60 buổi không tách ngày.
- **Lịch làm việc**: tuần = 7 cột liệt kê (dải giờ nhỏ + chip khung giờ làm việc / buổi / ngày nghỉ) thay cho lưới giờ
  kéo dài; tháng = mỗi ô tối đa 3 dòng gọn. "Khung rảnh" đổi thành "Khung giờ làm việc".
- **Sidebar**: Ví / Thu nhập / Thông báo không chọn mục nào; topbar điện thoại hiện tên trang.
- **Phân trang**: yêu cầu đặt lịch (BE `page`, 10 / trang), tiền cọc được hưởng (BE `page`), thẻ booking, ngày của
  booking, vắng ca, ca đăng ký (9 / trang), ngày nghỉ (8), chứng chỉ (6) — các danh sách sau trả về toàn bộ hoặc lọc ở
  trình duyệt nên phân trang phía client.

## 3. Còn tồn đọng

- Chưa kiểm tra trên trình duyệt với tài khoản caregiver thật (cần đăng nhập + image booking-service mới).
- App mobile caregiver chưa áp quy tắc hủy cả ngày / nhận nhanh (BE đã chặn `5265`, app sẽ hiện lỗi chung).
- Ca mở (`GET /work-shifts`) và đăng ký ca vẫn đọc 100 bản đầu rồi phân trang ở trình duyệt.
