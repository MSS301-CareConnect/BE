# 2026-11-11 — Chấm công ca chăm sóc, cờ vị trí, xác nhận ca tự đóng

Quyết định của owner: vị trí chỉ **gắn cờ** (không chặn check-in); về sớm chỉ **ghi nhận thời gian thực tế** (không đổi
tiền); ca hệ thống tự đóng **giữ quyết toán** tới khi trung tâm xác nhận.

## BookingService
- Check-in (`POST /care-sessions`): chặn khi caregiver còn ca khác chưa check-out (409 `5806`); lưu `lateMinutes`;
  thiếu tọa độ → `locationFlag=NO_LOCATION`; có tọa độ và hồ sơ người bệnh có tọa độ (buổi dùng địa chỉ booking) → lưu
  `checkInDistanceMeters`, xa hơn `booking.session.check-in-radius-meters` (300) → `OUT_OF_RANGE`. Có cờ → báo trung tâm
  (`SESSION_LOCATION_FLAGGED`).
- Kết thúc ca (mọi đường): lưu `earlyLeaveMinutes`.
- Tự đóng (D21): thêm `completionReview=PENDING`. `GET /care-sessions?review=PENDING` (manager, theo trang) và
  `POST /care-sessions/{id}/review` `{delivered, note}`: delivered → `CONFIRMED`; không → `NOT_DELIVERED`, buổi
  `COMPLETED → CAREGIVER_MISSED`, hoàn 100 % buổi cho khách, ghi vắng ca nguồn `SESSION_NOT_DELIVERED` (ghi chú ≥ 10 ký tự,
  400 `5819`; kết luận lại 409 `5820`).
- payment-context: mỗi buổi có `settlementHeld`.
- SQL: `BookingService/schema/changes/2026-11-11_care_session_attendance.sql` (**chưa áp vào DB dev**).

## PaymentService
- `WalletSettlementServiceImpl` bỏ qua buổi `settlementHeld=true`; lượt quét sau xét lại.

## FE web
- Chi tiết buổi của manager: đến trễ, về sớm, khoảng cách, cờ vị trí, trạng thái xác nhận.
- `/center-manager/bookings`: hàng đợi "Ca tự đóng chờ xác nhận" ở cột phải.

## Chưa làm / giới hạn
- Buổi có địa chỉ riêng (`occurrence.serviceAddress`) hoặc hồ sơ không có tọa độ: không đối chiếu khoảng cách (chỉ gắn cờ
  khi thiếu tọa độ check-in). Cần lưu tọa độ theo địa chỉ để phủ hết.
- Booking một buổi đã `COMPLETED` rồi bị kết luận không thực hiện: buổi thành CAREGIVER_MISSED nhưng booking vẫn COMPLETED.
- Giai đoạn 4 (khách bấm "chưa thấy người đến", cửa sổ phản ánh 24 giờ) chưa làm. Mobile chưa có câu tiếng Việt cho 5806.
- Chưa rebuild booking-service / payment-service; chưa kiểm trên trình duyệt.

---

## Tiến độ (cập nhật cuối phiên 2026-11-11) — đọc phần này để làm tiếp

### Đã xong và đang chạy trên dev
- Giai đoạn 1–3 (chặn ca trùng, ghi trễ / về sớm, cờ vị trí, ca tự đóng chờ trung tâm xác nhận + giữ quyết toán).
- SQL `2026-11-11_care_session_attendance.sql` **đã áp** vào DB booking dev; `booking-service` + `payment-service` **đã build
  lại** (healthy) — bản build này CHƯA gồm giai đoạn 4.
- Form "Báo cáo chăm sóc" của caregiver: thẻ trạng thái + popup giữa màn hình, hai cột (`entities/aftercare/care-report-card.tsx`,
  `care-report-form.tsx` có `variant='dialog'`).

### Giai đoạn 4 (phía khách) — ĐANG DỞ

**BE BookingService: xong, test xanh (toàn bộ suite, exit 0), chưa áp SQL / chưa build lại.**
- `POST /api/v1/booking-occurrences/{id}/customer-reports` body `{note?}` (CUSTOMER chủ booking, khách khác → 404):
  - buổi SCHEDULED / ACKNOWLEDGED, đã tới giờ, chưa có ca → chỉ ghi nhận + báo trung tâm (`CUSTOMER_VISIT_REPORT`);
  - buổi COMPLETED trong `booking.session.customer-report-window` (24h) kể từ `actualEndAt` → ghi chú ≥ 10 ký tự (400 `5825`),
    ca chuyển `completionReview=PENDING` (vào hàng đợi "chờ trung tâm xác nhận" sẵn có của manager);
  - khác → 409 `5823`; báo lần hai → 409 `5824`.
- `booking_occurrences.customer_reported_at`, `customer_report_note`; `OccurrenceResponse.customerReportedAt`;
  `CareSessionResponse.customerReportNote`.
- payment-context `settlementHeld` = ca đang PENDING **hoặc** buổi COMPLETED còn trong 24h phản ánh
  (`BookingServiceImpl.findSettlementHeldIds`) → trung tâm nhận tiền buổi chậm 24h. PaymentService không cần đổi thêm.
- SQL mới: `BookingService/schema/changes/2026-11-11_customer_visit_report.sql` (**chưa áp**).
- Test: `BookingApiTests.customerReportsAVisitNobodyCameToAndDisputesAFinishedOneOnce`.

**FE web: mới xong hợp đồng, chưa có giao diện.**
- Đã sửa `openapi/openapi.yaml` (operation `reportBookingOccurrenceByCustomer`, `customerReportedAt`, `customerReportNote`,
  loại thông báo `CUSTOMER_VISIT_REPORT`) và đã chạy `pnpm api:generate` (0 lỗi format). **Chưa chạy lại tsc / lint / test.**

### Việc còn lại (theo thứ tự)
1. FE `entities/booking/booking-api.ts`: thêm `reportVisitByCustomer(occurrenceId, note?)` gọi `reportBookingOccurrenceByCustomer`.
2. FE `features/customer/bookings/components/booking-occurrence-detail-dialog.tsx`:
   - buổi SCHEDULED / ACKNOWLEDGED đã quá `startAt`, chưa `customerReportedAt` → nút "Chưa thấy người chăm sóc đến";
   - buổi COMPLETED, `actualEndAt` trong 24h, chưa báo → nút "Buổi không được thực hiện đúng" mở `BookingReasonDialog`
     (`minLength={10}`); đã báo → dòng "Đã gửi phản ánh lúc …". Sau khi gửi: invalidate `bookingKeys.detail(booking.id)`.
   - 24h đang là cấu hình BE; FE dùng hằng số kèm comment `shortcut:` hoặc cứ hiện nút và để BE trả 409.
3. FE `manager-session-review-queue.tsx`: hiện `customerReportNote` khi có; sửa câu mô tả hàng đợi (không chỉ "hệ thống tự
   đóng" mà cả "khách phản ánh") trong `shared/i18n/center-manager/withdrawal-resources.ts` (`managerSessionReview*`).
4. i18n: `booking.errors.e5823 / e5824 / e5825` (vi + en) và thêm 3 mã vào `KNOWN` trong `entities/booking/booking-format.ts`;
   copy nút / thông báo ở `shared/i18n/customer/booking-detail-resources.ts`.
5. Kiểm: `npx tsc --noEmit -p .` (lỗi sẵn có duy nhất: `customer/booking/components/schedule-pattern.tsx`), `npx eslint`,
   `npx vitest run`; thêm một test nhỏ cho nút của khách.
6. Áp SQL `2026-11-11_customer_visit_report.sql` rồi mới build lại `booking-service`:
   `docker exec -i careconnect-booking-postgres-1 sh -c 'psql -v ON_ERROR_STOP=1 -1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < BookingService/schema/changes/2026-11-11_customer_visit_report.sql`
   → `docker compose up -d --build booking-service` (chạy trong `BE/`).
7. Mobile caregiver: câu tiếng Việt cho lỗi 5806. Cập nhật `FE-careconnect/docs/ARCHITECTURE.md` cho giai đoạn 1–4 và form báo cáo.

### Lưu ý
- Không commit / push (owner tự commit).
- Test trang chính sách trung tâm (`manager-policy-page.test.tsx`) đang lỗi do phiên khác sửa `center-booking-policy-form.tsx`.
- Chưa kiểm trên trình duyệt bất kỳ phần nào trong ngày 2026-11-11.

### Cập nhật cuối 2026-11-11 (phiên tiếp theo)
- Xong bước 1–5 và 7: `reportVisitByCustomer`; nút "Chưa thấy người chăm sóc đến" / "Buổi không được thực hiện đúng" +
  dòng "Đã gửi phản ánh lúc …" trong dialog buổi của khách; hàng đợi manager hiện ghi chú khách + đổi câu mô tả; i18n
  `e5823–e5825`; `BookingReasonDialog` cho phép lý do không bắt buộc khi `minLength=0`; test
  `booking-occurrence-detail-dialog.test.tsx`; mobile có câu cho 5806 (`sessionStillOpen`); ARCHITECTURE.md đã cập nhật.
- Bước 6 xong 2026-11-11: đã áp `2026-11-11_customer_visit_report.sql`, `booking-service` build lại (healthy).
- Chưa kiểm trên trình duyệt.
