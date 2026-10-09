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

- Bảo mật giống CareRecipient (JWKS + audience `careconnect-api`, CSRF chỉ đọc, CORS tắt, chain `/internal/**` +
  `X-Internal-Secret`). Thêm `security/CurrentCenterId` (claim `center_id` của CENTER_MANAGER; thiếu → 403 `CENTER_SCOPE_REQUIRED`).
- Ba Feign client (`client/`), mỗi client có `*ClientConfig` (không `@Configuration`), `*ErrorDecoder`, `*FallbackFactory`,
  timeout riêng, circuit breaker chung (`config/ResilienceConfig`, lỗi "không tồn tại" không mở mạch). Header chung ở
  `client/InternalClientHeaders` — mỗi service được gọi dùng **secret riêng** của nó.
- `@EnableScheduling`: `scheduler/BookingExpiryScheduler` (chu kỳ `BOOKING_EXPIRY_SCAN_INTERVAL`).
- `Clock` bean (`config/BookingConfig`) — mọi "bây giờ" lấy từ Clock (test cố định được).
- `bookings.recipient_snapshot` là jsonb map vào `dto/response/RecipientSnapshot` (`@JdbcTypeCode(JSON)`); `days_of_week`
  là `smallint[]` (`Short[]`, `@JdbcTypeCode(ARRAY)`). `booking_details`, `booking_status_history` ghi một lần, không kế
  thừa `BaseEntity`.
- Test H2 cần alias `HASHTEXT` / `PG_ADVISORY_XACT_LOCK` (`src/test/resources/h2-functions.sql`); khóa thật chỉ kiểm khi
  chạy `-Dbooking.testing.postgres=true`.

## Quy tắc nghiệp vụ (bắt buộc giữ)

1. **State machine chỉ ở `enums/BookingAction`** (nguồn → đích). Mọi đổi trạng thái đi qua
   `BookingLifecycleService.apply` (mốc thời gian, hủy buổi SCHEDULED khi kết thúc sớm, `booking_status_history`,
   `RefundPolicy`), trong transaction ghi, với booking **đã khóa dòng** (`BookingRepository.findForUpdate`). Không
   `setStatus` ở chỗ khác.
2. **Hai id caregiver luôn đi cùng nhau**: `caregiver_id` (Provider) + `caregiver_user_id` (Identity), lấy từ
   `CaregiverPort` (Provider) — không bao giờ nhận `user_id` từ client. CHECK trong SQL bắt cùng NULL / cùng có.
3. **Booking là nguồn sự thật về giờ đã đặt.** Kiểm trùng (`ScheduleConflictService`) trên buổi `SCHEDULED/IN_PROGRESS`
   của booking `PENDING/AWAITING_DEPOSIT/CONFIRMED/IN_PROGRESS`; khoảng nửa mở `[start, end)`. Gọi `lock(...)` (advisory lock
   caregiver → người bệnh) TRƯỚC khi check, trong cùng transaction ghi.
4. **Không I/O mạng trong transaction** (rule #24): gọi CareRecipient / Provider / Identity trước, ghi trong
   `TransactionTemplate`. Service có I/O mạng không được `@Transactional`.
5. **Không tin giá từ client**: `CareServiceCatalogPort` (tạm: giá NULL). Bật `BOOKING_CATALOG_INTEGRATION_ENABLED` khi
   chưa có adapter thật → app từ chối khởi động.
6. Kỹ năng caregiver: `careServiceIds == null` (Provider chưa quản lý) → bỏ qua + WARN; danh sách (kể cả rỗng) phải chứa
   đủ dịch vụ đã chọn, không thì `CAREGIVER_SERVICE_NOT_SUPPORTED`.
7. Phạm vi: customer chỉ booking của mình, manager chỉ booking cùng `center_id` trong token, caregiver chỉ buổi được giao
   thuộc booking đã xác nhận — ngoài phạm vi luôn **404** (không lộ tồn tại).
8. CareRecipient lỗi khi tạo booking → 503, **không** tạo booking thiếu kiểm quyền / thiếu snapshot. Identity lỗi chỉ làm
   thiếu thông tin khách ở màn hình trung tâm / admin.

## TODO đã biết

CareCatalog (báo giá, % cọc); Provider: kỹ năng (`caregiver_services`) + khung giờ làm việc (`availability_slot_id`);
Payment (thay `deposit-paid`, hoàn tiền theo `RefundPolicy`); bắt đầu / kết thúc ca, `occurrence_services`,
`care_reports`, `feedbacks`; ca qua đêm; exclusion constraint `tstzrange`; scheduler nhiều instance (SKIP LOCKED);
Flyway (đợt chung).
