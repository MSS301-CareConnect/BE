# PLAN — BookingService (giai đoạn 1: booking cốt lõi + nối Identity / CareRecipient + Gateway)

> Trạng thái: **HOÀN THÀNH giai đoạn 1 (2026-10-09)** — kết quả §13, chưa commit (chờ review). Quyết định cuối cùng ở **§0**; các
> mục §1–§12 là bản khảo sát/đề xuất ban đầu — chỗ nào khác §0 thì **§0 là chuẩn**.
> Nguồn quy ước: `AGENTS.md` (gốc, gồm §3 quy tắc MapStruct), `docs/2026-10-03-PLAN_CARE_RECIPIENT_GATEWAY.md`, cách làm
> của `CareRecipientService`. Khi yêu cầu task mâu thuẫn với `AGENTS.md`, plan này đề xuất theo `AGENTS.md` và ghi ở §9.
> Tên file theo quy ước `docs/<yyyy-MM-dd>-<TÊN>.md` (yêu cầu ghi `docs/PLAN_BOOKING.md`).

---

## 0. Quyết định đã chốt (2026-10-09)

| # | Chốt |
|---|---|
| Q1 | **Lưu cả hai id** trên `bookings` và `booking_occurrences`: `caregiver_id` = `caregiver_profiles.caregiver_id` (Provider — tham chiếu nghiệp vụ chính: lịch, kỹ năng, đánh giá, thống kê) và `caregiver_user_id` = `user_id` Identity (phân quyền theo JWT `sub`, `/caregiver/occurrences`, `caregiver-access`, thông báo). Hai giá trị luôn ghi cùng lúc khi phân công / đổi caregiver, **lấy từ Provider**, không nhận `user_id` từ client (CHECK cùng NULL / cùng có). Index `caregiver_user_id`. |
| Q1 → Provider | **Nối ProviderService ngay, tối thiểu, chỉ đọc** — thêm vào Provider (additive, `/internal/**` + `X-Internal-Secret`): `GET /internal/caregivers/{caregiverId}`, `GET /internal/caregivers/by-user/{userId}` → `caregiverId, userId, centerId, centerStatus, fullName, verificationStatus, caregiverStatus, careServiceIds`; `GET /internal/centers/{centerId}` → `centerId, centerName, centerStatus, managerUserId`. Booking dùng qua `CaregiverPort` / `CenterPort` (Feign). CareCatalog vẫn chưa nối (port + cờ). |
| Kỹ năng | Provider **chưa có dữ liệu kỹ năng** → `careServiceIds = null` (= "Provider chưa quản lý kỹ năng") → Booking bỏ qua kiểm kỹ năng, log WARN + TODO. `[]` hoặc danh sách thiếu dịch vụ đã chọn → `CAREGIVER_SERVICE_NOT_SUPPORTED` (5406). Unit test cả ba trường hợp. Vẫn kiểm caregiver thuộc đúng center + `VERIFIED` / `ACTIVE`. **Việc tiếp theo (đợt riêng): bảng `caregiver_services` + API quản lý trong ProviderService.** |
| availability_slot | Đã khảo sát: `caregiver_schedules` là **slot rời rạc theo ngày** (gắn `work_shifts.work_date` + giờ) → **giữ** `booking_occurrences.availability_slot_id` (nullable, chỉ tham chiếu `schedule_id`). Nguyên tắc: Provider chỉ giữ khung giờ làm việc, **Booking là nguồn sự thật duy nhất về giờ đã đặt**, Provider không đánh dấu slot đã đặt. Trùng lịch kiểm trên occurrence đang hoạt động trong Booking. Kiểm khung giờ làm việc qua Provider: **TODO** (Provider chưa có API phù hợp). |
| Q2 | Phân quyền CENTER_MANAGER bằng claim `center_id`. Khi customer tạo booking: kiểm `centerId` tồn tại + `ACTIVE` qua `GET /internal/centers/{id}`. |
| Q3 | Mặc định (đều qua env): tối đa **60 buổi**, khoảng **90 ngày**, báo trước tối thiểu **12 giờ**, hạn cọc **24 giờ** sau accept (không quá giờ buổi đầu), quét hết hạn mỗi **5 phút**. |
| Q4 | PENDING giữ chỗ caregiver. Thêm **hạn chờ duyệt** `acceptance_due_at` = tạo + 24 giờ (không quá giờ buổi đầu); scheduler chuyển PENDING quá hạn → `EXPIRED`, giải phóng caregiver. |
| Q5–Q10 | Theo đề xuất: caregiver không bắt buộc lúc tạo, bắt buộc trước accept; `caregiver-access` ở CONFIRMED / IN_PROGRESS; endpoint mới `GET /caregiver/care-recipients/{id}` bên CareRecipient (Booking lỗi → 503); chỉ `hasRole`; `price_plan_id`, giá NULL; idempotency bằng cột trên `bookings`. |
| Q11 | `booking_status_history`: `from_status, to_status, changed_by, reason, changed_at` (+ `history_id`, `booking_id`). |
| Q12 | Kiểm caregiver qua **Provider** (thuộc center, hoạt động, làm được dịch vụ). **Không** sửa Identity, không thêm `centerId` vào `InternalUserResponse`. |
| Q13 | `RefundPolicy` nhận bên hủy `CUSTOMER / CENTER / SYSTEM`; trung tâm hủy → hoàn toàn bộ (tính tiền vẫn TODO). |
| Q14, Q15 | Theo đề xuất. Không có API xóa booking; `deleted_at` chỉ do kế thừa `BaseEntity`. |
| Q16 | Seed manager / caregiver / center **chỉ vào DB e2e** của Identity và Provider, không seed DB dev. |
| Phụ | Danh sách manager/admin dùng `recipient_snapshot` cho tên người bệnh (không gọi batch CareRecipient). |

Điều chỉnh khi triển khai (so với đề xuất §2–§7): dải lỗi 57xx dành cho tích hợp Provider (không phải API nội bộ);
query param của `caregiver-access` là **`caregiverUserId`** (CareRecipient chỉ có `sub`); Identity chỉ còn dùng batch
(caregiver kiểm qua Provider).

---

## 1. Hiện trạng (kết quả khảo sát)

### 1.1 Repo
- `BookingService/` đang là **skeleton Spring Initializr chưa track** (`BookingServiceApplication`, `application.yaml` chỉ
  có tên app, test context-load, `.gitignore`, `HELP.md`, `.idea/`). pom: Boot 4.1.1, Cloud BOM 2025.1.3, JPA, Validation,
  WebMVC, springdoc 3.1.0, OpenFeign, PostgreSQL, Lombok — chưa có Security, Resource Server, Actuator, MapStruct,
  Resilience4j, H2.
- Port / DB / dải lỗi đang dùng: Identity 8081 / 5441 / 1xxx · CareRecipient 8082 / 5442 / 2xxx · CareCatalog 8083 / 5443 /
  3xxx · Provider 8084 / 5444 / 4xxx · Gateway 8080 / — / 90xx.
- Gateway hiện **chỉ** route Identity + CareRecipient (chưa có route Catalog/Provider).
- Test của CareRecipient không dùng WireMock: stub HTTP bằng `com.sun.net.httpserver.HttpServer`
  (`support/TestIdentityServer`, kiêm JWKS + ký JWT test) → Booking làm cùng cách, **không thêm WireMock**.

### 1.2 IdentityService — claim `center_id`
- `JwtServiceImpl`: access token có `center_id` khi `users.center_id != null`. `CENTER_MANAGER` bắt buộc có `centerId`
  lúc ADMIN provision; `CAREGIVER` nhận `center_id` **của manager tạo ra nó**. CUSTOMER/ADMIN không có claim.
- Identity **không xác minh** `centerId` có tồn tại bên Provider (chưa có contract). CareCatalog đã dùng claim này
  (`@CurrentCenterId` → `CENTER_ACCESS_DENIED` nếu thiếu).
- **Kết luận:** claim `center_id` dùng được cho CENTER_MANAGER ở Booking, với giả định Identity `users.center_id` =
  Provider `care_centers.center_id` (hiện đồng bộ **bằng tay** khi admin tạo manager). Lưu ý ProviderService lại phân quyền
  manager bằng `care_centers.manager_user_id == sub`, không dùng claim → hai nguồn sự thật, xem Q2.
- `/internal/users/{id}`, `/batch` trả `{id, email, fullName, phoneNumber, status, roles}` — **không có `centerId`**.

### 1.3 ProviderService (chỉ đọc)
| Bảng | Khóa / cột Booking quan tâm |
|---|---|
| `care_centers` | `center_id` (UUID do Provider sinh), `center_status` (PENDING/ACTIVE/INACTIVE/SUSPENDED/REJECTED/ARCHIVED), `manager_user_id` (Identity user, unique) |
| `caregiver_profiles` | **`caregiver_id` (id riêng của Provider)** + **`user_id` (Identity, UNIQUE)**, `center_id` FK, `verification_status` (PENDING/VERIFIED/REJECTED), `caregiver_status` (ACTIVE/INACTIVE/SUSPENDED/ARCHIVED), gender, experience_years, rating_* |
| `certificates` | theo `caregiver_id` |
| `work_shifts` | ca trung tâm công bố: `center_id`, `work_date`, `start_time`, `end_time`, `shift_type`, `capacity`, status DRAFT/OPEN/CLOSED/CANCELLED/COMPLETED |
| `shift_registrations` | caregiver đăng ký ca (PENDING/APPROVED/…) |
| `caregiver_schedules` | lịch caregiver sau khi duyệt đăng ký: `schedule_id`, `caregiver_id`, `work_shift_id`, status SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED |

- **Không có** bảng kỹ năng caregiver, **không có** "availability slot" — gần nhất là `caregiver_schedules` (caregiver đã được
  xếp vào một `work_shift` của trung tâm) → `booking_occurrences.availability_slot_id` sau này nhiều khả năng trỏ tới
  `caregiver_schedules.schedule_id`.
- **Không có API `/internal/**`** (đã có `InternalSecurityConfig` nhưng chưa controller nào). API public: `/catalog/caregivers`,
  `/caregivers/{id}`, `/caregiver/profile`, `/caregiver/schedules`…
- ⚠️ `caregiver_schedules` có vòng đời SCHEDULED → IN_PROGRESS → COMPLETED riêng — **chồng lấn** với occurrence của Booking
  (bắt đầu/kết thúc ca). Cần chốt ai sở hữu "ca chăm sóc thực tế" trước giai đoạn start/end (§8).

### 1.4 CareCatalogService (chỉ đọc)
| Bảng | Ghi chú |
|---|---|
| `care_services` (`care_services_id`) | danh mục chuẩn (ADMIN), status ACTIVE/INACTIVE |
| `center_services` (`center_services_id`) | `care_services_id` FK + `center_id`, unique (center, care_service) |
| `price_plans` (`price_id`) | **giá nằm ở đây**: `center_service_id`, `pricing_unit` HOUR/DAY/SHIFT, `duration_minutes`, `base_price` DECIMAL(10,2), `is_active` |

- **Không có API `/internal/**`**; API đọc public (`/center-services/{id}`, `/center-services/center/{centerId}`,
  `/price-plans/center-service/{id}`, `/price-plans/{id}`) cần JWT.
- Hệ quả: dòng dịch vụ trong booking cần thêm **`price_plan_id`** (không chỉ `center_service_id`) để biết giá/đơn vị.

### 1.5 CareRecipientService — điểm tích hợp
- `/internal/care-recipients/{id}/access?userId=&minLevel=` → luôn 200 `{allowed, accessLevel}`.
- `/internal/care-recipients/{id}/snapshot` → 404 khi đã ARCHIVED; INACTIVE vẫn trả (`status`) để Booking tự chặn.
  Snapshot gồm tên, ngày sinh/tuổi, giới tính, SĐT, địa chỉ + tọa độ, nhóm máu, vận động/tự lập, dị ứng, bệnh nền, ăn uống,
  hành vi, yêu cầu chăm sóc, thuốc đang dùng, liên hệ khẩn cấp chính, `snapshotAt`.
- `/internal/care-recipients/batch` (≤ 100 id).
- `CaregiverAssignmentService.isAssigned(recipientId, caregiverId)` — impl tạm trả `false`, **chưa ai gọi**, chưa có endpoint
  nào cho CAREGIVER.

---

## 2. Thông số BookingService

| Field | Value |
|---|---|
| Thư mục / artifact | `BookingService/` · `mss301:BookingService:0.0.1-SNAPSHOT` (giữ của skeleton) |
| Base package | `mss301.bookingservice` |
| Port / env prefix | **8085** / `BOOKING_` |
| Dải `ErrorCode` | **5000–5999** (+ 401/403/404/9999): 50xx chung, 51xx booking, 52xx lịch/occurrence, 53xx dịch vụ/giá, 54xx phân công, 55xx tích hợp Identity, 56xx tích hợp CareRecipient, 57xx API nội bộ |
| DB | PostgreSQL 17 riêng `127.0.0.1:5445`, DB `CARECONNECT_BOOKING`, `schema/booking.sql` áp tay (không Flyway), `ddl-auto: validate`, `timestamptz`, JVM UTC. **Không Redis** |
| Bảo mật | Resource Server (JWKS Identity, RS256, iss, aud `careconnect-api`, token_type=access, sub UUID), CSRF read-only như CareRecipient, CORS tắt; chain riêng `/internal/**` + `X-Internal-Secret` (`BOOKING_INTERNAL_SECRET`, trống = khóa) |
| Khác | `spring.threads.virtual.enabled=true`, `RequestIdFilter`, actuator health, springdoc + `BOOKING_OPENAPI_SERVER_URL` |

**Thư viện thêm vào pom Booking:** `spring-boot-starter-security`, `-security-oauth2-resource-server`, `-actuator`,
`spring-cloud-starter-circuitbreaker-resilience4j`, MapStruct 1.6.3 + processor + `lombok-mapstruct-binding` 0.2.0;
test: `h2`, `spring-boot-starter-security-test` (giống hệt CareRecipient). Không Redis, không Flyway, không WireMock.
**CareRecipient:** không thêm thư viện (đã có OpenFeign + Resilience4j). **Gateway:** không thêm.

---

## 3. Schema đề xuất (`BookingService/schema/booking.sql`)

Quy ước: PK UUID (`@UuidGenerator`, app sinh), `timestamptz`, enum `VARCHAR` + CHECK, `version BIGINT` (`@Version`),
FK chỉ trong nội bộ DB Booking, tham chiếu service khác bằng UUID không FK.

### 3.1 `bookings`
| Cột | Kiểu | Ghi chú |
|---|---|---|
| `booking_id` | uuid PK | |
| `booking_code` | varchar(20) NOT NULL | `BK-yyyyMMdd-XXXXXX` (ngày theo `Asia/Ho_Chi_Minh`, 6 ký tự base32 không nhập nhằng; trùng → sinh lại, tối đa 5 lần). `uk_bookings_code` |
| `customer_id` | uuid NOT NULL | sub của JWT (Identity) |
| `recipient_id` | uuid NOT NULL | CareRecipient |
| `center_id` | uuid NOT NULL | Provider `care_centers.center_id` (= claim `center_id` của manager) |
| `caregiver_id` | uuid NULL | **xem Q1** (đề xuất Identity user_id). NULL khi chưa phân công |
| `schedule_type` | varchar(20) | CHECK `ONE_TIME, DAILY, WEEKLY` |
| `start_date`, `end_date` | date NOT NULL | ONE_TIME: end = start. CHECK `end_date >= start_date` |
| `start_time`, `end_time` | time NOT NULL | CHECK `end_time > start_time` (chưa hỗ trợ ca qua đêm — TODO) |
| `days_of_week` | smallint[] NULL | ISO 1=Thứ 2 … 7=CN; CHECK `schedule_type <> 'WEEKLY' OR (cardinality(days_of_week) BETWEEN 1 AND 7 AND days_of_week <@ ARRAY[1..7])`; NULL cho ONE_TIME/DAILY |
| `timezone` | varchar(64) NOT NULL DEFAULT `'Asia/Ho_Chi_Minh'` | lấy từ cấu hình, **không nhận từ client** |
| `recipient_snapshot` | jsonb NOT NULL | bản sao `RecipientSnapshot` (DTO riêng của Booking) — bắt buộc có, không tạo booking thiếu snapshot |
| `service_address` | text NOT NULL | ghép `addressLine, ward, district, province` từ snapshot |
| `customer_note` | text NULL | ≤ 2000 ký tự |
| `status` | varchar(30) NOT NULL | CHECK theo §4 |
| `subtotal`, `deposit_amount`, `total_amount` | numeric(14,2) NULL | NULL khi `BOOKING_CATALOG_INTEGRATION_ENABLED=false`; CHECK `>= 0` |
| `accepted_by`, `accepted_at` | uuid / timestamptz NULL | *bổ sung* — manager chấp nhận |
| `deposit_due_at` | timestamptz NULL | *bổ sung* — hạn đặt cọc (scheduler EXPIRED) |
| `confirmed_at` | timestamptz NULL | *bổ sung* — lúc deposit-paid |
| `cancelled_at`, `cancelled_by`, `cancel_reason` | | |
| `rejected_reason` | text NULL | (+ `rejected_by`, `rejected_at` *bổ sung*) |
| `idempotency_key` | varchar(100) NULL | *bổ sung* — header `Idempotency-Key` |
| `idempotency_hash` | char(64) NULL | *bổ sung* — SHA-256 của request đã chuẩn hóa |
| `created_at`, `updated_at`, `deleted_at` | timestamptz | `BaseEntity` (`deleted_at` chưa dùng — không có API xóa) |
| `version` | bigint NOT NULL DEFAULT 0 | |

Index: `customer_id`, `recipient_id`, `center_id`, `caregiver_id`, `status` (+ `(center_id, status)`, `(status, deposit_due_at)`
cho scheduler); `uk_bookings_idempotency (customer_id, idempotency_key) WHERE idempotency_key IS NOT NULL`.

### 3.2 `booking_occurrences` (mỗi dòng = một buổi chăm sóc)
`occurrence_id` PK · `booking_id` FK NOT NULL · `occurrence_no` int (1..n) · `start_at`, `end_at` timestamptz NOT NULL ·
`duration_minutes` int NOT NULL CHECK > 0 · `actual_start_at`, `actual_end_at` NULL (chưa có API ghi) · `caregiver_id` uuid NULL
(mặc định = booking, đổi riêng được) · `availability_slot_id` uuid NULL (chuẩn bị cho Provider) · `status` CHECK
`SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, MISSED` · `cancel_reason`, `note` text · `created_at`, `updated_at` · `version`.
- `UNIQUE (booking_id, occurrence_no)`, CHECK `end_at > start_at`, CHECK `actual_end_at IS NULL OR actual_start_at IS NULL OR
  actual_end_at > actual_start_at`.
- Index `(caregiver_id, start_at)`, `(booking_id, start_at)`, `(start_at)`.
- Không kế thừa `deleted_at` (không xóa mềm buổi; hủy = `CANCELLED`) → entity dùng base riêng không có `deleted_at`, hoặc
  giữ `BaseEntity` + cột `deleted_at` để nhất quán (**đề xuất giữ BaseEntity cho đơn giản**, cột không dùng).
- Ghi chú tương lai: chống trùng ở DB bằng `btree_gist` + `EXCLUDE USING gist (caregiver_id WITH =, tstzrange(start_at,end_at) WITH &&)
  WHERE (status IN ('SCHEDULED','IN_PROGRESS'))` — chưa làm vì điều kiện còn phụ thuộc trạng thái booking (bảng khác).

### 3.3 `booking_details` (dịch vụ trong booking)
`booking_detail_id` PK · `booking_id` FK NOT NULL · `care_service_id` uuid NOT NULL · `center_service_id` uuid NULL ·
**`price_plan_id` uuid NULL** (*bổ sung* — giá nằm ở `price_plans`) · `service_name` varchar(255) NULL · `unit_type` varchar(20)
NULL CHECK `HOUR, DAY, SHIFT` · `unit_price` numeric(14,2) NULL · `quantity` int NULL CHECK > 0 · `total_price` numeric(14,2)
NULL · `created_at` timestamptz NOT NULL.
- `UNIQUE (booking_id, care_service_id)`. Giai đoạn này mọi cột tên/giá = NULL (không tin client).

### 3.4 `booking_status_history` (*đề xuất bổ sung* — Q11)
`id` PK · `booking_id` FK · `from_status` NULL · `to_status` · `actor_id` uuid NULL (NULL = hệ thống/Payment) · `actor_type`
(`CUSTOMER, CENTER_MANAGER, ADMIN, SYSTEM, PAYMENT`) · `reason` text NULL · `created_at`. Audit chuyển trạng thái (dispute,
hoàn tiền sau này). Không có API đọc ở giai đoạn này (chỉ ghi), trừ khi anh/chị muốn đưa vào chi tiết booking.

### 3.5 Chưa tạo — thiết kế cho giai đoạn sau
- **`occurrence_services`**: PK `(occurrence_id, booking_detail_id)`, `quantity INTEGER` (sửa lỗi `INTERGER`), `status`
  (`PENDING, DONE, SKIPPED`), `note`, `completed_at`, `updated_at`.
- **`care_reports`**: `care_report_id` PK, **`occurrence_id` FK → booking_occurrences, `UNIQUE (occurrence_id)`** (bỏ
  `care_session_id`), `caregiver_id` uuid NOT NULL, `summary` text, `mood`/`appetite`/`sleep_quality` varchar + CHECK,
  `temperature_c numeric(4,1) CHECK (34.0..43.0)`, `heart_rate_bpm smallint CHECK (20..250)`, `systolic_bp smallint CHECK (50..260)`,
  `diastolic_bp smallint CHECK (30..160)`, CHECK `systolic_bp > diastolic_bp`, `spo2_percent smallint CHECK (50..100)`,
  `report_status` CHECK `DRAFT, SUBMITTED`, `submitted_at`, `created_at`, `updated_at` (đổi từ `create_at/update_at`),
  `version`. Chỉ tạo khi occurrence `IN_PROGRESS` hoặc `COMPLETED`.
- **`feedbacks`**: `feedback_id` PK, `booking_id` FK `UNIQUE`, `customer_id`, `caregiver_id`, `center_id`, `rating smallint CHECK
  (1..5)`, `comment` text, `created_at`, `updated_at`. Chỉ tạo khi booking `COMPLETED`. (Review & complaint có thể tách
  service riêng — AGENTS.md chưa chốt.)

---

## 4. State machine

### 4.1 Booking
```
                 accept (manager)          deposit-paid (Payment, internal)      start buổi đầu (giai đoạn sau)
   PENDING ───────────────────▶ AWAITING_DEPOSIT ─────────────────▶ CONFIRMED ─────────────────▶ IN_PROGRESS ──▶ COMPLETED
     │  │ reject (manager)              │  │                            │                              │   (mọi buổi kết thúc —
     │  └──────────▶ REJECTED           │  └─ hết hạn cọc ─▶ EXPIRED     │                              │    giai đoạn sau)
     │                                  │                               │                              │
     ├─ hủy (customer) ─▶ CANCELLED ◀───┴─ hủy (customer / manager) ◀───┴─ hủy (customer trước buổi đầu │
     │                                                                     / manager)                  │
     └─ quá giờ buổi đầu mà chưa duyệt ─▶ EXPIRED              CANCELLED ◀── hủy đặc biệt (manager) ───┘
```

| Hành động (`BookingAction`) | Ai | Từ | Sang | Ghi chú |
|---|---|---|---|---|
| `ACCEPT` | CENTER_MANAGER | PENDING | AWAITING_DEPOSIT | cần caregiver (có sẵn hoặc truyền trong body); đặt `deposit_due_at` |
| `REJECT` | CENTER_MANAGER | PENDING | REJECTED | bắt buộc lý do |
| `PAY_DEPOSIT` | internal (Payment) | AWAITING_DEPOSIT | CONFIRMED | gọi lại khi đã CONFIRMED → 200 no-op (idempotent) |
| `CANCEL_BY_CUSTOMER` | CUSTOMER | PENDING, AWAITING_DEPOSIT, CONFIRMED | CANCELLED | chỉ khi **chưa tới giờ buổi đầu** và chưa buổi nào bắt đầu; bắt buộc lý do |
| `CANCEL_BY_CENTER` | CENTER_MANAGER | AWAITING_DEPOSIT, CONFIRMED, IN_PROGRESS | CANCELLED | trường hợp đặc biệt, bắt buộc lý do; PENDING thì dùng REJECT |
| `EXPIRE` | SYSTEM (scheduler) | PENDING (buổi đầu đã tới giờ), AWAITING_DEPOSIT (`deposit_due_at` đã qua) | EXPIRED | |
| `START` | (giai đoạn sau) | CONFIRMED | IN_PROGRESS | khi buổi đầu tiên bắt đầu |
| `COMPLETE` | (giai đoạn sau) | IN_PROGRESS | COMPLETED | khi mọi buổi ở trạng thái kết thúc |

- Cài đặt tập trung: `enums/BookingAction` giữ bảng `Set<BookingStatus> from → BookingStatus to`; `BookingStatus.isTerminal()`,
  `isActive()`. Service gọi `BookingAction.X.apply(currentStatus)` → sai thì `AppException(INVALID_BOOKING_TRANSITION)` (409).
  Kiểm "ai được làm" ở service (role + phạm vi), không ở enum.
- Đổi trạng thái luôn khóa dòng (`BookingRepository.findForUpdate`, PESSIMISTIC_WRITE) + `@Version`.
- Đồng bộ occurrence: CANCELLED/REJECTED/EXPIRED → mọi occurrence `SCHEDULED` → `CANCELLED` (kèm lý do); occurrence
  IN_PROGRESS/COMPLETED giữ nguyên. Ghi `booking_status_history` mỗi lần đổi.
- **Hoàn tiền:** `service/RefundPolicy` (interface: `RefundQuote quote(Booking, Instant cancelledAt, actor)`) + impl tạm
  `PendingRefundPolicy` trả "chưa tính" (null) — TODO khi chốt % theo mốc ngày (AGENTS.md: chưa chốt).
- Không còn trạng thái "đang chờ thanh toán" phía Payment — `deposit-paid` là tạm thời để test; TODO thay bằng event Payment.

### 4.2 Occurrence
`SCHEDULED → IN_PROGRESS → COMPLETED`; `SCHEDULED → CANCELLED`; `SCHEDULED → MISSED` (giai đoạn sau, scheduler/caregiver).
Giai đoạn này chỉ có SCHEDULED và CANCELLED được ghi. Enum `OccurrenceStatus` cũng có bảng chuyển hợp lệ.

---

## 5. Sinh occurrence & kiểm tra trùng lịch

### 5.1 `OccurrenceGenerator` (component thuần, không DB; `service/OccurrenceGenerator` + `Impl/`, nhận `Clock` bean)
Input: `scheduleType, startDate, endDate, startTime, endTime, daysOfWeek, zoneId` → `List<OccurrenceSlot(no, startAt, endAt,
durationMinutes)>` (Instant UTC).
- ONE_TIME: `endDate` null hoặc = `startDate`; 1 buổi. `daysOfWeek` phải rỗng.
- DAILY: mọi ngày `[startDate, endDate]`; `endDate` bắt buộc; `daysOfWeek` phải rỗng.
- WEEKLY: ngày có thứ ∈ `daysOfWeek` (1–7, không trùng); `endDate` bắt buộc; khoảng ngày không chứa thứ nào →
  `NO_OCCURRENCE_IN_RANGE`.
- `startAt = ZonedDateTime.of(date, startTime, zone).toInstant()` (ZonedDateTime xử lý DST cho múi giờ khác; VN không có DST).
- Ràng buộc: `startDate >= hôm nay (theo zone)`; buổi đầu `startAt > now + minLeadTime`; `endTime > startTime`
  (`OVERNIGHT_NOT_SUPPORTED` — TODO ca qua đêm); `endDate - startDate + 1 <= maxSpanDays`; số buổi `<= maxOccurrences`.
- Cấu hình (`booking.schedule.*`, env): `BOOKING_MAX_OCCURRENCES`, `BOOKING_MAX_SPAN_DAYS`, `BOOKING_MIN_LEAD_TIME`,
  `BOOKING_TIMEZONE` — **giá trị mặc định cần chốt (Q3)**.
- Unit test: ONE_TIME, DAILY, WEEKLY nhiều thứ, khoảng không chứa thứ nào, start/end trùng ranh giới tuần/tháng/năm,
  start_date hôm nay giờ đã qua, quá số buổi / quá khoảng ngày, ngày quá khứ, `endTime <= startTime`, đổi UTC đúng (07:00 VN
  = 00:00Z), zone có DST (vd `America/New_York`) để chứng minh không lệch.

### 5.2 Trùng lịch (tầng service, trong transaction tạo/đổi caregiver)
- **Caregiver:** không có occurrence khác cùng `caregiver_id`, status `SCHEDULED/IN_PROGRESS`, thuộc booking đang hiệu lực
  (`PENDING, AWAITING_DEPOSIT, CONFIRMED, IN_PROGRESS` — Q4), giao giờ `a.start < b.end AND b.start < a.end`. Kiểm khi tạo
  (nếu có caregiver), accept (gán caregiver), đổi caregiver cả booking / một buổi.
- **Recipient:** tương tự với `bookings.recipient_id` (mọi trung tâm).
- Truy vấn một lần cho cả danh sách slot (`EXISTS … AND (start_at, end_at) giao với bất kỳ khoảng nào` — dựng bằng
  khoảng bao [min start, max end] rồi lọc giao chính xác trong Java), không N truy vấn.
- **Race:** hai request song song cùng qua bước kiểm. Đề xuất khóa tư vấn PostgreSQL trong transaction:
  `pg_advisory_xact_lock(hashtext('caregiver:'||id))` và `('recipient:'||id)` theo thứ tự cố định trước khi kiểm (repository
  native query; test H2 tạo `ALIAS` no-op). Dài hạn: exclusion constraint `tstzrange` (§3.2).

---

## 6. API

Đường dẫn trong service **không có `/api/v1`** (gateway cắt). Mọi response `APIResponse<T>`; danh sách `PageResponse<T>`
(`page` mặc định 0, `size` 20, tối đa 100). Không thấy / không thuộc phạm vi → **404 `BOOKING_NOT_FOUND`**.

### 6.1 Customer — `/bookings` (`hasRole('CUSTOMER')`, `customer_id` = sub)
| Method | Path | Mô tả |
|---|---|---|
| POST | `/bookings` | Tạo. Header tùy chọn `Idempotency-Key` (≤ 100 ký tự). Body: `recipientId, centerId, caregiverId?, scheduleType, startDate, endDate?, startTime, endTime, daysOfWeek?, customerNote?, services[1..10] {careServiceId, centerServiceId?, pricePlanId?}`. **Không** nhận giá/status/customerId (fail-on-unknown-properties). |
| GET | `/bookings` | Danh sách của tôi: `status?, recipientId?, page, size` (mới nhất trước) |
| GET | `/bookings/{id}` | Chi tiết + `details` + `occurrences` |
| GET | `/bookings/{id}/occurrences` | Danh sách buổi |
| POST | `/bookings/{id}/cancel` | Body `{reason}` |

Luồng tạo (rule #24 — không I/O mạng trong transaction):
1. Idempotency: có key + đã có booking `(customer, key)` → cùng hash: trả booking cũ (200, message "đã tạo trước đó");
   khác hash → 409 `IDEMPOTENCY_KEY_REUSED`.
2. Validate + sinh occurrence (thuần).
3. Feign CareRecipient `access(minLevel=EDITOR)` → không allowed → 404 `RECIPIENT_NOT_FOUND` (không lộ hồ sơ); Feign
   `snapshot` → status ≠ ACTIVE → 400 `RECIPIENT_NOT_ACTIVE`; CareRecipient lỗi/timeout → 503 `CARE_RECIPIENT_UNAVAILABLE`
   (không tạo booking thiếu snapshot).
4. `CaregiverPort.verifyAssignable(centerId, caregiverId, services, slots)` (nếu có caregiver) và
   `CareServiceCatalogPort.quote(centerId, services, slots)` (tạm: trả dòng không tên/giá).
5. `TransactionTemplate`: advisory lock → kiểm trùng → sinh `booking_code` → lưu booking + details + occurrences (`saveAll`) +
   history. Unique idempotency vi phạm (bấm 2 lần song song) → đọc lại booking đã tạo, trả như bước 1.

### 6.2 Center manager — `/center/bookings` (`hasRole('CENTER_MANAGER')`, `center_id` từ JWT qua `@CurrentCenterId`; thiếu claim → 403 `CENTER_SCOPE_REQUIRED`)
| Method | Path | Mô tả |
|---|---|---|
| GET | `/center/bookings` | `status?, caregiverId?, from?, to?` (ngày bắt đầu), page — kèm tên/SĐT khách (Identity batch, lỗi → thiếu profile) |
| GET | `/center/bookings/{id}` | chi tiết + khách hàng |
| POST | `/center/bookings/{id}/accept` | body `{caregiverId?}` — gán (nếu truyền) rồi chuyển AWAITING_DEPOSIT |
| POST | `/center/bookings/{id}/reject` | `{reason}` |
| PATCH | `/center/bookings/{id}/caregiver` | `{caregiverId}` — đổi cho booking + mọi buổi **SCHEDULED chưa bắt đầu** đang theo caregiver mặc định (buổi đã đổi riêng giữ nguyên) |
| PATCH | `/center/bookings/occurrences/{occurrenceId}/caregiver` | `{caregiverId}` — chỉ buổi SCHEDULED chưa bắt đầu |
| POST | `/center/bookings/{id}/cancel` | `{reason}` — trường hợp đặc biệt |

Đổi caregiver cho phép khi booking ∈ `PENDING, AWAITING_DEPOSIT, CONFIRMED, IN_PROGRESS`; luôn qua `CaregiverPort` + kiểm trùng.

### 6.3 Admin — `/admin/bookings` (`hasRole('ADMIN')`)
`GET /admin/bookings` (`status?, centerId?, customerId?, recipientId?, from?, to?`, page) · `GET /admin/bookings/{id}`. Chỉ đọc.

### 6.4 Caregiver — `/caregiver/occurrences` (`hasRole('CAREGIVER')`, so `occurrence.caregiver_id` với sub — Q1)
`GET /caregiver/occurrences?from=&to=&status?` (bắt buộc khoảng ngày, tối đa 62 ngày) · `GET /caregiver/occurrences/{id}`
(kèm thông tin chăm sóc từ `recipient_snapshot`, địa chỉ, ghi chú khách). Chỉ thấy buổi được giao **và** booking ∈
`CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED` (chưa xác nhận thì chưa lộ dữ liệu người bệnh).

### 6.5 Internal — `/internal/bookings` (`X-Internal-Secret`, gateway chặn)
| Method | Path | Mô tả |
|---|---|---|
| GET | `/internal/bookings/caregiver-access?caregiverId=&recipientId=` | `{caregiverId, recipientId, allowed}` — luôn 200. allowed khi có booking của recipient ở `CONFIRMED/IN_PROGRESS` (Q6) mà caregiver là caregiver của booking **hoặc** của một buổi SCHEDULED/IN_PROGRESS |
| POST | `/internal/bookings/{id}/deposit-paid` | body `{paymentReference?}` → CONFIRMED. TODO(Payment) |
| GET | `/internal/bookings/{id}` | booking + details (cho Payment/Review sau này) |
| GET | `/internal/bookings/occurrences/{occurrenceId}` | một buổi + bookingId/caregiver/recipient (cho care report sau này) |

### 6.6 Scheduler
`scheduler/BookingExpiryScheduler` (`@Scheduled(fixedDelayString = BOOKING_EXPIRY_SCAN_INTERVAL)`): lấy id cần hết hạn
(giới hạn lô), mỗi booking một transaction ngắn (khóa dòng → kiểm lại trạng thái → EXPIRE). Lỗi một booking không dừng lô.

---

## 7. Tích hợp service

### 7.1 Feign trong BookingService (`client/`, mỗi client có `*ClientConfig` không `@Configuration`, `*ErrorDecoder`,
`*FallbackFactory`, timeout từ env, CB Resilience4j cấu hình Java như CareRecipient)
| Client | Endpoint | Dùng cho | Khi lỗi |
|---|---|---|---|
| `IdentityClient` | `POST /internal/users/batch`, `GET /internal/users/{id}` | tên/SĐT khách cho manager/admin; `CaregiverPort` tạm | batch → map rỗng (hiển thị thiếu profile); get → 503 `IDENTITY_UNAVAILABLE` |
| `CareRecipientClient` | `GET /internal/care-recipients/{id}/access`, `/{id}/snapshot`, `POST /batch` | tạo booking; batch (dự phòng) | access/snapshot → 503 `CARE_RECIPIENT_UNAVAILABLE` (404 snapshot → `RECIPIENT_NOT_FOUND`); batch → rỗng |

- Tên người bệnh ở danh sách manager/admin lấy từ **`recipient_snapshot.fullName`** (đã lưu) → không cần gọi batch. Client
  batch vẫn khai báo nhưng chỉ dùng nếu anh/chị muốn hiển thị tên hiện hành (Q-phụ ở §9).
- Gọi qua service bọc (`IdentityUserService`, `CareRecipientDirectoryService`), không gọi Feign trực tiếp từ service nghiệp vụ.

### 7.2 Port chưa nối thật (`service/CareServiceCatalogPort`, `service/CaregiverPort` + `Impl/` — theo layout §2, không tạo package `port/`)
- `BOOKING_CATALOG_INTEGRATION_ENABLED=false`, `BOOKING_PROVIDER_INTEGRATION_ENABLED=false` (`booking.integration.*`).
- `CareServiceCatalogPort.quote(centerId, List<ServiceSelection>, List<OccurrenceSlot>)` → `List<ServiceQuote>` (tên, đơn vị,
  đơn giá, số lượng, thành tiền) + tổng. Impl tạm `UnpricedCatalogAdapter`: trả dòng chỉ có id, giá NULL. Bật cờ khi chưa có
  adapter thật → app **fail khi khởi động** (không âm thầm chạy tạm).
- `CaregiverPort.verifyAssignable(centerId, caregiverId, careServiceIds, slots)`. Impl tạm `IdentityBackedCaregiverAdapter`:
  gọi Identity `GET /internal/users/{id}` → phải `ACTIVE` + role `CAREGIVER` (không kiểm được thuộc center / kỹ năng / lịch
  rảnh — TODO Provider). Q12 nếu muốn no-op hoàn toàn.

### 7.3 API `/internal/**` mà Catalog / Provider cần cung cấp cho Booking (giai đoạn sau)
**CareCatalogService**
- `POST /internal/center-services/quote` — body `{centerId, items[{careServiceId, centerServiceId, pricePlanId}]}` → mỗi dòng
  `{careServiceId, centerServiceId, pricePlanId, serviceName, pricingUnit, durationMinutes, basePrice, active}`; lỗi khi
  center_service không thuộc center, inactive, price plan không thuộc center_service / inactive.
- (tùy chọn) `GET /internal/center-services/{id}`, `GET /internal/price-plans/{id}`.
- Cần chốt: quy tắc tính `quantity` theo `pricing_unit` (HOUR = số giờ mỗi buổi × số buổi? SHIFT = số buổi? DAY?) và % đặt cọc.

**ProviderService**
- `GET /internal/centers/{centerId}` → `{centerId, status, managerUserId}` (booking chỉ cho center ACTIVE).
- `GET /internal/caregivers/by-user/{userId}` (hoặc `/internal/caregivers/{caregiverId}`) → `{caregiverId, userId, centerId,
  verificationStatus, caregiverStatus, gender, experienceYears, ratingAverage}`.
- `POST /internal/caregivers/{caregiverId}/availability-check` — body `{slots[{startAt,endAt}], careServiceIds[]}` →
  `{available, conflicts[], scheduleIds[]}` (dựa trên `caregiver_schedules`/`work_shifts`; trả `schedule_id` để lưu
  `availability_slot_id`).
- Kỹ năng caregiver ↔ dịch vụ: **Provider chưa có bảng kỹ năng** → cần bảng `caregiver_skills (caregiver_id, care_service_id)`
  hoặc chấp nhận bỏ kiểm "làm được dịch vụ".
- (sau) event/khóa lịch khi booking CONFIRMED / hủy để Provider không xếp trùng.

### 7.4 CareRecipientService — phần DUY NHẤT được sửa
- `client/BookingClient` (`GET /internal/bookings/caregiver-access`) + `BookingClientConfig` (header
  `X-Internal-Secret` = `CARE_RECIPIENT_BOOKING_INTERNAL_SECRET`, X-Request-Id) + `BookingErrorDecoder` + `BookingClientFallbackFactory`
  (ném `BOOKING_UNAVAILABLE` 503 — **fail-closed**, không đoán cho xem).
- `CaregiverAssignmentServiceImpl.isAssigned` → gọi Booking (bỏ TODO).
- Endpoint mới cho caregiver (Q7): `GET /caregiver/care-recipients/{id}` (`hasRole('CAREGIVER')`, controller mới
  `CaregiverCareRecipientController`) → `RecipientSnapshotResponse` (dữ liệu cần để chăm sóc; không lộ danh sách người
  thân). Không phân công → 404 `RECIPIENT_NOT_FOUND`; Booking lỗi → 503 `BOOKING_UNAVAILABLE` (mã mới 2502…2504 dải 25xx/
  tích hợp). Hồ sơ ARCHIVED → 404.
- Config: `care-recipient.booking.url/internal-secret` (env `CARE_RECIPIENT_BOOKING_URL`, `CARE_RECIPIENT_BOOKING_INTERNAL_SECRET`,
  `CARE_RECIPIENT_BOOKING_CONNECT_TIMEOUT_MS`, `CARE_RECIPIENT_BOOKING_READ_TIMEOUT_MS`). `.env.example`, README, AGENTS riêng
  cập nhật. **Mọi API/hành vi cũ giữ nguyên**; 55 test cũ phải pass.
- ⚠️ Vòng phụ thuộc Booking → CareRecipient → Booking chỉ ở API đọc khác nhau, không gọi lồng nhau trong cùng request.

### 7.5 ApiGateway
- `GatewayPaths.BOOKING_ROUTES = /api/v1/bookings/**, /api/v1/center/bookings/**, /api/v1/admin/bookings/**,
  /api/v1/caregiver/occurrences/**`; `CARE_RECIPIENT_ROUTES` thêm `/api/v1/caregiver/care-recipients/**`.
- `RouteConfig.bookingRoutes()` (uri → stripPrefix(2) → sanitize → removeResponseHeader), `/api-docs/booking`,
  `GatewayProperties.bookingUrl` (`GATEWAY_BOOKING_URL`), swagger-ui thêm nguồn `booking`. `/internal/**` vẫn chặn
  (filter có sẵn). README + `.env.example` + test route mới (upstream giả thứ 3).
- Không dùng prefix chung `/api/v1/caregiver/**` vì Provider đã có `/caregiver/profile`, `/caregiver/schedules`… (route sau
  này của Provider) — route từng path cụ thể.

---

## 8. Kiểm thử & e2e

| Loại | Nội dung |
|---|---|
| Unit (không Spring) | `OccurrenceGeneratorTests` (§5.1), `BookingActionTests` (mọi cặp trạng thái × hành động: hợp lệ → đích đúng, còn lại → `INVALID_BOOKING_TRANSITION`), `ScheduleConflictTests` (giao/không giao/chạm biên `end == start` không tính trùng), `BookingMapperTests` (snapshot → service_address, caregiver mặc định cho occurrence, giá NULL, field server không chép từ request), `*ErrorDecoderTests` |
| MockMvc + H2 (stub HTTP JDK như `TestIdentityServer`: JWKS + Identity + CareRecipient giả) | tạo booking (thành công, Idempotency-Key replay/khác body, recipient không quyền → 404, INACTIVE → 400, CareRecipient tắt → 503 & không có dòng nào trong DB, trùng caregiver/recipient → 409), customer xem booking người khác → 404, manager khác center → 404, thiếu claim center → 403, accept/reject/cancel/đổi caregiver, caregiver chỉ thấy buổi của mình, admin, internal secret (thiếu/sai → 401, đúng → 200), deposit-paid, refresh token / sai audience → 401, CSRF cookie |
| PostgreSQL thật | chạy lại bộ test với cờ `-Dbooking.testing.postgres=true` trên container riêng (schema SQL + `validate`, jsonb, smallint[], advisory lock) |
| CareRecipient | test mới cho endpoint caregiver (assigned / not assigned / Booking down) + toàn bộ test cũ |
| Gateway | route 4 path booking + `/caregiver/care-recipients`, `/internal/bookings` → 404 |
| E2E (mở rộng `scripts/e2e/Run-E2E.ps1` + `e2e.py`, phase **e**/**f**) | thêm hạ tầng Booking e2e (port 55603), chạy 4 JAR; seed thêm CENTER_MANAGER (có `center_id`) + 2 CAREGIVER vào DB Identity e2e (`SeedCustomers.java` mở rộng, vì tài khoản provision cần OTP). Luồng: customer login → tạo hồ sơ → booking WEEKLY → kiểm occurrences → manager accept (gán caregiver) → `deposit-paid` (gọi thẳng service có secret) → CONFIRMED → customer xem chi tiết; ca lỗi: hồ sơ không quyền 404, trùng lịch 409, hủy booking (occurrence → CANCELLED), caregiver xem hồ sơ qua `/api/v1/caregiver/care-recipients/{id}` khi được phân công (200) / caregiver khác (404); tắt CareRecipient → tạo booking 503 và không có booking mới |

---

## 9. Điểm cần anh/chị chốt (đề xuất in đậm)

| # | Câu hỏi | Đề xuất |
|---|---|---|
| Q1 | `caregiver_id` trong Booking là gì? Provider có `caregiver_profiles.caregiver_id` (id riêng) **và** `user_id` (Identity, unique 1-1). | **Lưu Identity `user_id`** (cột vẫn tên `caregiver_id`, comment rõ). Lý do: caregiver đăng nhập bằng JWT `sub` = user_id → `/caregiver/occurrences` và `caregiver-access` (CareRecipient truyền `sub`) chạy được ngay, không phụ thuộc Provider; validate tạm qua Identity. Khi nối Provider thêm cột `caregiver_profile_id` nếu cần. *Phương án khác:* lưu Provider `caregiver_id` → phải có API Provider map user ↔ profile mới làm được phần caregiver. |
| Q2 | `center_id` = claim JWT của manager, giả định trùng Provider `care_centers.center_id` (hiện đồng bộ tay). Provider phân quyền bằng `manager_user_id`. | **Dùng claim `center_id`** (như CareCatalog); ghi rủi ro + TODO kiểm `GET /internal/centers/{id}` khi nối Provider. Booking tạo bởi customer nhận `centerId` trong body, chưa xác minh tồn tại. |
| Q3 | Số liệu nghiệp vụ (AGENTS: không tự đặt): số buổi tối đa, khoảng ngày tối đa, thời gian báo trước tối thiểu, hạn đặt cọc, chu kỳ quét hết hạn. | Mặc định đề xuất (đều cấu hình qua env): **60 buổi, 90 ngày, báo trước 2 giờ, hạn cọc 24 giờ sau accept nhưng không quá giờ buổi đầu, quét mỗi 5 phút**. |
| Q4 | Booking PENDING có giữ chỗ caregiver (tính trùng) không? | **Có**: tính PENDING, AWAITING_DEPOSIT, CONFIRMED, IN_PROGRESS (EXPIRED/REJECTED/CANCELLED giải phóng). |
| Q5 | Caregiver bắt buộc lúc tạo? | **Không bắt buộc** (khách có thể chọn); **bắt buộc trước khi accept** (`CAREGIVER_REQUIRED`). |
| Q6 | `caregiver-access` cho xem hồ sơ ở trạng thái nào? | **CONFIRMED, IN_PROGRESS** (không cho khi PENDING/AWAITING_DEPOSIT/kết thúc). |
| Q7 | Caregiver xem hồ sơ người bệnh qua endpoint nào? | **Mới `GET /caregiver/care-recipients/{id}`** trả snapshot + route gateway. Booking lỗi → 503 (fail-closed). |
| Q8 | Permission code riêng cho booking (`booking:assign`…)? | **Chưa** — chỉ `hasRole(...)` như CareRecipient (catalog permission chưa chốt). |
| Q9 | `booking_details` thêm `price_plan_id`; tên/đơn vị/giá/số lượng NULL ở giai đoạn này. | **Đồng ý thêm.** |
| Q10 | Idempotency lưu ở đâu? | **Cột trên `bookings`** + unique `(customer_id, idempotency_key)`, không hết hạn, không Redis. Khác body cùng key → 409. |
| Q11 | Bảng `booking_status_history`? | **Có** (chỉ ghi, chưa API đọc). |
| Q12 | `CaregiverPort` tạm: kiểm qua Identity (ACTIVE + role CAREGIVER) hay không kiểm gì? | **Kiểm qua Identity** (không đổi Identity). Không kiểm được thuộc center — có thể thêm `centerId` vào `InternalUserResponse` của Identity (đổi nhỏ, additive) nếu anh/chị muốn chặn ngay caregiver khác trung tâm. |
| Q13 | Manager hủy được khi IN_PROGRESS? | **Có** (trường hợp đặc biệt): hủy các buổi SCHEDULED còn lại, booking → CANCELLED. |
| Q14 | Customer hủy CONFIRMED trước giờ buổi đầu? | **Có**, kèm lý do; số tiền hoàn = TODO `RefundPolicy`. |
| Q15 | Thêm cột ngoài danh sách yêu cầu: `accepted_by/at`, `deposit_due_at`, `confirmed_at`, `rejected_by/at`, `idempotency_key/hash`, `deleted_at` (BaseEntity). | **Đồng ý** (cần cho scheduler/idempotency/audit). |
| Q16 | Mở rộng script e2e chung (thêm Booking, seed manager/caregiver thẳng vào DB Identity e2e). | **Đồng ý.** |
| Phụ | Danh sách manager/admin hiển thị tên người bệnh từ snapshot (lúc đặt) thay vì gọi batch CareRecipient. | **Dùng snapshot**, không gọi batch. |

---

## 10. File sẽ tạo / sửa

### BookingService (mới — xóa `.gitignore`, `HELP.md` của Initializr; giữ `.idea/` của IDE)
```
pom.xml (sửa), docker-compose.yml, .env.example, README.md, AGENTS.md, scripts/Start-Dev.ps1, schema/booking.sql
src/main/resources/application.yaml
src/main/java/mss301/bookingservice/
  BookingServiceApplication (+@EnableFeignClients, @EnableScheduling, TZ UTC)
  config/   BookingProperties, BookingConfig (Clock), WebConfig, RequestIdFilter, ResilienceConfig, swagger/OpenApiConfig
  security/ SecurityConfig, InternalSecurityConfig, InternalSecretFilter, JwtConfig, JwtAuthorityConverter,
            ReadOnlyCookieCsrfTokenRepository, SecurityErrorHandler, CookieUtils, TokenClaims,
            CurrentUserId(+Resolver), CurrentCenterId(+Resolver)
  controller/ BookingController, CenterBookingController, AdminBookingController, CaregiverOccurrenceController,
              InternalBookingController
  dto/request/  BookingCreateRequest, BookingServiceRequest, CancelRequest, RejectRequest, AcceptRequest,
                CaregiverAssignRequest, DepositPaidRequest
  dto/response/ APIResponse, PageResponse, BookingResponse, BookingSummaryResponse, BookingDetailResponse(+customer),
                BookingItemResponse, OccurrenceResponse, CaregiverOccurrenceResponse, CaregiverAccessResponse,
                RecipientSnapshot (jsonb), CustomerProfileResponse
  entity/   BaseEntity, Booking, BookingOccurrence, BookingDetail, BookingStatusHistory
  enums/    BookingStatus, BookingAction, OccurrenceStatus, ScheduleType, UnitType, ActorType
  exception/ AppException, ErrorCode, GlobalExceptionHandler
  mapper/   MapStructConfig, IgnoreServerManagedFields, IgnoreServerManagedBookingFields, BookingMapper, OccurrenceMapper,
            BookingDetailMapper, BookingStatusHistoryMapper
  repository/ BookingRepository, BookingOccurrenceRepository, BookingDetailRepository, BookingStatusHistoryRepository,
              BookingSpecifications, ScheduleLockRepository
  service/(+Impl/) BookingService, CenterBookingService, AdminBookingService, CaregiverOccurrenceService,
              InternalBookingService, OccurrenceGenerator, ScheduleConflictService, BookingCodeGenerator,
              IdentityUserService, CareRecipientDirectoryService, CareServiceCatalogPort, CaregiverPort, RefundPolicy,
              BookingExpiryService
  client/   IdentityClient(+Config, ErrorDecoder, FallbackFactory), CareRecipientClient(+…), dto/…
  scheduler/ BookingExpiryScheduler
  util/     IdempotencyHasher (SHA-256)
src/test/…  (§8)
```
### CareRecipientService (chỉ phần §7.4)
`client/BookingClient`, `BookingClientConfig`, `BookingErrorDecoder`, `BookingClientFallbackFactory`, `client/dto/BookingApiResponse`,
`client/dto/CaregiverAccessResponse`; `controller/CaregiverCareRecipientController`; `service/CaregiverRecipientService` (+Impl);
sửa `CaregiverAssignmentServiceImpl`, `CareRecipientProperties`, `ResilienceConfig` (lỗi nghiệp vụ không mở mạch), `ErrorCode`
(+25xx), `application.yaml`, `.env.example`, `README.md`, `AGENTS.md`; test mới.
### ApiGateway
`GatewayPaths`, `GatewayProperties`, `RouteConfig`, `application.yaml`, `.env.example`, `README.md`, test.
### Chung
`AGENTS.md` (bảng domain → `BookingService`, phạm vi hiện tại, dải lỗi 5000–5999), `docs/PLAN.md`, `scripts/e2e/*`, plan này.

## 11. Thứ tự thực hiện (sau khi xác nhận)
- [x] 0. ProviderService: API nội bộ chỉ đọc `/internal/caregivers/{id}`, `/by-user/{userId}`, `/internal/centers/{id}` + test — 2026-10-09.
- [x] 1. BookingService: pom/config/security/exception → schema SQL → entity/repository → enums + state machine → generator
      (+ unit test) → mapper → client Feign + port → service → controller → scheduler → MockMvc test → PostgreSQL test — 2026-10-09.
- [x] 2. CareRecipientService: Booking client + endpoint caregiver + test (giữ test cũ pass) — 2026-10-09.
- [x] 3. ApiGateway: route + test — 2026-10-09.
- [x] 4. E2E 5 JAR + PostgreSQL thật; cập nhật README/AGENTS/PLAN; báo cáo — 2026-10-09 (kết quả §13).

## 12. TODO các giai đoạn sau
- **Đợt riêng ProviderService:** bảng `caregiver_services (caregiver_id, care_service_id)` + API quản lý kỹ năng; khi có,
  `/internal/caregivers/**` trả danh sách thật (không còn `null`) và Booking tự động kiểm chặt.
- Provider: API kiểm khung giờ làm việc (`caregiver_schedules`) → gán `availability_slot_id`; vẫn giữ Booking là nguồn sự
  thật về giờ đã đặt.
- Nối Catalog (quote giá, quy tắc `quantity`, % cọc → `subtotal/deposit/total`) — `CareServiceCatalogPort` + cờ
  `BOOKING_CATALOG_INTEGRATION_ENABLED`.
- Payment: thay `deposit-paid` nội bộ bằng event + outbox; `RefundPolicy` theo % mốc ngày (chưa chốt).
- Bắt đầu/kết thúc ca (`actual_start_at/end_at`, CONFIRMED → IN_PROGRESS → COMPLETED, MISSED), `occurrence_services`,
  `care_reports`, `feedbacks`; chốt ranh giới với `caregiver_schedules` của Provider.
- Ca qua đêm; exclusion constraint `tstzrange`; thông báo (Notification) khi đổi trạng thái; Flyway (đợt chung).

## 13. Kết quả triển khai (2026-10-09, chưa commit)

### Test tự động

| Project | Trước | Sau | Ghi chú |
|---|---|---|---|
| ProviderService | 12/12 | **17/17** (H2) | +5 `InternalProviderApiTests` |
| BookingService | — | **66/66** trên H2 và **66/66** trên PostgreSQL 17.11 | 19 tích hợp MockMvc + 47 unit (generator 13, state machine 4, trùng lịch 8, CaregiverPort 8, mapper 8, ErrorDecoder 3, RefundPolicy 3) |
| CareRecipientService | 55/55 | **59/59** (H2) | 55 test cũ không đổi + 4 test caregiver xem hồ sơ |
| ApiGateway | 14/14 | **16/16** | +2; test 404 cũ đổi path ví dụ `/api/v1/bookings` → `/api/v1/unknown-resources` |

### End-to-end (`scripts/e2e/Run-E2E.ps1`, 5 JAR, PostgreSQL 17 + Redis 7, compose project riêng, dọn sạch sau khi chạy)

**a 19/19, b 14/14, c 10/10, e 30/30, d 4/4, f 2/2 — E2E PASSED (79 check).** Phase e: tạo hồ sơ → booking WEEKLY qua
gateway (PENDING, mã BK-…, snapshot + địa chỉ, giá NULL, 6 buổi 01:00Z) → Idempotency-Key trả cùng booking → hồ sơ không
quyền 404 5601 → trùng lịch người bệnh 409 5216 → trung tâm thấy booking kèm tên khách → accept + phân công caregiver
(Provider, đúng `caregiverUserId`) → caregiver chưa xem được hồ sơ (404) → `/api/v1/internal/**` bị chặn, deposit-paid
thiếu secret 401 → deposit-paid → CONFIRMED → khách xem chi tiết → caregiver thấy 6 buổi + chi tiết có snapshot, caregiver
khác 404 → caregiver xem hồ sơ qua CareRecipient (200), caregiver khác 404 2100 → trùng lịch caregiver 409 5215 → hủy
booking (mọi buổi CANCELLED). Phase f: tắt CareRecipient → tạo booking 503 5600, số booking không đổi.

### Điểm lệch so với plan / ghi chú

- Query param `caregiver-access` là `caregiverUserId` (yêu cầu ghi `caregiverId`) — CareRecipient chỉ có `sub` của JWT.
- Dải lỗi 57xx dành cho tích hợp Provider; thêm `acceptance_due_at` (Q4) và `rejected_by/at`, `idempotency_key/hash`.
- E2E tự cấp cấu hình Booking/Provider + secret nội bộ sinh ngẫu nhiên mỗi lần chạy (không sửa `.env`); BookingService không
  cần `.env` để chạy e2e. Seed CENTER_MANAGER/CAREGIVER (Identity e2e) và trung tâm/caregiver (Provider e2e) chỉ vào DB e2e.
- **Cấu hình bắt buộc mới**: `CARE_RECIPIENT_BOOKING_URL` (CareRecipient) và `GATEWAY_BOOKING_URL` (Gateway) là
  `@NotBlank` (fail-fast như URL khác) — `.env` cục bộ hiện có của hai service phải bổ sung trước khi chạy `Start-Dev.ps1`.
