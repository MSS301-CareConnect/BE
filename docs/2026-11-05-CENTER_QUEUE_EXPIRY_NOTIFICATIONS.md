# 2026-11-05 — Hàng đợi trung tâm, hết hạn xác nhận, trung tâm thông báo

Làm theo đề xuất A/B/C ngày 2026-11-05, cộng thêm phần thông báo. Code đã viết xong và test đã xanh, nhưng **chưa commit, chưa chạy với backend thật**.

## Vì sao lịch BK-20261103-AMK4A2 "quá hạn mà vẫn chờ"

BookingService đã tắt từ 03/11 23:20 đến 05/11 11:50 (giờ VN). Scheduler có `initialDelay = 5m`, nên đến 11:55 mới chuyển lịch này sang `EXPIRED`. Ảnh chụp màn hình rơi đúng vào 5 phút đó. Cơ chế hết hạn vẫn đúng; chỗ thiếu là khoảng treo và giao diện.

## Backend (BookingService)

| # | Thay đổi | File chính |
|---|---|---|
| A1 | Quét hết hạn mỗi `1m`; lượt đầu chạy sau `30s` (`BOOKING_EXPIRY_INITIAL_DELAY`) | `BookingExpiryScheduler`, `application.yaml`, `BookingProperties` |
| A2 | Hết hạn khi có thao tác: accept / reject / phân công / caregiver nhận / từ chối sau hạn thì đóng booking ngay (transaction riêng) và trả 409 `5116 BOOKING_EXPIRED` | `BookingServiceImpl.checkNotExpired`, `expireLocked` |
| A3 | `bookings.expired_reason` (`BookingExpiredReason`), suy từ trạng thái lúc hết hạn; lý do trong lịch sử và thông báo ghi rõ bên nào chưa làm kịp | `BookingLifecycleServiceImpl` |
| A4 | Nhắc trung tâm trước hạn xác nhận / phân công, mặc định 720 và 120 phút (`BOOKING_CENTER_REMINDER_MINUTES`). Mỗi mốc nhắc một lần, mốc đã qua lúc bắt đầu chờ thì bỏ qua. Thông báo mới `CENTER_ACTION_DUE`; cột mới `bookings.center_reminder_minutes` | `CenterReminders`, `remindCenterAction` |
| A5 | Summary và detail có thêm `deadlineType`, `deadlineAt`, `expiredReason`; riêng summary có thêm `serviceNames` | `util/BookingDeadlines`, `BookingMapper` |
| A6 | `GET /bookings?statuses=A&statuses=B`, `sort=deadlineAt,asc\|desc`, và `GET /bookings/status-counts` (manager: trung tâm của mình; admin: `centerId` hoặc toàn hệ thống) | `BookingSpecifications`, `BookingSorts`, `countByStatus` |
| A7 | Giờ trong nội dung thông báo hiển thị dạng `HH:mm dd/MM/yyyy` theo giờ VN, không còn in Instant UTC thô | `BookingNotificationServiceImpl.formatTime` |
| A8 | Thống kê có thêm `responsiveness`: số lịch trung tâm đã trả lời, thời gian trả lời trung bình, số lịch hết hạn do trung tâm | `StatisticsServiceImpl` |
| N | Thông báo: `PATCH /notifications/read-state` (`{ids, read}`, đánh dấu nhiều cái đã đọc / chưa đọc), `PATCH /notifications/{id}/unread`, `DELETE /notifications/{id}`, `POST /notifications/deletions` (`{ids}`), `DELETE /notifications/read`. Đều là xóa mềm và chỉ áp lên thông báo của chính người gọi. Response có thêm `bookingId` / `occurrenceId`, suy từ ref (buổi, ca, báo cáo, khiếu nại, đánh giá) | `NotificationServiceImpl` |

Test: thêm 4 test trong `BookingApiTests` và sửa 1 test (giờ phân công sau hạn sẽ đóng booking ngay). Kết quả: `mvnw test` 127 chạy, 0 lỗi, 2 skip.

## Frontend

- `/center-manager/bookings`: thẻ hàng đợi, tab theo nhóm, thanh công cụ, bảng 5 cột (thẻ trên điện thoại), đếm ngược hạn, nút xác nhận / phân công / từ chối ngay trên dòng. Tự làm mới mỗi 30 giây.
- Trang chi tiết của manager: hạn có đếm ngược; khi quá hạn thì ẩn thao tác và giải thích lý do.
- Phía khách: trạng thái "đang đóng", lý do hết hạn, nút "Đặt lại lịch tương tự" và "Chọn trung tâm khác".
- Trang thống kê trung tâm có thẻ "Phản hồi lịch đặt".
- Thông báo: trang `/<portal>/notifications` cho cả 4 role. Chuông thông báo bấm vào sẽ mở trang liên quan, có nút xóa từng thông báo và link "Xem tất cả".
- Kết quả kiểm tra: `pnpm lint`, `typecheck`, `test` (413 test), `build` đều xanh. `prettier --check .` chỉ báo `pnpm-lock.yaml`, file này đã lệch format từ trước.
- Đã kiểm tra trên trình duyệt bằng Playwright với dữ liệu giả (fixture), ở cỡ 1440 và 390 px: không có lỗi console (ngoài luồng SSE bị chặn có chủ đích), không có cuộn ngang.

## Việc của owner

1. Chạy `psql -v ON_ERROR_STOP=1 -f schema/changes/2026-11-05_center_queue_and_notifications.sql` (`ddl-auto: validate` sẽ báo lỗi nếu thiếu cột). Trước khi chạy, kiểm tra tên CHECK của `notifications.type` bằng `\d notifications`.
2. Rebuild BookingService, rồi chạy E2E: tạo lịch → đợi qua hạn (có thể đặt policy hạn xác nhận 1 phút) → kiểm tra lịch tự đóng sau khoảng 1 phút, thông báo nhắc trước hạn, trang manager và trang khách.
3. Commit.
