# AGENTS.md — BookingService

> Chỉ ghi phần **khác / bổ sung** so với `../AGENTS.md`. Đọc file gốc trước. Thiết kế + quyết định: `../docs/2026-10-09-PLAN_BOOKING.md` (§0).

## Thông số

| Field | Value |
|---|---|
| Thư mục / artifact | `CareConnect/BookingService/` · `mss301:BookingService:0.0.1-SNAPSHOT` |
| Base package | `mss301.bookingservice` |
| Port / env prefix | `8085` / `BOOKING_` |
| Dải `ErrorCode` | **5000–5999** (+ 401/403/404/9999) — 50xx chung, 51xx booking, 52xx lịch/buổi, 54xx phân công/trung tâm, 55xx tích hợp Identity, 56xx CareRecipient, 57xx Provider |
| DB | PostgreSQL riêng `127.0.0.1:5445`, DB `CARECONNECT_BOOKING`, `schema/booking.sql` (áp tay, không Flyway). Không Redis |

## Khác với CareRecipientService

- Bảo mật giống CareRecipient (JWKS + audience `careconnect-api`, CSRF chỉ đọc, CORS tắt). Không còn `/internal/**`:
  endpoint cho service khác dùng `hasRole('SERVICE')` (token client-credentials của IdentityService).
  `security/CurrentCenterId` (claim `center_id`; thiếu → 403 `CENTER_SCOPE_REQUIRED`, `required = false` → null) và
  `security/CurrentRoles` (`Set<RoleCode>`) để service chọn phạm vi theo role — controller không so chuỗi `ROLE_*`.
- Feign client (`client/`) gọi qua gateway, mỗi client có `*ClientConfig` (không `@Configuration`), `*ErrorDecoder`,
  `*FallbackFactory`, timeout riêng, circuit breaker chung (`config/ResilienceConfig`). Client chạy dưới quyền người dùng
  dùng `FeignAuthForwardingInterceptor`; client cần quyền service dùng `ServiceTokenInterceptor` + `ServiceTokenProvider`.
- Controller: `BookingController` (`/api/v1/bookings`) + `BookingOccurrenceController` (`/api/v1/booking-occurrences`,
  service `BookingOccurrenceService`). Filter động ở `util/BookingSpecifications` (helper tĩnh như `PageRequests`; `repository/` chỉ chứa interface).
- `@EnableScheduling`: `scheduler/BookingExpiryScheduler` (chu kỳ `BOOKING_EXPIRY_SCAN_INTERVAL`).
- `Clock` bean (`config/BookingConfig`) — mọi "bây giờ" lấy từ Clock (test cố định được).
- `bookings.recipient_snapshot` là jsonb map vào `dto/response/RecipientSnapshot` (`@JdbcTypeCode(JSON)`); `days_of_week`
  là `smallint[]` (`Short[]`, `@JdbcTypeCode(ARRAY)`). `booking_details`, `booking_status_history` ghi một lần, không kế
  thừa `BaseEntity`.
- Test H2 cần alias `HASHTEXT` / `PG_ADVISORY_XACT_LOCK` (`src/test/resources/h2-functions.sql`); khóa thật chỉ kiểm khi
  chạy `-Dbooking.testing.postgres=true`.

## Quy tắc nghiệp vụ (bắt buộc giữ)

1. **State machine chỉ ở `enums/BookingAction`** (nguồn → đích, mỗi action một đích). Mọi đổi trạng thái booking đi
   qua `BookingLifecycleService.apply` (mốc thời gian, hủy buổi chưa bắt đầu khi kết thúc sớm, `booking_status_history`,
   ghi outbox), trong transaction ghi, với booking **đã khóa dòng** (`BookingRepository.findForUpdate`). Hủy từng buổi:
   `cancelOccurrences` (ghi `cancelled_at`, `cancelled_by_party`, `booking_events`); hoàn tiền: `requestRefund` (outbox
   `REFUND`, Payment tính số tiền — Booking **không** tự tính %).
   Không `setStatus` booking ở chỗ khác. Chốt booking khi hết buổi: `BookingLifecycleService.finalizeIfDone` →
   `FINALIZE` (COMPLETED, có buổi COMPLETED / CUSTOMER_NO_SHOW) hoặc `FINALIZE_CANCELLED` (CANCELLED) — BK-03 / BK-04 /
   OPS-06 dùng lại. Thứ tự khóa luôn booking → buổi → session.
1a. **Buổi**: SCHEDULED → ACKNOWLEDGED (caregiver nhận ca) → IN_PROGRESS (check-in) → COMPLETED (check-out); chưa bắt
   đầu → CANCELLED / MISSED / CUSTOMER_NO_SHOW (`OccurrenceStatus.canTransitionTo`). Đổi caregiver dùng
   `BookingOccurrence.assignCaregiver` (ACKNOWLEDGED → SCHEDULED). Sự kiện mức buổi ghi `booking_events`.
1b. **Gửi sang service khác = outbox** (`BookingOutboxService.enqueue` trong transaction nghiệp vụ → gửi sau commit,
   `REQUIRES_NEW`; `BookingOutboxScheduler` retry backoff, quá `BOOKING_OUTBOX_MAX_ATTEMPTS` → FAILED). Bên nhận khử
   trùng theo `Idempotency-Key` (cột `idempotency_key` UNIQUE; `enqueue` bỏ qua key đã có): `REMAINING_INVOICE:<bookingId>`,
   `REFUND:CAREGIVER_MISSED:<occurrenceId>`, `REFUND:<CUSTOMER_CANCEL|CENTER_CANCEL|CENTER_LOCKED>:<bookingId>:<uuid theo tập
   buổi>`. Không gọi Payment ghi dữ liệu trong transaction; preview hoàn tiền (C2.2) gọi đồng bộ ngoài transaction.
1c. **Hủy buổi (BK-03 / BK-04)**: chỉ buổi chưa bắt đầu, chưa check-in, `startAt > now`; danh sách chỉ định có buổi không
   hợp lệ → từ chối cả lệnh. Booking chưa cọc chỉ hủy cả booking. `previewToken` = HMAC (không lưu DB) — lệnh hủy tính lại
   tiền ở Payment rồi so token cả trước lẫn trong transaction. Booking rời trạng thái giữ chỗ luôn kéo mọi buổi chưa kết
   thúc về CANCELLED — exclusion constraint BK-09 dựa vào bất biến này (không có cột `active_slot`).
1d. **MISSED / báo vắng (OPS-06)**: job chỉ đánh dấu buổi không có báo vắng; báo vắng chờ manager quyết định (không tự
   quyết, chỉ nhắc). MISSED không mở lại.
2. **Hai id caregiver luôn đi cùng nhau**: `caregiver_id` (Provider) + `caregiver_user_id` (Identity), lấy từ
   `CaregiverPort` (Provider) — không bao giờ nhận `user_id` từ client. CHECK trong SQL bắt cùng NULL / cùng có.
3. **Booking là nguồn sự thật về giờ đã đặt.** Kiểm trùng trên buổi `SCHEDULED/ACKNOWLEDGED/IN_PROGRESS` của booking
   `PENDING/AWAITING_DEPOSIT/CONFIRMED/IN_PROGRESS`; khoảng nửa mở `[start, end)`. Gọi `lockSchedule(...)` (advisory lock
   caregiver → người bệnh) TRƯỚC khi check, trong cùng transaction ghi. Chốt chặn cuối ở PostgreSQL: exclusion constraint
   `ex_booking_occurrences_caregiver_slot` (btree_gist) → `GlobalExceptionHandler` trả 409 `5215`.
4. **Không I/O mạng trong transaction** (rule #24): gọi CareRecipient / Provider / Identity trước, ghi trong
   `TransactionTemplate`. Service có I/O mạng không được `@Transactional`.
5. **Không tin giá từ client** (BK-01): luôn báo giá qua `CareServiceCatalogPort` (gói giá bắt buộc); Catalog lỗi → 503,
   không tạo booking không giá. Giá từng buổi do `BookingPriceCalculator` (HOUR = số giờ làm tròn lên, DAY / SHIFT = 1,
   làm tròn đồng); `bookings.deposit_amount` để NULL — PaymentService quyết định tiền cọc.
6. Kỹ năng caregiver (PRV-01): Provider trả `careServiceIds` (không có = rỗng); phải chứa đủ dịch vụ đã chọn, không thì
   `CAREGIVER_SERVICE_NOT_SUPPORTED`.
7. Phạm vi: customer chỉ booking của mình, manager chỉ booking cùng `center_id` trong token, caregiver chỉ buổi được giao
   (`booking_occurrences.caregiver_user_id`, không theo caregiver cấp booking) thuộc booking đã xác nhận — ngoài phạm vi
   luôn **404** (không lộ tồn tại). Quyền xem hồ sơ người bệnh của caregiver (snapshot + `caregiver-access`) dùng chung một
   quy tắc: booking CONFIRMED / IN_PROGRESS và buổi còn hoạt động hoặc COMPLETED chưa quá `BOOKING_CAREGIVER_ACCESS_GRACE`
   (24h) — xem `BookingOccurrenceServiceImpl.canCaregiverSeeRecipient` và `BookingOccurrenceRepository.existsCaregiverAccess`.
8. CareRecipient lỗi khi tạo booking → 503, **không** tạo booking thiếu kiểm quyền / thiếu snapshot. Identity lỗi chỉ làm
   thiếu thông tin khách ở màn hình trung tâm / admin.

## TODO đã biết

Payment (thay `deposit-paid` bằng `deposit-confirmations` — PAY-08; endpoint C2.2 / C2.3 / C2.5 chưa có thật, mới thử
với stub); thông báo (NTF-01 / NTF-02, người C — các chỗ `TODO(NTF-0x)`); cập nhật checklist / care report (OPS-03 /
OPS-05, người C); ca qua đêm; Flyway (đợt chung). Scheduler (BK-09): lô id đọc không khóa, mỗi dòng khóa bằng
`findForUpdateSkipLocked` / outbox `findForUpdate` (`SKIP LOCKED`) rồi kiểm lại trong transaction riêng — nhiều instance
không chờ nhau, không xử lý trùng (test PostgreSQL `expirySkipsBookingLockedByAnotherInstance`).
