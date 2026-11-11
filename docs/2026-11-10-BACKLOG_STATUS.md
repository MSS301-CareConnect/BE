# 2026-11-10 — Tình trạng các việc tồn đọng (sau đợt caregiver vắng ca + rà soát hoàn tiền)

Nguồn: mục 8 "Tồn đọng" của `2026-11-10-CAREGIVER_ABSENCE_AND_REFUND_REVIEW.md`. Chưa commit, chưa push (chủ dự án tự commit).
Cập nhật lần cuối: 2026-11-10 (lượt 2: làm hết mục 4–9, áp SQL, rebuild, E2E API; chưa commit).

## Đã làm

| # | Việc | Kết quả | Kiểm chứng |
|---|---|---|---|
| 1 | Điều phối người thay thế khẩn cấp không kiểm lịch rảnh | `BookingOccurrenceServiceImpl.emergencyReassign` viết lại: hỏi Provider (đủ kỹ năng, có lịch, không nghỉ) **ngoài** transaction, rồi khóa booking → buổi → lịch người mới, kiểm trùng giờ / travel-buffer với booking khác (409 `5215` / `5248`). Đổi người qua `assignCaregiver`; ghi `CAREGIVER_CHANGED` từng buổi; báo khách, người mới, người cũ. Bỏ qua buổi đã tới giờ / đã thuộc người mới | Test `emergencyReplacementRejectsACaregiverAlreadyBookedAtTheSameTime` + `rejectingAnEmergencyReplacement…` xanh |
| 2 | Không gửi thông báo "Hoàn tiền thành công" | PaymentService: outbox `REFUND_COMPLETED` → `POST /api/v1/notifications` của Booking (token SERVICE). Một thông báo cho mỗi **lệnh** hoàn (gộp mọi dòng cùng idempotency key, ghi tổng tiền; nói rõ vào ví hay về phương thức thanh toán). Dispatcher khóa `booking_financials` trước khi ghi kết quả để chỉ dòng cuối xếp thông báo. Booking từ chối thông báo → chỉ FAILED, không đụng tiền | Test `customerIsNotifiedOncePerCompletedRefundOrder` (một thông báo / lệnh, tổng 600.000, ví vs cổng, lệnh chưa xong thì chưa báo). PaymentService **57 / 57** xanh |
| 3 | Mốc "hủy dưới 2 giờ không hoàn" cấu hình theo trung tâm — **BE + FE** | Booking: cột `center_booking_policies.late_cancel_no_refund_minutes` (mặc định 120, `0 ≤ x ≤ refund_threshold_minutes`, sai → 400 `5413`, thiếu → 400 `5414`), có trong request / response policy, mặc định env `BOOKING_POLICY_LATE_CANCEL_NO_REFUND_MINUTES`, gửi qua `payment-context`. Payment: chụp vào `PolicySnapshot.lateCancelNoRefundMinutes`; `RefundCalculator` dùng giá trị snapshot, snapshot cũ thiếu → 120 phút | PaymentService 57 / 57 xanh (test mới: mốc 30 phút, mốc 0, snapshot cũ null, quote với mốc 60 phút). BookingService `mvnw -o test` **151 test, 0 lỗi, 2 skip** (2026-11-10). FE (2026-11-10): `openapi.yaml` + `pnpm api:generate`; ô "Hủy sát giờ, không hoàn" trong form chính sách (giờ, ≤ mốc hủy sớm và ≤ 24 giờ) + test; bảng hoàn tiền thêm hàng "Dưới X giờ → Không hoàn", trang trung tâm và bước xem lại booking thêm dòng mốc này; 3 ô mức hoàn trong hộp thoại hủy đọc từ chính sách (bỏ "100% / 30% / 24h / 2h" viết cứng; mốc 0 → chỉ 2 ô); nhãn `LATE_NO_REFUND` bỏ số 2 giờ. Vitest 19 file / 96 test xanh, `tsc` sạch |

## Làm tiếp cùng ngày (2026-11-10, lượt 2)

| # | Việc | Kết quả | Kiểm chứng |
|---|---|---|---|
| 4 | Số lần vắng ca trên trang chi tiết caregiver của quản lý | FE `features/center-manager/caregivers/components/caregiver-absence-history.tsx`: 4 con số (tổng / chờ / có lý do / vi phạm) từ `GET /caregiver-absences/statistics?caregiverId=`, 5 lần gần nhất (thẻ dùng chung `CaregiverAbsenceCard`), link "Kết luận các lần chờ" về hàng đợi trang lịch đặt khi còn lần chờ | Test `caregiver-absence-history.test.tsx` (2 ca) xanh |
| 5 | App mobile: màn giải trình vắng ca | `careconnect-mobile/src/features/absences` (models, repository, `AbsencesScreen`, `AbsenceExplainScreen`): danh sách Chờ kết luận / Tất cả, gửi / sửa giải trình 10–1000 ký tự (400 `5272`, 409 `5271` khi trung tâm đã kết luận), giữ nội dung khi lỗi, hỏi trước khi bỏ form. Hồ sơ → "Vắng ca"; Trang chủ nhắc khi còn lần chờ giải trình; thông báo `CAREGIVER_ABSENCE_UPDATED` mở danh sách; biểu tượng riêng cho `CHECK_IN_LATE`, `REFUND_COMPLETED` | `npm run lint` 0 lỗi, `typecheck` sạch, jest **33 suite / 257 test** xanh (mới: `absences.test.ts`, `absences-ui.test.tsx`). `build:android` **không chạy xong**: Claude Code dừng vì máy thiếu RAM — cần chạy lại |
| 6 | Hoàn khiếu nại / quá hạn vẫn qua cổng | **Không đổi** (quyết định chủ dự án 2026-11-10) | — |
| 7 | FE: 2 test đỏ + lỗi eslint | 2 test đỏ do dấu `*` nằm trong `<label>` của `booking-reason-dialog.tsx` → đưa ra ngoài label. Chữ viết cứng chuyển sang i18n ở `booking-reason-dialog`, `booking-occurrence-detail-dialog` (thêm nhãn trạng thái đồng ý người thay, hiện lỗi khi gửi đồng ý), `occurrence-reschedule-dialog` (lỗi đọc qua `bookingErrorKey`, thêm mã `5250` / `5251`, bỏ `Date.now()` khi render), `payment-result-page`; `booking-api.ts` bỏ 8 khối `any` bằng một hàm `requestError`; bỏ import thừa ở `services-results` | `eslint .` **0 lỗi**, `tsc` sạch, Vitest **110 file / 506 test** xanh |
| 8 | Áp SQL + rebuild + E2E | Đã backup 2 DB (`pg_dump`), áp `2026-11-10_caregiver_absences.sql` (ghi bù **6** hồ sơ vắng ca cũ) → `2026-11-10_late_cancel_no_refund.sql` → Payment `2026-11-10-refund-notification-and-late-cancel.sql`; `docker compose up --build -d --wait booking-service payment-service api-gateway` → cả 3 healthy (`ddl-auto: validate` qua), log không lỗi. E2E API qua gateway với tài khoản admin bootstrap: thống kê / danh sách / chi tiết / lọc theo caregiver của hồ sơ vắng ca, admin bị từ chối kết luận / giải trình (403) và hồ sơ không đổi, mọi policy có `lateCancelNoRefundMinutes` = 120, lưu mốc > mốc hoàn → 400 `5413`, thiếu → 400 `5414`, âm → 400, policy vẫn mặc định | **16 / 16** kiểm tra live xanh. **Chưa E2E được bằng tài khoản manager / caregiver / customer** (không có mật khẩu test; extension Chrome không kết nối) — xem "Còn lại" |
| 9 | Test trên PostgreSQL thật | Container `postgres:17` tạm (đã xóa sau khi chạy). Payment **57 / 57**. Booking lần đầu 2 test hỏng — đều là test cũ (29/10), chỉ lộ trên PostgreSQL: `forgottenCareSession…` đẩy `end_at` trước `start_at` (vi phạm `ck_booking_occurrences_range`), `expirySkipsBookingLocked…` còn chờ `PENDING_CENTER_CONFIRMATION` trong khi từ 2026-11-09 booking có caregiver vào thẳng `AWAITING_INITIAL_PAYMENT` → sửa dữ liệu test | Booking PostgreSQL **151 / 151** (0 skip), H2 151 (2 skip chỉ chạy trên PostgreSQL) |

## Còn lại

- **E2E có đăng nhập cho manager / caregiver / customer**: kết luận một lần vắng ca (không hoàn tác được — chưa làm trên
  dữ liệu thật), caregiver gửi giải trình trên web và mobile, khách hủy sát giờ xem bảng mức hoàn và nhận thông báo
  "Hoàn tiền thành công". Cần mật khẩu tài khoản test của các vai trò này, hoặc bật extension Chrome đã đăng nhập.
- **`npm run build:android`** của mobile: chạy lại khi máy đủ RAM.
- Prettier `--check` toàn repo mobile báo 101 file chỉ vì máy Windows checkout CRLF (`core.autocrlf=true`); với
  `--end-of-line auto` thì sạch — không phải lỗi code.
- Backup DB trước khi áp SQL: thư mục scratchpad của phiên (`booking-before-2026-11-10.dump`, `payment-before-2026-11-10.dump`).

## File đã sửa sau commit đề xuất trước (phần tồn đọng)

- BE Booking: `dto/request/CenterBookingPolicyRequest.java`, `dto/response/CenterBookingPolicyResponse.java`,
  `dto/response/PaymentContextResponse.java`, `entity/CenterBookingPolicy.java`, `mapper/BookingMapper.java`,
  `config/BookingProperties.java`, `service/Impl/CenterBookingPolicyServiceImpl.java`, `application.yaml`, `.env.example`,
  `schema/booking.sql`, `schema/changes/2026-11-10_late_cancel_no_refund.sql` (mới), `src/test/.../BookingApiTests.java`.
  (`service/Impl/BookingOccurrenceServiceImpl.java` — sửa thay thế khẩn cấp — nằm chung file với đợt trước.)
- BE Payment: `enums/OutboxEventType.java`, `entity/PaymentOutbox.java`, `repository/PaymentOutboxRepository.java`,
  `client/BookingClient.java`, `client/dto/BookingNotificationRequest.java` (mới), `client/dto/BookingPaymentContextResponse.java`,
  `dto/response/PolicySnapshot.java`, `mapper/PaymentPolicyMapper.java`, `util/RefundCalculator.java`,
  `service/PaymentOutboxService.java`, `service/Impl/PaymentOutboxServiceImpl.java`,
  `service/Impl/RefundDispatchServiceImpl.java`, `schema/payment.sql`,
  `schema/changes/2026-11-10-refund-notification-and-late-cancel.sql` (mới), test: `support/PaymentTestServer.java`,
  `WalletApiTests.java`, `PaymentApiTests.java`, `util/RefundCalculatorTests.java`.

- FE (mục 3): `openapi/openapi.yaml` (+ `app/shared/api/generated/**` sinh lại), `entities/booking/center-booking-policy-form.tsx`
  (+ `.test.tsx`), `entities/payment/refund-policy.tsx`, `features/customer/centers/components/center-policy-section.tsx`,
  `features/customer/booking/components/step-review.tsx`, `features/customer/bookings/components/booking-cancel-dialog.tsx`,
  i18n `booking-resources.ts`, `payment-resources.ts`, `customer/booking-detail-resources.ts`, `customer/centers-resources.ts`,
  `customer/booking-resources.ts`; fixture `lateCancelNoRefundMinutes` trong 6 test trang; `docs/ARCHITECTURE.md`.

- Lượt 2 — BE: `BookingService/src/test/.../BookingApiTests.java` (2 test cũ), docs. FE: `caregiver-absence-history.tsx`
  (+ test), `manager-caregiver-live-detail-page.tsx`, `booking-reason-dialog.tsx`, `booking-api.ts`, `booking-format.ts`,
  `booking-occurrence-detail-dialog.tsx`, `occurrence-reschedule-dialog.tsx`, `payment-result-page.tsx`,
  `services-results.tsx`, i18n. Mobile: `src/features/absences/**`, `src/core/copy/absence.ts`, routes / navigation /
  l10n / home / profile / notifications, tests `absences*.test.ts(x)`, `ARCHITECTURE.md`.

**Commit đề xuất (phần tồn đọng):** `fix(booking): check availability and lock the replacement caregiver in emergency
reassignment` · `feat(payment): notify the customer once a refund order completes` · `feat(booking,payment): per-center
late-cancel no-refund window` · FE: `feat(policy): edit and show the per-center late-cancel no-refund window`
