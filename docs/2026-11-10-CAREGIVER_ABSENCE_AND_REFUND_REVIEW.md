# 2026-11-10 — Caregiver vắng ca + rà soát hoàn tiền của mọi role

Phạm vi: BookingService, PaymentService, ApiGateway, web FE (4 role). App mobile caregiver **chưa** có màn giải trình
(caregiver giải trình trên web). Chưa commit (chủ repo tự commit).

## 1. Quyết định của chủ dự án (2026-11-10)

| # | Chốt |
|---|---|
| 1 | Hoàn tiền do **lỗi phía trung tâm** (caregiver vắng, trung tâm / admin hủy, hệ thống hủy vì không có người, trung tâm bị khóa, khách từ chối người thay thế khẩn cấp) → **ví khách** (`refunds.channel = WALLET`), như khách tự hủy. Khiếu nại, quá hạn phần còn lại, trả trễ / trùng vẫn qua cổng |
| 2 | Caregiver vắng: khách nhận lại **100 %** tiền đã trả của buổi. Không bồi thường thêm, không phạt tiền trung tâm |
| 3 | Mỗi buổi vắng tạo **hồ sơ vắng ca**: caregiver giải trình, Center Manager kết luận (có lý do chính đáng / vi phạm). Admin xem toàn hệ thống. Không tự động khóa caregiver |
| 4 | Làm BE + web 4 role; mobile để sau |

## 2. Luồng caregiver vắng ca

```
tới giờ bắt đầu, chưa check-in
  └─ job 1 phút (late-warning-scan-interval): CHECK_IN_LATE → caregiver, trung tâm, khách (một lần / buổi)
quá start + checkInLateMinutes (mặc định 30'), vẫn chưa check-in, không có báo "khách vắng"
  └─ job 5 phút: buổi → CAREGIVER_MISSED
       ├─ outbox REFUND:CAREGIVER_MISSED:<occurrenceId> → Payment hoàn 100 % vào ví khách
       ├─ caregiver_absences: PENDING (nguồn MISSED_CHECK_IN)
       ├─ báo khách (tiền hoàn vào ví), caregiver (cần giải trình), trung tâm (cần kết luận)
       └─ hết buổi: có buổi đã phục vụ → booking COMPLETED; không buổi nào → CANCELLED
caregiver báo "khách vắng" nhưng trung tâm bác
  └─ cũng CAREGIVER_MISSED + hoàn 100 % + hồ sơ vắng ca (nguồn NO_SHOW_REPORT_REJECTED)
```

- Còn dưới 2 giờ mà caregiver chưa bấm nhận ca: ngoài nhắc caregiver, trung tâm cũng được báo để gọi xác nhận.
- Buổi `CAREGIVER_MISSED`: trung tâm và nền tảng **không** nhận tiền (job cộng tiền buổi bỏ qua), caregiver không nhận gì.
- Các buổi còn lại của booking giữ nguyên người; trung tâm đổi người bằng các API sẵn có nếu cần (khách cũng có thể yêu
  cầu đổi caregiver cả booking).

### Hồ sơ vắng ca (`CaregiverAbsence`)

| Role | API | Ghi chú |
|---|---|---|
| CAREGIVER, CENTER_MANAGER, ADMIN | `GET /api/v1/caregiver-absences?status&centerId&caregiverId&bookingId&from&to&page&size` | Caregiver: của mình; manager: trung tâm trong token; admin: mọi trung tâm (`centerId` tùy chọn) |
| như trên | `GET /api/v1/caregiver-absences/statistics` · `GET /{id}` | Số lần theo PENDING / EXCUSED / CONFIRMED; ngoài phạm vi → 404 |
| CAREGIVER | `POST /{id}/explanation {explanation 10–1000}` | Gửi / sửa tới khi trung tâm kết luận (sau đó 409 `5271`) |
| CENTER_MANAGER | `POST /{id}/conclusion {decision: EXCUSED\|CONFIRMED, note?}` | Kết luận một lần; EXCUSED bắt buộc ghi chú (`5274`) |

Mã lỗi `5270`–`5274`. Thông báo mới: `CHECK_IN_LATE`, `CAREGIVER_ABSENCE_UPDATED`. Mỗi buổi tối đa một hồ sơ (unique
`occurrence_id`).

## 3. Rà soát hoàn tiền — bảng tổng hợp sau khi sửa

| Tình huống | Khách nhận | Kênh | Phần bị giữ thuộc về |
|---|---|---|---|
| Khách hủy, còn ≥ mốc của trung tâm (24h) | 100 % số đã trả của buổi | Ví | — |
| Khách hủy, từ 2h tới < mốc | 70 % (theo policy) | Ví | Caregiver của buổi; chưa có người → trung tâm |
| Khách hủy, còn < 2h | 0 % (`LATE_NO_REFUND`) | — | như trên |
| Trung tâm / admin hủy; hệ thống hủy vì chưa có người (12h); trung tâm bị khóa | 100 % | **Ví** (trước: cổng) | — |
| Caregiver vắng (`CAREGIVER_MISSED`) | 100 % | **Ví** (trước: cổng) | — |
| Khách từ chối người thay thế khẩn cấp | 100 % | **Ví** | — |
| Quá hạn trả phần còn lại | phần cọc không bị giữ (`overduePaymentForfeitPercent`) | Cổng | Caregiver đang được giao; chưa có → trung tâm |
| Khiếu nại được admin chấp nhận | số admin quyết | Cổng | Trừ ví trung tâm / buổi chưa đối soát / hoa hồng |
| Trả trễ, trả trùng | toàn bộ khoản đó | Cổng (đúng giao dịch) | — |
| Khách vắng (`CUSTOMER_NO_SHOW`) | 0 | — | Trung tâm (trừ hoa hồng), như buổi hoàn tất |

## 4. Lỗi đã sửa

1. **Mất tiền hoàn khi khách từ chối người thay thế khẩn cấp** — khóa idempotency là chuỗi cố định nên chỉ lần từ chối
   đầu tiên trên toàn hệ thống được xếp hoàn. Nay khóa theo id buổi, luồng khóa booking → buổi, đi qua
   `cancelOccurrences` (ghi bên hủy, giờ hủy, sự kiện, thông báo) và chốt booking khi hết buổi. Nhánh "dời lịch" kiểm
   giờ mới ở tương lai, cùng thời lượng và caregiver rảnh.
2. **Tiền hoàn do lỗi trung tâm không tới khách ở chế độ `SIMULATED`** — chuyển sang ví (quyết định 1).
3. **Hoàn khiếu nại bị trả hai lần** — số hoàn không ghi vào buổi nào nên buổi chưa đối soát vẫn cộng đủ cho trung tâm.
   Nay: trừ ví trung tâm phần đã nhận → phần còn lại ghi `refunded_amount` vào các buổi chưa đối soát → nếu vẫn dư thì
   trừ lại hoa hồng nền tảng đã thu của chính booking đó. Tổng chi không vượt tổng đã thu.
4. **Hủy dưới 2 giờ hiển thị "giữ một phần" dù hoàn 0 %** — thêm rule `LATE_NO_REFUND`; mốc của trung tâm ngắn hơn 2 giờ
   vẫn được tôn trọng. 4 test PaymentService đỏ từ 2026-11-05 đã xanh.
5. **Cọc bị giữ vì quá hạn vào sai ví** — job quá hạn đọc lại caregiver từng buổi từ Booking trước khi giữ cọc (buổi
   được phân / đổi người sau lúc trả cọc).
6. Dời lịch buổi xóa mốc đã nhắc ca / đã cảnh báo trễ để giờ mới được nhắc lại.

## 5. Schema

- Booking: `schema/changes/2026-11-10_caregiver_absences.sql` (đã gộp vào `booking.sql`) — bảng `caregiver_absences`,
  cột `booking_occurrences.late_warned_at`, CHECK `notifications.type`, backfill buổi `CAREGIVER_MISSED` cũ thành hồ sơ
  PENDING. **Chưa áp vào DB dev.**
- Payment: không đổi schema (`refunds.channel`, `LATE_NO_REFUND` là giá trị chuỗi sẵn có cột).

## 6. Web

- Manager `/center-manager/bookings`: khối "Người chăm sóc vắng ca" (giải trình, miễn trừ / ghi nhận vi phạm).
- Caregiver `/caregiver/visits`: khối "Bạn bị ghi nhận vắng ca" (gửi / sửa giải trình).
- Admin `/admin/caregiver-absences` (menu Vận hành): số liệu + danh sách toàn hệ thống theo trạng thái, chỉ xem.
- Khách: chi tiết buổi `CAREGIVER_MISSED` nói rõ tiền đã hoàn vào ví; nhãn hoàn tiền `LATE_NO_REFUND`.

## 7. Kiểm chứng (2026-11-10)

| Phần | Kết quả |
|---|---|
| PaymentService `mvnw -o test` | 56 / 56 (trước: 53, 4 đỏ) — mới: vắng ca / trung tâm hủy vào ví, khiếu nại không trả hai lần, cọc quá hạn đúng ví |
| BookingService `mvnw -o test` | 150 chạy, 0 lỗi, 2 skip có sẵn — mới: cảnh báo trễ + hồ sơ vắng ca + phạm vi, bác báo vắng, từ chối người thay thế |
| ApiGateway `mvnw -o test` | 25 / 25 |
| FE | xem báo cáo cuối phiên |

Chưa làm: chạy trên PostgreSQL thật (`-D*.testing.postgres=true`), áp SQL + rebuild Docker, E2E có đăng nhập.

## 8. Tồn đọng

- Mobile caregiver chưa có màn giải trình và chưa hiện thông báo loại mới bằng nhãn riêng.
- Số lần vi phạm chưa hiện ở trang chi tiết caregiver của manager (đã có API `statistics?caregiverId=`).
- Hoàn khiếu nại / quá hạn vẫn qua cổng: ở `PAYMENT_REFUND_MODE=SIMULATED` khách không thấy tiền.
- `REFUND_COMPLETED` vẫn chưa được gửi (Payment không gọi thông báo của Booking); khách biết qua thông báo vắng ca / hủy.
- `emergency-reassignment` vẫn chưa kiểm lịch rảnh của người thay thế (có từ trước).
- Luật "< 2 giờ không hoàn" là hằng số trong `RefundCalculator`, chưa nằm trong center booking policy.
