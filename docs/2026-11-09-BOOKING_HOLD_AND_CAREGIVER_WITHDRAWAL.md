# 2026-11-09 — Giữ chỗ 5 phút, cọc ngay, tự phân công, caregiver xin hủy buổi, đổi caregiver cả booking

Làm song song với phiên ví (`2026-11-09-WALLET_PLAN.md`). Không đổi logic tính tiền hoàn / chuyển tiền (PaymentService
giữ nguyên `RefundCalculator`, `DepositAllocator`); Booking chỉ đổi **điều kiện / luồng** gọi hủy + hoàn tiền sẵn có.
Chưa commit (chủ repo tự commit).

## 1. Quyết định của chủ repo

| # | Quyết định |
|---|---|
| Q1 | Booking mới giữ chỗ **5 phút** chờ cọc (như vé máy bay / rạp). Hết hạn → hệ thống hủy, nhả lịch caregiver |
| Q2 | Ân hạn ẩn **2 phút**: tiền về trong 5–7 phút vẫn nhận (chống trường hợp khách trả đúng giây cuối) |
| Q3 | Caregiver xin hủy buổi có hiệu lực ngay, không cần duyệt; Center Manager phân người khác |
| Q4 | Khi caregiver rút: báo khách trung tính; khi có người thay: báo khách tên caregiver mới |
| Q5 | Lịch buổi của khách: bảng mặc định + nút chuyển lịch tháng |
| Q6 | Làm cả app mobile (caregiver) cho đồng bộ |
| A / Q-A1 | Khách **luôn trả cọc ngay** sau khi đặt (có hay chưa có caregiver); bỏ bước trung tâm / caregiver xác nhận trước khi trả. Booking cũ đang ở trạng thái chờ xác nhận vẫn chạy luồng cũ |
| B / Q-B1 | Tự phân công caregiver lúc tạo booking và bằng scheduler sau khi trả cọc / sau khi caregiver rút. Manager vẫn đổi tay được |
| Q-B2 | Buổi đã trả mà còn **12h** trước giờ bắt đầu vẫn chưa có người → hệ thống hủy buổi đó, hoàn 100 % qua luồng hủy sẵn có (`CENTER_CANCEL`) |
| — | Caregiver được xin hủy **từng buổi** hoặc **mọi buổi còn lại** của booking; mỗi buổi phải còn > 24h |
| — | Khách đổi caregiver = áp cho **mọi buổi còn lại** của booking (bỏ đổi từng buổi); manager duyệt như cũ |

## 2. Luồng mới

```
khách tạo booking ──▶ AWAITING_INITIAL_PAYMENT (depositDueAt = min(now + 5m, mốc chốt))
   │  có caregiverId → khóa lịch caregiver đó, buổi RESERVED
   │  không có → tự chọn caregiver rảnh (§3); không ai rảnh → buổi PENDING_ASSIGNMENT, báo trung tâm
   ├─ deposit-paid trước depositDueAt + 2m ─▶ CONFIRMED (buổi RESERVED → SCHEDULED; PENDING_ASSIGNMENT giữ nguyên)
   └─ quá depositDueAt + 2m ─▶ BookingExpiryScheduler (mỗi 15s) EXPIRE, buổi EXPIRED, nhả lịch
```

- Response chi tiết booking có `serverTime` để FE đếm ngược theo giờ server (`shared/lib/server-clock.ts`).
- `deposit-paid` tới sau hạn + ân hạn → 409, Payment hoàn lại theo luồng trả trễ sẵn có.
- SHIFT không còn tự `CAREGIVER_ACCEPT` lúc tạo (đã thay bằng cọc ngay).

## 3. Tự phân công (`CaregiverAssignmentService`)

- Ứng viên: Provider `GET /api/v1/caregivers/search` (cùng trung tâm, VERIFIED + ACTIVE, đủ kỹ năng các dịch vụ đã chọn),
  lọc rảnh mọi buổi qua `availability-checks` (chia lô 60 khung giờ, lấy giao) + không trùng buổi đã giữ ở Booking.
- Xếp hạng: ít giờ đã nhận trong khoảng của booking trước (`sumCaregiverLoads`), rồi rating cao hơn; lấy tối đa
  `BOOKING_AUTO_ASSIGN_CANDIDATES` (5) người rồi thử lần lượt dưới khóa lịch (`CaregiverScheduleGuard`: advisory lock
  caregiver + kiểm trùng) — người đầu tiên rảnh nhận **cả booking** (một caregiver / booking).
- Thời điểm: lúc tạo; `CaregiverAutoAssignScheduler` (mỗi `BOOKING_AUTO_ASSIGN_INTERVAL` = 1m) cho booking đã trả còn buổi
  chưa người, mỗi booking thử lại sau `BOOKING_AUTO_ASSIGN_RETRY` (15m, cột `bookings.auto_assign_attempted_at`); ngay sau
  khi caregiver rút. Không tìm được → báo trung tâm, nhắc lại tối đa mỗi `BOOKING_UNASSIGNED_NOTIFY_EVERY` (24h,
  `bookings.unassigned_notified_at`).
- Cùng scheduler: buổi đã trả, chưa người, bắt đầu trong `BOOKING_UNASSIGNED_CANCEL_LEAD` (12h) → `cancelUnassigned`
  (hủy buổi `SYSTEM`, `requestRefund(CENTER_CANCEL)`, chốt booking nếu hết buổi).
- Event `CAREGIVER_CHANGED` ghi chú "Hệ thống tự phân công"; manager vẫn dùng `PATCH /bookings/{id}/caregiver` để đổi.

## 4. Caregiver xin hủy buổi (aggregate `CaregiverWithdrawal`)

| Role | API | Ghi chú |
|---|---|---|
| CAREGIVER | `POST /api/v1/caregiver-withdrawals` `{occurrenceIds[], reasonCode: PERSONAL\|OTHER, reasonDetail?}` | Mọi buổi cùng một booking, của chính caregiver, SCHEDULED / ACKNOWLEDGED, chưa có session, bắt đầu sau > 24h (`BOOKING_CAREGIVER_WITHDRAWAL_LEAD`). OTHER bắt buộc `reasonDetail` |
| CAREGIVER, CENTER_MANAGER | `GET /api/v1/caregiver-withdrawals?bookingId=&pendingOnly=&page=&size=` | Caregiver: của mình; manager: trung tâm mình. Mỗi dòng có `state` = PENDING / REASSIGNED / CLOSED suy ra từ buổi; `pendingOnly=true` chỉ lấy PENDING |

Xử lý (khóa booking → buổi): buổi giữ caregiver cũ nhưng `needsReassignment = true` (caregiver cũ vẫn thấy cờ "chờ thay
người"), event `CAREGIVER_WITHDRAWN`, báo trung tâm (`CAREGIVER_UNASSIGNED`) + báo khách trung tính, rồi thử tự phân công
ngay. Người thay được gán (tự động hoặc manager) → bỏ cờ, báo khách tên caregiver mới.

Mã lỗi: `5260 WITHDRAWAL_TOO_LATE`, `5261 WITHDRAWAL_ALREADY_REQUESTED` (unique `(occurrence_id, caregiver_id)`),
`5262 WITHDRAWAL_NOT_ALLOWED`, `5263 WITHDRAWAL_REASON_DETAIL_REQUIRED`, `5264 WITHDRAWAL_REASON_REQUIRED`.

`GET /api/v1/booking-occurrences` thêm `bookingId` (caregiver xem mọi buổi của một booking); response buổi có
`needsReassignment`.

## 5. Khách đổi caregiver cả booking

`POST /api/v1/bookings/{id}/caregiver-change-requests` chỉ nhận `scopeType = ALL_REMAINING` (khác → 400 `5175`). Duyệt
(`/caregiver-change-requests/{id}/approve`): khóa booking, đọc lại yêu cầu, áp cho mọi buổi WORKABLE còn ≥ 12h (`5173`),
bỏ buổi đã thuộc người mới, khóa lịch người mới qua `CaregiverScheduleGuard`, đổi caregiver cấp booking, báo khách / người
cũ / người mới. Sửa luôn lỗi cũ: `BookingEvent` thiếu `createdAt` (NOT NULL). Gateway thêm route
`/api/v1/caregiver-change-requests/**` (trước đó manager duyệt bị 404).

## 6. Schema

`schema/changes/2026-11-09_caregiver_withdrawals.sql` (đã gộp vào `schema/booking.sql`): bảng `caregiver_withdrawals`,
cột `bookings.auto_assign_attempted_at`, `bookings.unassigned_notified_at`, CHECK `booking_events` thêm
`CAREGIVER_WITHDRAWN`. Giữ chỗ / cọc ngay không cần đổi schema. Đã áp vào DB dev Docker 2026-11-09, booking-service khởi
động qua `ddl-auto: validate`.

## 7. FE / mobile

- FE khách: đồng hồ đếm ngược giữ chỗ (`entities/booking/hold-countdown.tsx`), ngày giờ rõ hơn ở form giữ chỗ, lịch buổi
  dạng bảng + lịch tháng (`booking-visit-schedule.tsx`), đổi caregiver cả booking (`caregiver-change-dialog.tsx`).
- FE caregiver: danh sách buổi gom theo booking (`visit-booking-groups.tsx`), trang booking `/caregiver/visits/bookings/:bookingId`,
  xin hủy một / mọi buổi còn lại (`withdraw-dialog.tsx`, `visit-withdraw.tsx`).
- FE manager: hàng đợi caregiver xin hủy (`manager-withdrawal-queue.tsx`) trên trang quản lý booking + chi tiết booking.
- Mobile caregiver: `features/visits` — thẻ gom theo booking, màn `BookingVisits`, `WithdrawModal` / `VisitWithdrawSection`.

## 8. Kiểm chứng (2026-11-09)

| Phần | Kết quả |
|---|---|
| BookingService `mvnw test` | pass (test mới: giữ chỗ / quá hạn / ân hạn, rút buổi, đổi caregiver cả booking, tự phân công + manager đổi, hủy 12h hoàn 100 %, rút rồi tự phân người khác) |
| ApiGateway `mvnw test` | pass (sau khi phiên AI sửa gateway: một lần fail ngắt quãng ở `paymentAndBookingOperationRoutesForwardToOwners` — NPE trong JDK HttpClient, chạy lại pass) |
| PaymentService | 4 test hoàn tiền fail **có sẵn** (`RefundCalculator`, sửa 2026-11-05) — không thuộc phiên này |
| FE `tsc` / lint / vitest | tsc pass; không thêm lỗi lint; 2 test fail có sẵn (`booking-reason-dialog` đổi nhãn 2026-11-06) |
| Mobile `npm run check` | jest 252/252, tsc, lint 0 lỗi; prettier chỉ báo 104 file CRLF có sẵn |
| Docker dev | SQL đã áp, booking-service + api-gateway build lại, khởi động sạch |
| E2E có đăng nhập | **Chưa làm** — cần tài khoản dev (chủ repo đăng nhập) hoặc cho phép seed tài khoản test vào DB dev |

## 9. Còn tồn đọng

- E2E thật trên trình duyệt / thiết bị (cần đăng nhập).
- `scripts/e2e/e2e.py` phase e vẫn theo luồng cũ (PENDING → accept → AWAITING_DEPOSIT) — lỗi thời từ trước phiên này.
- Gửi phiên ví: `PaymentServiceImpl.upsertOccurrenceFinancials` chụp caregiver của buổi lúc trả cọc → buổi chưa có người /
  được tự phân công sau sẽ có caregiver null ở bản chụp (ảnh hưởng người nhận cọc bị giữ). Không sửa ở phiên này.
- `POST /bookings/{id}/emergency-reassignment` chưa kiểm lịch rảnh / khóa lịch người mới (có từ trước, chỉ báo lại).
