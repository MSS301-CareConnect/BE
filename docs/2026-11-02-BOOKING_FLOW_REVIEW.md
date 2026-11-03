# Rà soát luồng Booking theo đặc tả CareConnect (2026-11-02)

> Đối chiếu `CareConnect_Booking_Business_Specification.docx` (v1.0, 02/11/2026) với code hiện tại của
> BookingService, PaymentService, ProviderService và FE-careconnect.
> **Trạng thái: X1 + đợt 1–6 đã làm xong (§8, §9, §10, §11). Tài liệu luồng sau khi sửa: `2026-11-03-BOOKING_FLOW_V1.md`. Không commit / push (chủ dự án tự làm).**
>
> Vị trí file: dự án đặt tài liệu tại `BE/docs/<yyyy-MM-dd>-<TÊN>.md` (BE/AGENTS.md §2), nên không tạo `docs/booking/`.
> Tài liệu booking cũ: `2026-10-09-PLAN_BOOKING.md`, `GAP_ANALYSIS.md` (D1–D14),
> `2026-10-28-FULLSTACK_GAP_ANALYSIS.md` (D15–D30), `IMPLEMENTATION_PROGRESS.md`.

---

## 1. Rule và cấu trúc dự án sẽ áp dụng

### 1.1 Backend (`BE/AGENTS.md`, `BE/BookingService/AGENTS.md`)

- **Microservice.** Mỗi service có DB riêng. Tham chiếu chéo bằng UUID, không FK xuyên DB. Gọi service khác bằng Feign
  qua gateway: dùng token người dùng (`FeignAuthForwardingInterceptor`), hoặc token `SERVICE` cho scheduler/outbox.
  Không còn `/internal/**`.
- **Layout.**
  - Package `controller/ service/ service/Impl/ repository/ entity/ enums/ dto/request|response/ mapper/ client/ scheduler/ util/ config/ security/`.
  - Mỗi aggregate có đúng một bộ Controller → Service → ServiceImpl → Repository. Không tách theo role.
  - `repository/` và `service/` chỉ chứa interface.
- **State machine booking.**
  - Chỉ định nghĩa ở `enums/BookingAction`.
  - Mọi đổi trạng thái đi qua `BookingLifecycleService.apply`, trong transaction, trên booking đã khóa dòng (`findForUpdate`).
  - Thứ tự khóa: booking → buổi → session.
- **Giao tiếp và transaction.**
  - Không I/O mạng trong transaction. Gọi mạng trước, ghi bằng `TransactionTemplate`.
  - Gửi sang service khác qua outbox, idempotency key.
- **Code style.**
  - Lombok `@RequiredArgsConstructor` + `@FieldDefaults(makeFinal)`.
  - MapStruct với `MapStructConfig` cho mọi chuyển đổi.
  - DTO dùng `@Data @Builder…`. Validation message là key của `ErrorCode`.
  - Lỗi qua `AppException(ErrorCode)`, dải mã của Booking là 5000–5999. Message tiếng Anh, comment tiếng Việt.
  - Controller mỏng, trả `APIResponse<T>`. Method xếp GET → POST → PUT → PATCH → DELETE.
- **Database.**
  - Không Flyway. Thay đổi schema cần cập nhật `schema/<service>.sql` và thêm change script ở `schema/changes/`.
  - Đổi enum phải sửa CHECK constraint.
  - `ddl-auto: validate`.
- **Contract.** Đổi contract phá vỡ thì mở `/api/v2`, không sửa v1 (AGENTS §3 "Aggregate & versioning").
- **Phạm vi dữ liệu.**
  - userId/centerId lấy từ token (`@CurrentUserId`, `@CurrentCenterId`, `@CurrentRoles`).
  - Ngoài phạm vi trả 404.
- **Test.**
  - `mvnw.cmd test` chạy H2. Có alias advisory lock. Khóa thật kiểm bằng `-Dbooking.testing.postgres=true`.
  - **Baseline hôm nay: BookingService 110 test, 0 fail, 2 skip.**
- **Thiếu business rule thì hỏi**, không tự đặt số (rule #31). Không commit (chủ dự án tự commit).

### 1.2 Frontend (`FE-careconnect/CLAUDE.md` → `AGENTS.md`, `ARCHITECTURE.md`)

- **Stack:** React 19, React Router (SPA), TanStack Query, Tailwind, Orval, Vitest, pnpm.
- **Lớp:** `routes → features/<role>/<feature> → entities → shared`. Feature không import feature khác. Tên file kebab-case.
- **API.** API sinh từ `openapi/openapi.yaml` (`pnpm api:generate`, có validate). **Không sửa tay `generated/`.**
  Wrapper ở `entities/*/…-api.ts`.
- **UI text.** Mọi text qua i18n (`shared/i18n/...`, vi + en). Rule ESLint `no-hardcoded-ui-text` chặn text cứng.
- **Prettier:** không dấu chấm phẩy, nháy đơn, printWidth 120.
- **Kiểm tra:** `pnpm check` (prettier, lint, typecheck, test, build).
- **Nguyên tắc:** BE quyết định nghiệp vụ. FE chỉ ẩn/hiện hành động.

---

## 2. Tóm tắt nghiệp vụ Booking theo spec

### 2.1 Vai trò (§2, §12)

| | CUSTOMER | CENTER_MANAGER | CAREGIVER | ADMIN |
|---|---|---|---|---|
| Tạo booking | ✔ | ✘ | ✘ | ✘ |
| Xem danh sách / chi tiết | booking của mình | booking của trung tâm | booking/buổi được giao | tất cả |
| Xem lịch caregiver | ✔ | ✔ | không bắt buộc | ✔ |
| Xác nhận / từ chối | ✘ | đúng trung tâm | ✘ | không trong luồng thường |
| Phân caregiver | ✘ | đúng trung tâm | ✘ | không trong luồng thường |
| Hủy booking | booking của mình | đúng trung tâm | ✘ | xử lý đặc biệt "nếu bổ sung" |
| Cập nhật center policy | ✘ | trung tâm mình | ✘ | mọi trung tâm |

### 2.2 Ba loại booking (§4)

| | SHIFT | RECURRING | CUSTOM |
|---|---|---|---|
| Caregiver | bắt buộc | tùy chọn | tùy chọn |
| Đầu vào | `caregiverScheduleId` | khoảng ngày + giờ + DAILY/WEEKLY | danh sách `{startAt, endAt}` |
| Số buổi | 1 | nhiều, sinh theo quy tắc | nhiều, theo danh sách |
| Thanh toán đầu | 100% sau khi trung tâm xác nhận | cọc theo policy (mặc định 30%) | cọc theo policy |
| Phần còn lại | không có | trả trước buổi đầu theo policy | trả trước buổi đầu theo policy |

### 2.3 Luồng chính (§3, §5)

**Bước 0 — hình thành lịch caregiver.** Manager tạo work shift → caregiver đăng ký → manager duyệt → ProviderService sinh
`CaregiverSchedule`.

**Bước 1 — customer tạo booking.**
1. Chọn trung tâm ACTIVE.
2. Chọn caregiver VERIFIED + ACTIVE có dịch vụ phù hợp. SHIFT bắt buộc; RECURRING/CUSTOM có thể bỏ trống.
3. Với SHIFT: chọn schedule còn trống. Schedule đã bị giữ phải hiện "không còn trống".
4. Chọn hồ sơ người nhận, dịch vụ, price plan.
5. Catalog báo giá. Booking tính tổng và khoản thanh toán đầu.
6. Tạo booking ở trạng thái `PENDING_CENTER_CONFIRMATION`.

**Bước 2 — trung tâm xử lý.**
- Xác nhận khi đã có caregiver → `AWAITING_INITIAL_PAYMENT`.
- Xác nhận khi RECURRING/CUSTOM chưa có caregiver → `PENDING_CAREGIVER_ASSIGNMENT`. Phân caregiver đúng hạn → `AWAITING_INITIAL_PAYMENT`.
- Từ chối → `REJECTED`.
- Quá hạn (xác nhận / phân công / thanh toán) → `EXPIRED` và giải phóng lịch.

**Bước 3 — customer thanh toán.**
- Trả khoản đầu → `CONFIRMED`.
- RECURRING/CUSTOM phải trả nốt phần còn lại trước mốc "24h trước buổi đầu" (theo policy). Phải FULLY PAID trước khi bắt đầu chăm sóc.

**Bước 4 — chăm sóc.**
- Caregiver check-in trong cửa sổ policy → `IN_PROGRESS`.
- Hết buổi → `COMPLETED`.

### 2.4 State machine theo spec (§11)

**Booking**

| Trạng thái | Chuyển tiếp chính |
|---|---|
| PENDING_CENTER_CONFIRMATION | xác nhận / từ chối / hủy / hết hạn |
| PENDING_CAREGIVER_ASSIGNMENT | phân caregiver / hết hạn (hoặc trung tâm từ chối, §5.3) |
| AWAITING_INITIAL_PAYMENT | trả thành công → CONFIRMED / hết hạn |
| CONFIRMED | IN_PROGRESS / CANCELLED / hoàn tiền |
| IN_PROGRESS | COMPLETED / xử lý sự cố |
| COMPLETED, REJECTED, CANCELLED, EXPIRED | kết thúc |

**Occurrence:** PENDING_ASSIGNMENT, RESERVED, SCHEDULED, ACKNOWLEDGED, IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED,
CUSTOMER_NO_SHOW, CAREGIVER_MISSED.
- Trạng thái chiếm lịch: RESERVED, SCHEDULED, ACKNOWLEDGED, IN_PROGRESS.
- Status history phải có `previousStatus`, `newStatus`, `actorUserId`, `reason`, thời điểm.

### 2.5 Business rule

- **Chống trùng lịch (§6).**
  - Schedule thuộc đúng center + caregiver và có status SCHEDULED, chưa bị buổi khác giữ.
  - `endAt > startAt` và đạt thời lượng tối thiểu.
  - Các buổi trong cùng booking không chồng nhau.
  - Caregiver không có hai buổi giao nhau ở trạng thái chiếm lịch.
  - Kiểm ở cả Provider và Booking; DB là chốt chặn cuối.
- **Giá (§7).**
  - HOUR = phút/60, làm tròn theo quy tắc tiền tệ. DAY = 1. SHIFT = 1.
  - Subtotal = tổng các buổi. Discount = 0.
  - Snapshot giá lưu vào booking details và occurrence.
- **Center booking policy (§8).** Đọc tại thời điểm xử lý, không hardcode.

  | Mục | Mặc định |
  |---|---|
  | SHIFT min advance | 2h |
  | RECURRING / CUSTOM min advance | 48h |
  | Xác nhận | 24h |
  | Phân công | 24h |
  | Thanh toán đầu | 24h |
  | Phần còn lại | trả trước buổi đầu 24h |
  | Cọc | 30% |
  | Check-in | sớm 30', trễ 30' |

  Backend phải từ chối booking nếu không còn deadline hợp lệ trong tương lai.
- **Thanh toán (§9).** Payment mang `bookingId`, `customerUserId`, `centerId`. Doanh thu tổng hợp theo `centerId`;
  manager xem được doanh thu trung tâm mình. Không commission / settlement.
- **Hủy và hoàn tiền (§10).**
  - Customer hủy booking của mình. Manager hủy booking cùng trung tâm.
  - Không hủy thường khi booking đã hoàn tất hoặc đã có buổi bắt đầu.
  - RECURRING/CUSTOM hủy được từng buổi:

    | Tình huống | Mức hoàn |
    |---|---|
    | Customer hủy, buổi còn ≥ 24h | 100% |
    | Customer hủy, buổi còn < 24h | 70% |
    | Trung tâm hủy / không có caregiver | 100% |
    | Caregiver vắng (CAREGIVER_MISSED) | 100% |

  - PaymentService là nơi tạo refund.
- **Edge case (§15).**
  - Đặt trùng đồng thời → chỉ một thành công.
  - Caregiver bị khóa trước khi xác nhận → không xác nhận với người đó; RECURRING/CUSTOM có thể phân người khác.
  - Giá đổi sau khi tạo → booking giữ snapshot.
  - Quá hạn → EXPIRED.
  - Callback thanh toán lặp → idempotent.
  - userId/centerId trong body → bỏ qua.

---

## 3. Bảng vấn đề (spec ↔ code)

> Viết tắt đường dẫn:
> - `BKS` = `BE/BookingService/src/main/java/mss301/bookingservice/`
> - `PAY` = `BE/PaymentService/src/main/java/mss301/paymentservice/`
> - `PRV` = `BE/ProviderService/src/main/java/mss301/providerservice/`
> - `FE` = `FE-careconnect/app/`
>
> Mức độ: **C** Critical · **H** High · **M** Medium · **L** Low.
>
> Nhận xét chung: code hiện tại **chạy nhất quán theo thiết kế cũ** (PLAN_BOOKING 2026-10-09, D1–D30); test BE pass hết.
> Phần lớn sai lệch là do **spec mới thay đổi mô hình nghiệp vụ**, không phải bug lẻ. Tôi chưa tái hiện được lỗi runtime
> cụ thể nào bạn gặp — xem Câu hỏi mở Q0.

| # | Vấn đề | Spec | Code hiện tại | Mức | Phía |
|---|---|---|---|---|---|
| B1 | **Thiếu trạng thái `PENDING_CAREGIVER_ASSIGNMENT`.** Trung tâm không thể "xác nhận trước, phân người sau". Booking chưa có caregiver bị chặn ở accept. | §5.2, §5.3, §11.1, §16 | `BKS/enums/BookingStatus.java:7-15` không có trạng thái này. `BookingServiceImpl.java:640-643` ném `CAREGIVER_REQUIRED` (ErrorCode 5400, `ErrorCode.java:114`). | **C** | BE, FE, DB |
| B2 | **Tên trạng thái booking khác spec.** `PENDING` ≙ PENDING_CENTER_CONFIRMATION; `AWAITING_DEPOSIT` ≙ AWAITING_INITIAL_PAYMENT. | §11.1 | `BookingStatus.java:8-9`; CHECK `schema/booking.sql:61`, `:198-201`; FE `contracts/bookingStatus.ts`, `shared/i18n/booking-resources.ts:2-30` | H (đổi tên phá contract v1) | BE, FE, DB |
| B3 | **Ba loại booking không khớp.** Code có ONE_TIME/DAILY/WEEKLY. Không có SHIFT gắn `caregiverScheduleId`, cũng không có CUSTOM (danh sách startAt/endAt). Mọi buổi cùng khung giờ. | §4, §5.1, §16 ("SHIFT không thể tạo nếu thiếu caregiverId hoặc caregiverScheduleId") | `BKS/enums/ScheduleType.java:4-8`; `dto/request/BookingCreateRequest.java:18-37`; `OccurrenceGeneratorImpl` (sinh theo ngày, cùng giờ); CHECK `booking.sql:60,64,68-73` | **C** | BE, FE, DB |
| B4 | **FE tách nhiều ngày ONE_TIME thành N booking riêng** (mỗi ngày một POST). Spec gọi đây là một booking CUSTOM nhiều buổi; tách ra thì sai giá cọc, sai hủy từng buổi, sai giới hạn PENDING (3). | §4 CUSTOM | `FE/features/customer/booking/hooks/use-booking-form.ts:140-144`, `:156-175` | H | FE (+ BE khi có CUSTOM) |
| B5 | **Thanh toán phần còn lại sau khi hoàn tất**, trái spec. Spec: trả trước buổi đầu và phải FULLY PAID trước khi chăm sóc. Code: hóa đơn REMAINING chỉ sinh khi FINALIZE (hết mọi buổi), hạn +48h. Check-in chỉ cần đã cọc. | §9.1 (spec ghi rõ không chọn phương án thu sau), §14 | `BKS/service/Impl/BookingLifecycleServiceImpl.java:104-110` (enqueue REMAINING_INVOICE khi FINALIZE); `PAY` `RemainingInvoiceServiceImpl` (charge theo buổi COMPLETED/NO_SHOW, due 48h); `BKS/service/Impl/CareSessionServiceImpl.java:248` (chỉ kiểm CONFIRMED/IN_PROGRESS) | **C** | BE (Booking + Payment), FE, DB |
| B6 | **SHIFT phải trả 100%,** code luôn tính cọc theo `deposit_percent` cho mọi booking. | §4, §9.1 | `PAY/util/DepositAllocator.java:20-45`; `PaymentServiceImpl.java:195-220`; `BKS/BookingServiceImpl.java:306-311` (quote) | **C** | BE (Payment, Booking), FE |
| B7 | **Không có Center Booking Policy theo trung tâm.** Min advance theo loại, các timeout và check-in window đang là env toàn cục của BookingService. Cọc/hạn nằm ở `platform_policies` của Payment và không có API ghi (D15). Manager/Admin không chỉnh được. | §8, §12, §16 ("Tỷ lệ cọc và deadline lấy từ center booking policy") | `BKS/config/BookingProperties.java:200-248`; `application.yaml:129-155`; `PaymentService/schema/payment.sql:6-28`; FE `features/admin/policies/admin-policies-page.tsx` là **mock tĩnh**, không gọi API | **C** | BE, FE, DB |
| B8 | **Min advance sai.** Code dùng một mốc 12h cho mọi loại; spec là SHIFT 2h, RECURRING/CUSTOM 48h. Không kiểm "deadline xác nhận + phân công + thanh toán phải còn trước buổi đầu". | §8 | `OccurrenceGeneratorImpl.java:82-84`; `BookingProperties.java:218` | H | BE |
| B9 | **Hạn thanh toán đầu lấy từ env `BOOKING_DEPOSIT_TIMEOUT`,** không từ policy (`deposit_due_hours` của Payment bị bỏ qua, lệch contract C2.1). | §8, §16 | `BookingLifecycleServiceImpl.java:82`, `:188-196` | M | BE |
| B10 | **Thiếu job hết hạn phân công caregiver** (vì chưa có B1). | §5.2, §8, §15 | `BookingAction.java:34` (EXPIRE chỉ từ PENDING/AWAITING_DEPOSIT); `BookingServiceImpl.java:540`, `:1014-1020` | H | BE |
| B11 | **Trạng thái occurrence khác spec.** Thiếu PENDING_ASSIGNMENT, RESERVED, EXPIRED. `MISSED` ≙ CAREGIVER_MISSED. Buổi tạo ra luôn là SCHEDULED, kể cả khi booking còn chờ xác nhận hoặc chưa có caregiver. Buổi hết hạn/bị từ chối chuyển CANCELLED thay vì EXPIRED. | §11.2, §6 | `BKS/enums/OccurrenceStatus.java:9-16`; `BookingServiceImpl.java:575` (SCHEDULED khi tạo); `BookingLifecycleServiceImpl.java:84-101`; CHECK `booking.sql:139`; exclusion `:149-151` | H | BE, FE, DB |
| B12 | **Không có nguồn lịch SHIFT cho customer.** Provider không có API trả danh sách `caregiver_schedules` (kèm id) cho customer. `/caregivers/{id}/availability` trả khung giờ gộp, không có scheduleId, chưa trừ buổi đã đặt. FE wizard không gọi API lịch nào và không khóa schedule đã bị giữ. | §3, §5.1, §6 | `PRV` `CaregiverController.java:123`, `CaregiverServiceImpl.java:432-447`; FE `use-booking-providers.ts:13-21` (chỉ danh sách caregiver); `use-booking-form.ts` không có bước chọn schedule | **C** | BE (Provider, Booking), FE |
| B13 | **Mức hoàn tiền khác spec.** Spec: ≥24h → 100%, <24h → 70%, theo từng buổi. Payment: cooling-off 60', phí 0/10/30% theo mốc 72h/24h (`refund_tiers`). Admin policy mock FE hiển thị 100/70/50/0. | §10.2 | `PAY/util/RefundCalculator.java:47-117`; `payment.sql:213-219` (seed); FE `features/admin/policies/policies-preview.ts` | H | BE (Payment), FE |
| B14 | **Manager không xem được doanh thu trung tâm mình.** `/payments/revenue` chỉ cho ADMIN. | §9.3, §16 | `PAY/controller/PaymentController.java:90-95` | M | BE (Payment), FE |
| B15 | **FE hardcode cọc 30%.** Nếu policy trả khác 30% thì **chặn nút đặt lịch**. Trái nguyên tắc "policy linh hoạt" và đang ở đúng luồng booking. *Đây là ứng viên cho "luồng đang lỗi" nếu dev `.env`/policy ≠ 30.* | §8 | `FE/features/customer/booking/components/booking-summary-sidebar.tsx:20-27`; `hooks/use-booking-form.ts:263-264` | H | FE |
| B16 | **FE manager/admin hủy luôn hủy cả booking** (không gửi `occurrenceIds`). Trung tâm không hủy được một buổi; spec cho trung tâm hủy buổi, hoàn 100%. | §10.1, §10.2 | `FE/features/center-manager/booking-management/manager-booking-detail-page.tsx:63`, `:144-149`; `admin-booking-detail-page.tsx:32-33` | M | FE |
| B17 | **Provider `availability-checks` / `availability` không kiểm caregiver ACTIVE/VERIFIED và center ACTIVE.** Booking bù lại ở `resolveAssignable`; spec yêu cầu kiểm ở cả hai phía. | §6 | `PRV/service/Impl/CaregiverServiceImpl.java:486-490` (`findActiveCaregiver` chỉ lọc `deletedAt`) | L | BE (Provider) |
| B18 | **Phân caregiver không ghi vào `booking_status_history`** (ghi ở `booking_events`). Spec muốn history giải thích được "ai phân công". Hiện API `/history` gộp hai bảng nên vẫn đọc được. | §11 cuối | `BookingServiceImpl.java:603-618`, `:271-273` | L | BE |
| B19 | **Admin hủy booking** đã được làm (D23, M-11, hoàn 100%). Spec ghi "xử lý đặc biệt nếu bổ sung" — không sai, chỉ cần xác nhận giữ. | §12 | `BookingController.java:134-143`; `BookingServiceImpl.java:357-358,748-749` | L | — |
| B20 | **Thông báo** đã có (BOOKING_CREATED/ACCEPTED/…/EXPIRED). Khi có B1 cần thêm loại cho "chờ phân công" và "nhắc trả phần còn lại trước buổi đầu" (REMAINING_DUE đã có trong enum). | §5.2, §17 GĐ3 | `booking.sql:317`; `BKS/enums/NotificationType.java` | M | BE, DB |

### Các điểm đã đúng spec (không cần sửa)

- **Chống trùng / race.**
  - Advisory lock caregiver → người nhận trước khi kiểm (`BookingServiceImpl.java:562-566`, `:1047-1054`).
  - Exclusion constraint `ex_booking_occurrences_caregiver_slot` (`booking.sql:149-151`) trả 409 5215.
  - Idempotency-Key khi tạo (`:170-207`).
- **Phạm vi dữ liệu.** userId/centerId lấy từ token; ngoài phạm vi trả 404 (`:964-974`); `fail-on-unknown-properties` chặn field lạ.
- **Snapshot giá** vào `booking_details` và `booking_occurrences.price_amount`; Catalog lỗi thì không tạo booking.
- **Caregiver.** Kiểm thuộc trung tâm, VERIFIED, ACTIVE, đủ dịch vụ, rảnh (`CaregiverPortImpl.java:30-39,49-59`).
- **Hủy từng buổi có preview + token, hoàn tiền qua outbox.** PaymentService tính số tiền. IPN idempotent.
- **Status history** có `from/to/changed_by/reason/changed_at`.
- **Payment có `center_id`** trên mọi bảng tiền.

---

## 4. Kế hoạch sửa (đề xuất — chờ duyệt)

> Quy mô thật sự là **tái cấu trúc mô hình booking/thanh toán** chạm 4 phần: BookingService, PaymentService,
> ProviderService và FE, kèm migration DB. Tôi đề xuất chia đúng theo §17 của spec. Mỗi đợt build/test xanh rồi mới sang
> đợt sau. **Cách làm cụ thể phụ thuộc câu trả lời Q1–Q6**; dưới đây là phương án tôi khuyến nghị.

### Đợt 1 — BookingService lõi (spec GĐ1): B1, B2, B3, B8, B10, B11, B18

1. **Enum + state machine.**
   - `enums/BookingStatus`: thêm `PENDING_CAREGIVER_ASSIGNMENT`; đổi tên hoặc giữ tên theo Q1.
   - `enums/BookingAction`:
     - `CONFIRM` (PENDING → AWAITING_INITIAL_PAYMENT nếu đã có caregiver, ngược lại → PENDING_CAREGIVER_ASSIGNMENT). Vì nguyên tắc "mỗi action một đích", tách thành `ACCEPT` và `ACCEPT_WITHOUT_CAREGIVER`.
     - `ASSIGN_CAREGIVER` (PENDING_CAREGIVER_ASSIGNMENT → AWAITING_INITIAL_PAYMENT).
     - `EXPIRE` thêm nguồn PENDING_CAREGIVER_ASSIGNMENT.
     - `REJECT` thêm nguồn PENDING_CAREGIVER_ASSIGNMENT (§5.3 "trung tâm chủ động từ chối").
   - Cập nhật các tập `SCHEDULE_HOLDING`, `CAREGIVER_CHANGEABLE`…
   - `enums/OccurrenceStatus`: thêm `PENDING_ASSIGNMENT`, `RESERVED`, `EXPIRED`; đổi `MISSED` → `CAREGIVER_MISSED` (theo Q1); cập nhật `ACTIVE` và `canTransitionTo`.
2. **Loại booking (Q2).**
   - `enums/BookingType {SHIFT, RECURRING, CUSTOM}` + `RecurrenceFrequency {DAILY, WEEKLY}`.
   - `BookingCreateRequest` / `BookingQuoteRequest`: `bookingType`, `caregiverScheduleId` (SHIFT), `frequency`/`startDate`/`endDate`/`startTime`/`endTime`/`daysOfWeek` (RECURRING), `slots[{startAt,endAt}]` (CUSTOM). Validation có ErrorCode mới (51xx/52xx).
   - `OccurrenceGeneratorImpl`: sinh 3 kiểu. CUSTOM kiểm các buổi không chồng nhau và đủ thời lượng tối thiểu.
3. **SHIFT.** Cần client Provider mới để lấy schedule theo id, kiểm thuộc center + caregiver và status SCHEDULED.
   - Lưu `booking_occurrences.caregiver_schedule_id`.
   - Thêm unique partial index: một schedule chỉ có một buổi đang chiếm lịch.
4. **Center Booking Policy (Q4)** — khuyến nghị đặt ở BookingService:
   - Aggregate mới `CenterBookingPolicy` (entity / repository / service / impl / controller / mapper / DTO).
   - API `GET /api/v1/center-booking-policies/{centerId}`: CUSTOMER, CENTER_MANAGER, ADMIN.
   - API `PUT …/{centerId}`: manager chỉ trung tâm mình, admin mọi trung tâm.
   - Không có dòng thì dùng mặc định từ `BookingProperties`.
   - Validate tổ hợp hợp lý (§8).
   - Booking đọc policy khi tạo, xác nhận, phân công; lưu các deadline vào booking.
5. **Deadline.** Lưu `acceptance_due_at`, `assignment_due_at` (mới), `initial_payment_due_at` (đổi tên từ `deposit_due_at`, hoặc giữ tên theo Q1), `remaining_payment_due_at` (mới).
   - Lúc tạo, từ chối nếu "tạo + xác nhận + (phân công) + thanh toán" vượt mốc trả phần còn lại trước buổi đầu.
   - `BookingExpiryScheduler` quét thêm PENDING_CAREGIVER_ASSIGNMENT.
6. **Occurrence lúc tạo.** Có caregiver → `RESERVED`; chưa có → `PENDING_ASSIGNMENT`. Khi CONFIRMED (đã trả khoản đầu) → `SCHEDULED`.
   - Hết hạn hoặc từ chối → `EXPIRED` / `CANCELLED` đúng spec.
   - Exclusion constraint đổi điều kiện sang `RESERVED, SCHEDULED, ACKNOWLEDGED, IN_PROGRESS`.
7. **History.** Ghi `booking_status_history` cho hành động phân công (B18).
8. **Schema.**
   - Cập nhật `schema/booking.sql`.
   - Thêm `schema/changes/2026-11-xx_booking_spec_v1.sql`: thêm cột, bảng `center_booking_policies`, đổi CHECK, `UPDATE` map giá trị cũ → mới (Q1, Q6).
9. **Test.**
   - `BookingActionTests` (mọi cạnh mới).
   - `OccurrenceGeneratorTests` (SHIFT/RECURRING/CUSTOM, min advance theo loại).
   - `BookingApiTests`: xác nhận không caregiver → chờ phân công; phân công → chờ thanh toán; hết hạn phân công; SHIFT thiếu scheduleId → 400; schedule đã bị giữ → 409; manager trung tâm khác → 404; manager sửa policy trung tâm khác → 403/404.
   - `BookingScheduleConflictTests`.
10. **Tài liệu.** Cập nhật `BookingService/README.md`, `BookingService/AGENTS.md` (quy tắc 1, 3), `docs/PLAN.md`.

**File chạm (dự kiến).**
- Sửa:
  - `enums/BookingStatus`, `BookingAction`, `OccurrenceStatus`, `ScheduleType` (hoặc thay)
  - `dto/request/BookingCreateRequest`, `BookingQuoteRequest`, `BookingScheduleRequest`, `AcceptRequest`
  - `dto/response/BookingDetailResponse`, `BookingSummaryResponse`, `OccurrenceResponse`, `BookingQuoteResponse`
  - `entity/Booking`, `BookingOccurrence`
  - `mapper/BookingMapper`, `OccurrenceMapper`
  - `service/Impl/BookingServiceImpl`, `BookingLifecycleServiceImpl`, `OccurrenceGeneratorImpl`, `CaregiverPortImpl`, `BookingNotificationServiceImpl`
  - `repository/BookingRepository`, `BookingOccurrenceRepository`
  - `scheduler/BookingExpiryScheduler`, `controller/BookingController`, `exception/ErrorCode`, `config/BookingProperties`
  - `application.yaml`, `schema/booking.sql`, test
- Tạo: `entity/CenterBookingPolicy`, `repository/…`, `service/CenterBookingPolicyService(+Impl)`, `controller/CenterBookingPolicyController`, `mapper/CenterBookingPolicyMapper`, DTO request/response, `enums/BookingType`, client DTO schedule, change script.
- Các service khác đọc `BookingStatus` qua contract cũng phải cập nhật: CareSession, Feedback, Complaint, Statistics, `PaymentContextResponse`.

### Đợt 2 — ProviderService: B12, B17

1. **API cho customer xem schedule của một caregiver.** `GET /api/v1/caregivers/{id}/schedules?from&to`.
   - Chỉ caregiver VERIFIED + ACTIVE thuộc center ACTIVE.
   - Trả `scheduleId, workDate, startAt, endAt, status`.
2. **API token SERVICE** lấy một schedule theo id (hoặc cho phép trên endpoint (1)) để Booking xác minh SHIFT.
3. `availability` / `availability-checks` kiểm thêm trạng thái caregiver và center (B17).
4. Test + README Provider. Không đổi schema Provider.

### Đợt 3 — BookingService: trạng thái "đã giữ" cho FE (B12, phía Booking)

- API cho customer: `GET /api/v1/bookings/held-schedules?caregiverId&from&to`, trả các `caregiverScheduleId` / khoảng giờ đang bị chiếm (không lộ booking của người khác).
- FE dùng kết quả để khóa lựa chọn.

### Đợt 4 — PaymentService (spec GĐ2): B5, B6, B9, B13, B14

1. **Khoản đầu.** SHIFT = 100% total; RECURRING/CUSTOM = cọc % từ policy (nguồn % theo Q4). `DepositAllocator` dùng % theo loại.
   - Booking gửi `bookingType` + `initialPaymentDueAt` trong `payment-context`.
2. **Phần còn lại.**
   - Tạo hóa đơn REMAINING ngay khi khoản đầu SUCCESS (RECURRING/CUSTOM), `due_at = remaining_payment_due_at` từ Booking.
   - Trả xong → báo Booking (`/remaining-paid` hoặc mở rộng contract) → booking `fullyPaidAt`.
   - Quá hạn → **Q3** (hủy buổi chưa trả / hủy booking / chặn check-in).
   - Bỏ việc sinh hóa đơn REMAINING khi FINALIZE (`BookingLifecycleServiceImpl.java:104-110`). Xử lý no-show/thu thêm theo Q3.
3. **Check-in** (`CareSessionServiceImpl.java:248`): yêu cầu booking đã FULLY PAID.
4. **Hoàn tiền theo spec:** 100% (≥24h) / 70% (<24h) theo từng buổi; trung tâm/caregiver vắng 100% — theo **Q5** (sửa seed `refund_tiers` hay sửa `RefundCalculator`).
5. **Doanh thu:** mở `/payments/revenue` cho CENTER_MANAGER, khóa `centerId` từ token.
6. **Schema:** change script cho `payment.sql` (cột/loại thanh toán nếu cần, seed policy).
7. **Test:** `RefundCalculator`, `DepositAllocator`, contract test của BookingService (`PaymentContractTests`, JSON trong `src/test/resources/contracts/payment`).

### Đợt 5 — FE (sau khi BE xong và regen OpenAPI)

1. Cập nhật `openapi/openapi.yaml` từ `/v3/api-docs` mới; chạy `pnpm api:generate`.
2. **Wizard** `features/customer/booking/*`:
   - chọn loại SHIFT / RECURRING / CUSTOM
   - SHIFT: chọn caregiver → lịch schedule (khóa slot đã giữ)
   - CUSTOM: danh sách buổi trong **một** booking (bỏ fan-out B4)
   - **bỏ hardcode 30%** (B15)
   - hiển thị khoản đầu và hạn phần còn lại theo policy.
3. **Trạng thái và hành động.** `entities/booking/booking-format.ts`, `booking-status-badge.tsx`, i18n vi/en; next-step copy.
   - Manager: nút "Xác nhận" không bắt buộc caregiver; màn "Phân công" cho PENDING_CAREGIVER_ASSIGNMENT; hủy từng buổi (B16).
4. **Thanh toán.** Trang deposit đổi thành "thanh toán đầu" (100% cho SHIFT); nút trả phần còn lại ngay trên chi tiết booking trước buổi đầu.
5. **Policy UI.** Trang policy cho manager (trung tâm mình) và admin (thay mock `admin-policies-page.tsx`), có cảnh báo tổ hợp quá ngắn (§8).
6. **Doanh thu trung tâm** cho manager (nếu Q đồng ý B14).
7. **Kiểm tra.** Test Vitest cho hook form, ma trận nút theo trạng thái/role; `pnpm check` xanh.

### Đợt 6 — Tài liệu (Giai đoạn 5 của nhiệm vụ)

Cập nhật file này với danh sách thay đổi, luồng sau sửa, Mermaid state/sequence, API contract, edge case và hướng dẫn test
thủ công.

### Migration và rủi ro dữ liệu cũ

- **Cần migration** ở Booking (enum/CHECK, cột mới, bảng policy, exclusion constraint) và Payment (seed/cột).
- Booking đang dở dang:
  - Map `PENDING` → `PENDING_CENTER_CONFIRMATION` và `AWAITING_DEPOSIT` → `AWAITING_INITIAL_PAYMENT` (nếu đổi tên).
  - Buổi `SCHEDULED` của booking chưa trả tiền → `RESERVED`.
  - `MISSED` → `CAREGIVER_MISSED`.
  - Booking cũ ONE_TIME/DAILY/WEEKLY → `CUSTOM`/`RECURRING` (ONE_TIME có thể map CUSTOM 1 buổi vì không có `caregiverScheduleId`).
- Booking cũ đã CONFIRMED theo mô hình "trả sau" không có hóa đơn REMAINING trước buổi đầu. Cần quyết định (Q6) cho chúng giữ quy tắc cũ (hóa đơn khi FINALIZE) hay ép theo quy tắc mới.
- Đổi tên enum là **breaking change** với FE, mobile caregiver app và các service đọc status. AGENTS yêu cầu `/api/v2` cho đổi phá vỡ → Q1.

---

## 5. Câu hỏi mở (cần bạn quyết định trước khi code)

- **Q0. "Luồng booking đang lỗi" cụ thể là lỗi gì?** Thao tác nào, role nào, mã lỗi / màn hình nào? Test BE đang xanh;
  ứng viên tôi thấy là B15 (FE chặn submit khi policy ≠ 30%) và B1 (manager không accept được booking chưa có
  caregiver nếu không chọn người). Biết lỗi thật để sửa nó trước.
- **Q1. Tên trạng thái:**
  - (a) Đổi đúng tên spec (`PENDING_CENTER_CONFIRMATION`, `AWAITING_INITIAL_PAYMENT`, `CAREGIVER_MISSED`…) kèm migration, sửa FE và mobile app cùng lúc, vẫn giữ `/api/v1` vì đồ án chưa phát hành.
  - (b) Giữ tên cũ, chỉ thêm trạng thái còn thiếu, ghi bảng ánh xạ trong tài liệu.
  - Tôi **khuyến nghị (a)**, vì spec là "contract chung cho BE/FE/test". Nhưng nó vi phạm quy tắc `/api/v2` của AGENTS → cần bạn cho phép ngoại lệ.
- **Q2. Loại booking.** Thay `ONE_TIME/DAILY/WEEKLY` bằng `SHIFT/RECURRING(DAILY|WEEKLY)/CUSTOM`? CUSTOM có được nhiều khung giờ khác nhau mỗi buổi (spec: danh sách startAt/endAt — có)?
- **Q3. Phần còn lại.**
  - Quá hạn trả phần còn lại trước buổi đầu thì xử lý thế nào: EXPIRED/hủy booking (hoàn cọc? mất cọc?) hay chỉ chặn check-in?
  - Hóa đơn bổ sung sau khi hoàn tất (CUSTOMER_NO_SHOW…) còn không?
  - Booking nhiều buổi bị hủy một phần sau khi đã FULLY PAID thì hoàn theo 100/70 trên giá buổi — xác nhận?
- **Q4. Center Booking Policy lưu ở đâu?**
  - Khuyến nghị: bảng mới trong BookingService chứa mọi mục §8 trừ phần tiền.
  - Cọc % và hạn phần còn lại: (a) chuyển sang policy của Booking rồi truyền cho Payment qua `payment-context`; hoặc (b) giữ ở `platform_policies` của Payment và **mở API ghi theo center** (bỏ D15).
  - Spec §8 gộp tất cả vào một policy → tôi nghiêng về (a).
- **Q5. Mức hoàn tiền:** áp đúng spec 100% (≥24h) / 70% (<24h) và bỏ cooling-off + bậc 72h hiện có (D2, D17)? Hardcode hay cấu hình?
- **Q6. Dữ liệu cũ:** DB dev có thể reset (áp lại `booking.sql`, `payment.sql`) hay bắt buộc change script giữ dữ liệu?
- **Q7. Phạm vi lần này:** làm hết đợt 1–5 hay chỉ đợt 1 (+2, +3) cho spec GĐ1 rồi dừng duyệt tiếp?
- **Q8. Mobile app caregiver** (ngoài repo này?) có đọc `OccurrenceStatus` không — đổi tên `MISSED`/thêm `RESERVED` có ảnh hưởng?
- **Q9. Admin hủy booking** (D23) có giữ không (spec: "xử lý đặc biệt nếu bổ sung")?
- **Q10.** Spec §5.1 nói "chọn caregiver có khả năng cung cấp dịch vụ customer chọn" nhưng cũng nói chọn caregiver trước dịch vụ. FE nên lọc caregiver theo dịch vụ (dùng `/caregivers/search?careServiceIds`) hay lọc dịch vụ theo caregiver?

---

## 6. Góp ý / Đề xuất chờ xác nhận (ngoài phạm vi, CHƯA làm)

| # | Vị trí | Vấn đề | Đề xuất | Lợi ích / rủi ro |
|---|---|---|---|---|
| G1 | `BKS/config/BookingProperties.java:90` | `Notification.adminUserIds` dùng tên đầy đủ `java.util.List` / `java.util.UUID` trong thân code (trái AGENTS §3 "import vào") | Import tường minh | Đúng convention; không đổi hành vi |
| G2 | `BKS/service/Impl/BookingServiceImpl.java:3-111` | Import lộn xộn, không theo thứ tự (`RecipientActivityResponse`, `Map` ở đầu; `NotificationService` chen giữa `java.time`) | Sắp lại import khi chạm file | Dễ đọc; diff lớn hơn |
| G3 | `BookingServiceImpl.java:507-508` | `RecipientActivityResponse.builder()` dựng DTO trong service (trái rule #18 "MapStruct cho mọi chuyển đổi") | Chuyển sang mapper | Đúng convention |
| G4 | `BookingServiceImpl.java:1030-1031`, `:1054-1055`, `:1064-1065` | Thiếu dòng trống giữa method; `lockSchedule` / `checkCaregiverAvailable` / `checkRecipientAvailable` package-private, không có `private` | Format lại | Style |
| G5 | `BKS/enums/BookingAction.java:22` | Comment còn nhắc `/internal/bookings/{id}/deposit-paid` (đã bỏ) | Sửa comment | Tránh hiểu nhầm |
| G6 | `BKS/scheduler/BookingExpiryScheduler.java:17` | TODO "dùng SKIP LOCKED" nhưng `expire` đã dùng `findForUpdateSkipLocked` | Xóa TODO cũ | Tài liệu đúng |
| G7 | `PAY` `platform_policies.commission_percent` | Không service nào dùng; spec §9.3 nói không thu commission | Cân nhắc bỏ hoặc ghi rõ "không dùng" | Gọn schema; cần change script |
| G8 | `FE/features/customer/bookings/booking-detail-page.tsx:21` | Nút hủy hiện cả khi IN_PROGRESS; BE chỉ cho hủy buổi chưa bắt đầu (cả booking IN_PROGRESS → 409) | Chỉ hiện "hủy buổi còn lại" khi IN_PROGRESS | UX rõ hơn |
| G9 | FE `checkout {provider:'VNPAY'}` hardcode | Enum có STRIPE (D16: tắt) | Giữ nguyên; chỉ ghi chú | — |

---

## 7. Vòng 2 — quyết định của chủ dự án và kết quả điều tra bổ sung

### 7.1 Quyết định đã chốt

| Q | Quyết định |
|---|---|
| Q1 | **(a)** Đổi đúng tên trạng thái theo spec; giữ `/api/v1` (ngoại lệ có chủ đích với quy tắc `/api/v2` của AGENTS vì chưa phát hành) |
| Q2 | Thay bằng `SHIFT / RECURRING / CUSTOM`. CUSTOM: mỗi buổi có `startAt/endAt` riêng, validate không chồng giờ (trích spec ở §7.3) |
| Q3 | Xem §7.4 — đã ghi, **có điểm mâu thuẫn spec cần chốt** |
| Q4 | **(a)** Center Booking Policy ở BookingService chứa mọi mục §8, gồm cọc %, hạn phần còn lại; truyền sang Payment qua `payment-context` |
| Q5 | Hoàn 100% (≥ mốc) / 70% (< mốc), bỏ cooling-off và bậc 72h. Lưu vào center policy: mốc mặc định 24h, 100, 70 |
| Q6 | DB dev được reset; **vẫn viết change script đầy đủ** cho staging/production |
| Q7 | Làm đợt 1 (+2, +3) rồi dừng duyệt |
| Q8 | Xem §7.5 |
| Q9 | Giữ admin hủy (hoàn 100%), không mở rộng; ghi rõ **ngoài spec**, cần xác nhận với người viết spec |
| Q10 | FE chọn dịch vụ trước, lọc caregiver bằng `/caregivers/search?careServiceIds`; BE luôn validate kỹ năng. **Báo người viết spec: §5.1 tự mâu thuẫn về thứ tự chọn** |

### 7.2 Q0 — hai lỗi bạn gặp

#### Q0.1 Thanh toán lỗi khi đặt dịch vụ — ĐÃ TÌM RA NGUYÊN NHÂN (cấu hình, không phải code)

Bằng chứng từ log gateway đang chạy (đếm theo path):

```
2794  GET /api/v1/payments/me/outstanding -> 401
2793  GET /api/v1/remaining-invoices      -> 401
2397  GET /api/v1/payments                -> 401
   1  GET /api/v1/payment-policies/effective -> 401
  21  GET /api/v1/bookings -> 200      (cùng phiên đăng nhập)
   1  POST /api/v1/bookings -> 503
```

**Mọi API của PaymentService từ chối token người dùng (401).** Cùng token đó vẫn dùng được với BookingService.

Nguyên nhân là claim `iss` không khớp:

| Service | Giá trị |
|---|---|
| IdentityService (container) | `IDENTITY_JWT_ISSUER=http://localhost:8081` |
| BookingService | `BOOKING_JWT_ISSUER=http://localhost:8081` ✔ |
| PaymentService | `PAYMENT_JWT_ISSUER=careconnect-identity` ✘ |

Giá trị sai nằm ở `BE/PaymentService/.env:7`, `.env.example:8` và default trong `PaymentService/src/main/resources/application.yaml:31`.

Hậu quả dây chuyền:
- **Tạo booking** gọi Payment kiểm nợ (`BookingServiceImpl.java:181` → `PaymentPortImpl.java:36-44`), nhận 401, ném `PAYMENT_SERVICE_UNAVAILABLE`, kết quả là `POST /bookings → 503`.
- **Thanh toán cọc** (`checkout`), xem hóa đơn, xem policy đều 401.
- Token SERVICE do cùng Identity ký, nên Booking → Payment (refund, refund-quote, remaining-invoice) cũng sẽ 401.

**Đề xuất sửa:**
- Đặt `PAYMENT_JWT_ISSUER` bằng `IDENTITY_JWT_ISSUER` trong `.env` (file local, không commit).
- `.env.example`: để trống kèm comment "phải khớp IDENTITY_JWT_ISSUER", giống BookingService.
- Bỏ default `careconnect-identity` trong `application.yaml` để app **fail khi khởi động** nếu thiếu, giống Booking.
- Restart `payment-service`.

Đây là sửa **ngoài phạm vi booking**, nên chờ bạn đồng ý (§7.6 X1).

Ghi chú phụ: `PAYMENT_DEPOSIT_PERCENT=100` trong `.env` dev chỉ sinh cảnh báo khi khởi động; giá trị thật lấy từ DB (30).

#### Q0.2 Trung tâm ARCHIVED "biến mất"

Cơ chế hiện tại:
- **Nút "Lưu trữ"** ở trang chi tiết trung tâm của admin (`FE/features/admin/centers/center-detail-page.tsx:95-105`, `:171-176`) gọi `DELETE /api/v1/care-centers/{id}`.
- **BE** (`ProviderService/.../CareCenterServiceImpl.java:208-215`, `:272-275`):
  - chuyển `ARCHIVED` **và đặt `deleted_at`**, tức là xóa mềm;
  - `ARCHIVED` là trạng thái cuối, không có chuyển đi đâu (`:261-268`);
  - mọi query mặc định lọc `deleted_at is null`.
- **FE** (`admin-centers-page.tsx:25`, `:91-92`, `:244-302`, `:317-319`):
  - chỉ hiện trung tâm đã lưu trữ khi chọn bộ lọc trạng thái **ARCHIVED** (gửi `includeDeleted=true`);
  - dòng đó chỉ đọc, không mở được chi tiết;
  - **không có nút khôi phục**;
  - thống kê (`CareCenterRepository.java:70`) cũng bỏ qua trung tâm đã xóa mềm.
- DB dev hiện có 1 trung tâm như vậy: **"TT An Tâm"**, archived lúc 2026-10-29 04:42 UTC.

**Cách xem lại ngay (không cần sửa code):** trang Admin → Trung tâm → bộ lọc trạng thái = "Đã lưu trữ".

Ba hướng xử lý (chờ bạn chọn, §7.6 X2):

| Phương án | Nội dung | Ghi chú |
|---|---|---|
| (a) | **Khôi phục.** Thêm chuyển `ARCHIVED → INACTIVE`, xóa `deleted_at`, chỉ ADMIN, bắt buộc lý do, ghi audit. FE có nút "Khôi phục" ở dòng archived. Kích hoạt lại theo luồng INACTIVE → ACTIVE như cũ | Tôi khuyến nghị |
| (b) | Chỉ làm rõ UI: hộp xác nhận lưu trữ ghi rõ "ẩn khỏi danh sách, không khôi phục được", bắt gõ tên trung tâm để xác nhận; tab "Tất cả" hiện cả archived (mờ) | Không thêm khả năng khôi phục |
| (c) | (a) + (b) | |

Phát hiện liên quan Q1: `CareCenterServiceImpl.java:288` hardcode chuỗi trạng thái booking `"PENDING", "AWAITING_DEPOSIT", "CONFIRMED", "IN_PROGRESS"` để chặn lưu trữ / ngưng trung tâm còn booking. **Đổi tên trạng thái (Q1a) bắt buộc sửa dòng này**, nếu không thì kiểm tra "còn booking" sẽ luôn sai.

### 7.3 Q2 — trích nguyên văn spec về CUSTOM

Bảng §4 "Ba loại booking", cột CUSTOM:
> - Mục đích: "Tự chọn nhiều thời điểm không theo quy luật cố định."
> - Caregiver: "Customer có thể chọn hoặc để trung tâm phân công."
> - Lịch đầu vào: "**Danh sách startAt và endAt của từng buổi.**"
> - Số occurrence: "Nhiều occurrence theo danh sách customer nhập."
> - Thanh toán đầu: "Đặt cọc theo tỷ lệ policy, mặc định 30 phần trăm."

§6 "Quy tắc kiểm tra lịch và chống trùng":
> "Khoảng thời gian phải có endAt sau startAt và đạt thời lượng tối thiểu cấu hình."
> "**Các occurrence trong cùng booking không được chồng lấn nhau.**"

Kết luận: spec xác nhận mỗi buổi CUSTOM có khung giờ riêng. Validate không chồng giờ là yêu cầu của spec (áp cho mọi loại); dùng khoảng nửa mở `[startAt, endAt)` như code hiện có. Validate thêm: số buổi tối đa (`max-occurrences`) và min advance tính trên buổi sớm nhất.

### 7.4 Q3 — đã ghi nhận; các điểm MÂU THUẪN / CHƯA RÕ với spec (không tự quyết)

Đã ghi nhận:
- Hạn trả phần còn lại nằm trong center policy (mặc định 24h trước buổi đầu); nhắc trước 72h và 48h.
- Quá hạn thì hệ thống tự kết thúc booking, lý do `PAYMENT_OVERDUE`, giải phóng lịch, báo caregiver + center.
- Không làm hóa đơn bổ sung sau hoàn tất. `CUSTOMER_NO_SHOW` coi như đã dùng, không hoàn.
- Hoàn tiền tính riêng từng buổi theo giá thực trả của buổi.

| # | Điểm | Spec nói | Quyết định Q3 | Cần bạn chốt |
|---|---|---|---|---|
| M1 | Trạng thái booking khi quá hạn trả phần còn lại | §11.1 EXPIRED: "Quá hạn xác nhận, phân caregiver **hoặc thanh toán**." §15: "Customer không thanh toán đúng hạn → **Booking EXPIRED**, giải phóng occurrence và lịch caregiver." | "Tự **huỷ** booking, lý do PAYMENT_OVERDUE" | `EXPIRED` (đúng spec, lý do PAYMENT_OVERDUE ghi vào history) hay `CANCELLED`? Tôi khuyến nghị **EXPIRED** |
| M2 | Trạng thái buổi lúc quá hạn | §11.2 RESERVED = "đang giữ lịch trong luồng xác nhận **hoặc thanh toán**"; SCHEDULED = "lên lịch chính thức". §11.1 CONFIRMED = "khoản thanh toán **cần thiết** đã hợp lệ và lịch được giữ chính thức" | "giải phóng lịch caregiver (RESERVED)" | (i) Buổi giữ `RESERVED` cho tới khi **FULLY PAID**, rồi mới `SCHEDULED`? hay (ii) `SCHEDULED` ngay khi trả khoản đầu (booking CONFIRMED)? Spec nghiêng về (ii); câu của bạn nghiêng về (i). Lưu ý: với (i), booking CONFIRMED nhưng buổi vẫn RESERVED; caregiver chỉ thấy buổi khi đã SCHEDULED? |
| M3 | Mất cọc khi quá hạn | §9.2: cọc để "bù một phần chi phí cơ hội khi trung tâm đã giữ lịch". §10.2: "<24h: 70%… 30% được giữ lại như phần cọc bị mất" | "Tiền cọc xử lý như khách tự huỷ **tại thời điểm hết hạn** (bậc 100/70)" | **Hệ quả số học:** hạn = 24h trước buổi đầu, nên lúc hết hạn **mọi buổi còn ≥ 24h**, nghĩa là hoàn 100% cọc. Khách không trả phần còn lại **không mất gì**, trái mục đích §9.2. Chọn: (i) giữ như vậy; (ii) quá hạn thanh toán áp mức "< mốc" (70%) cho mọi buổi; (iii) mất toàn bộ cọc; (iv) khác |
| M4 | Cơ sở tính 70% | §10.2: "Tổng giá trị được hoàn dựa trên **phần tiền phân bổ cho occurrence** bị hủy"; bảng ghi "70 phần trăm **giá trị occurrence**" | "giá thực trả từng buổi" | Hai cách đọc cho ra số khác nhau khi mới trả cọc. Ví dụ buổi 1.000.000đ, đã cọc 300.000đ: (i) 70% × **tiền đã trả** cho buổi = 210.000đ; (ii) 70% × giá trị buổi = 700.000đ, chặn trần tiền đã trả = 300.000đ (hoàn đủ). Tôi khuyến nghị (i), khớp "phần tiền phân bổ" và "giá thực trả" |
| M5 | Hủy hết các buổi còn lại khi đã có buổi diễn ra | §10.1 "hủy toàn bộ → occurrence chưa bắt đầu CANCELLED" (booking CANCELLED). §11.1 COMPLETED = "**Toàn bộ** occurrence đã hoàn tất". **Spec không nói** trường hợp một phần đã làm, phần còn lại bị hủy | Hỏi lại nếu spec không rõ | Code hiện tại: có ≥ 1 buổi COMPLETED/NO_SHOW thì booking `COMPLETED`, không có thì `CANCELLED` (`BookingLifecycleServiceImpl.finalizeIfDone`). Giữ quy tắc này? |
| M6 | Nhắc 72h / 48h trước hạn | Spec không nói về nhắc | Nhắc 72h, 48h | Booking tạo sát giờ (vd RECURRING min advance 48h) có mốc nhắc đã qua → bỏ qua mốc đó. Mốc nhắc là hằng hay đưa vào policy? Tôi đề xuất **đưa vào policy** (`remainingReminderOffsets`, mặc định 72h, 48h) |
| M7 | Phạm vi đợt | Q3 và Q5 chủ yếu là PaymentService (đợt 4) | Q7: chỉ đợt 1–3 | Đợt 1 sẽ **lưu đủ các trường policy** (cọc %, hạn, mốc/tỷ lệ hoàn, mốc nhắc) và tính `remaining_payment_due_at`. **Hành vi** thu trước buổi đầu, quá hạn và hoàn 100/70 làm ở đợt 4. Đồng ý? |

Ghi vào đề xuất (G10): phí phát sinh (làm thêm giờ…) và "tính công caregiver" cho buổi CUSTOMER_NO_SHOW là ngoài phạm vi; hệ thống chưa có module công caregiver.

### 7.5 Q8 — careconnect-mobile

**Rule và cấu trúc** (`careconnect-mobile/AGENTS.md`, `ARCHITECTURE.md`):
- **Nền tảng:** React Native CLI 0.88, TypeScript strict, chỉ Android, chỉ role CAREGIVER.
- **Cấu trúc:** `src/core` (api, json parser, l10n, ui) và `src/features/<feature>/{models,repository,screens}`. Core không import feature.
- **API:** chỉ gọi gateway `/api/v1`. Parse JSON ở biên. Text qua `useApp().t(key)`, thêm vi + en cùng lúc. Tôn trọng trạng thái và `version` của BE.
- **Kiểm tra:** `npm run check` (prettier, eslint, tsc, jest) và `npm run build:android`.
- **Không tự commit.** Repo có `.git` riêng (3 commit).

**Phát hành:** câu Q8 của bạn còn để nguyên placeholder "[chưa phát hành / đã phát hành]". Bằng chứng trong repo cho thấy **chưa phát hành**: ARCHITECTURE "Production signing/release is not configured", chỉ có debug APK. Tôi đang lập kế hoạch theo nhánh **chưa phát hành**: áp dụng Q1(a), sửa mobile trong đợt 1, commit riêng. **Cần bạn xác nhận** (§7.6 X3).

**Chỗ liên quan phía caregiver:**

| Hạng mục | File:dòng | Nội dung |
|---|---|---|
| OccurrenceStatus (danh sách lọc) | `src/features/visits/models.ts:2-10` | `SCHEDULED, ACKNOWLEDGED, IN_PROGRESS, COMPLETED, CANCELLED, MISSED, CUSTOMER_NO_SHOW`, dùng làm option bộ lọc |
| Bộ lọc gửi lên BE | `src/features/visits/screens.tsx:30`, `:64`, `:126`; `repository.ts:7-18` | `GET /booking-occurrences?status=<giá trị>` |
| Nhãn trạng thái | `src/core/l10n.ts:343-349` | key = tên trạng thái (`MISSED: 'Bỏ lỡ'`…) |
| Màu/icon trạng thái | `src/core/ui.tsx:229-235` | `['CANCELLED','MISSED','CUSTOMER_NO_SHOW','DECLINED']` tô đỏ; `t(status)` |
| Parse visit | `src/features/visits/models.ts` `parseVisit` | `status: required(j.status)` là chuỗi tự do, không validate enum |
| Nhận ca (acknowledge) | `src/features/visits/screens.tsx:279`; `repository.ts:30-31` | Nút hiện khi `SCHEDULED`; `POST /booking-occurrences/{id}/acknowledge` |
| Check-in / báo vắng | `src/features/visits/screens.tsx:294`; `sessions/repository.ts:8` | Hiện khi `SCHEDULED`/`ACKNOWLEDGED`; `POST /care-sessions` |
| Check-out, task, note | `src/features/sessions/repository.ts:12-39`; `screen.tsx:67` | `CareSession.status` (`IN_PROGRESS`/`ENDED`), không đổi |
| Hôm nay | `src/features/visits/repository.ts:19-25` | đếm `status=COMPLETED` |
| Lịch làm việc (Provider) | `src/features/profile/repository.ts:50-70`; `schedule.ts:44` | `caregiver_schedules` `SCHEDULED/CANCELLED`, không thuộc Booking, không đổi |
| Work shift / đăng ký | `src/features/work-shifts/*` | Provider; `l10n.ts:14-15` `workShiftType_DAILY/WEEKLY` là loại ca của Provider, **không** liên quan ScheduleType booking |
| Thông báo | `src/features/notifications/*` | đọc `type`, điều hướng theo reference |
| Từ chối booking | — | **Không có** (caregiver không từ chối booking; spec cũng không yêu cầu) |
| Trạng thái booking | — | Mobile **không đọc** `BookingStatus` |

**Ảnh hưởng khi đổi theo Q1(a):**
1. **`MISSED → CAREGIVER_MISSED`:**
   - Bộ lọc "Bỏ lỡ" gửi `status=MISSED`, BE trả **400** (enum không còn).
   - Buổi bị bỏ lỡ hiện nhãn thô `CAREGIVER_MISSED` (thiếu key l10n) và mất màu đỏ (`ui.tsx:229`).
   - Sửa: `models.ts`, `l10n.ts` (vi + en), `ui.tsx`.
2. **Thêm `EXPIRED` cho buổi:** cần nhãn và màu. Caregiver thường không thấy, vì BE chỉ cho caregiver xem buổi của booking đã xác nhận; booking quá hạn thanh toán (M1) thì có thể thấy.
3. **Thêm `RESERVED`, `PENDING_ASSIGNMENT`:** BE không trả cho caregiver (chỉ booking CONFIRMED trở đi), nên mobile không cần lọc. Vẫn thêm nhãn phòng hờ để không hiện key thô.
4. **Đổi tên trạng thái booking:** không ảnh hưởng mobile.
5. **SHIFT gắn `caregiverScheduleId`:** màn lịch làm việc có thể hiện "slot đã có booking". BE Provider đã chặn hủy slot có booking (409/4415) nên không bắt buộc; ghi đề xuất G11.

### 7.6 Kế hoạch đợt 1–3 (điều chỉnh) và các mục còn chờ xác nhận

**Thay đổi kế hoạch so với §4 (cần bạn duyệt vì phải đụng thêm phần ngoài BookingService ngay đợt 1):**

Đổi tên trạng thái (Q1a) là breaking change. Nếu chỉ sửa BookingService thì sau đợt 1 hệ thống **gãy ngay**:
- **PaymentService** parse `status` của `payment-context` vào enum riêng (`PaymentService/.../enums/BookingStatus.java:5-6`), dùng ở `PaymentServiceImpl.java:204` (`AWAITING_DEPOSIT` thì cho checkout) và `RefundServiceImpl.java:74`. `fail-on-unknown` sẽ làm checkout và hoàn tiền lỗi.
- **ProviderService** `CareCenterServiceImpl.java:288` hardcode chuỗi trạng thái booking.
- **FE** generated enum, nhãn, ma trận nút theo trạng thái; **mobile** như §7.5.

→ Đề xuất **đợt 1 gồm thêm "lớp tương thích tối thiểu"** ở 4 chỗ trên: chỉ đổi tên enum, nhãn, điều kiện trạng thái. Không làm tính năng mới của Payment/FE/mobile. Mỗi repo commit riêng (BE, FE, mobile) nhưng cùng đợt.

**Đợt 1 — BookingService + tương thích**
1. **Booking — trạng thái.**
   - `PENDING_CENTER_CONFIRMATION`, `PENDING_CAREGIVER_ASSIGNMENT`, `AWAITING_INITIAL_PAYMENT`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`, `CANCELLED`, `EXPIRED`.
   - Buổi: `PENDING_ASSIGNMENT`, `RESERVED`, `SCHEDULED`, `ACKNOWLEDGED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `EXPIRED`, `CUSTOMER_NO_SHOW`, `CAREGIVER_MISSED`.
   - `BookingAction` thêm các cạnh xác nhận không caregiver, phân công, hết hạn phân công. Tách `ACCEPT` theo nguyên tắc "một action một đích".
2. **Booking — loại booking** `SHIFT/RECURRING/CUSTOM`: request mới; generator 3 kiểu; CUSTOM validate không chồng giờ; SHIFT bắt buộc `caregiverId` + `caregiverScheduleId`.
3. **Booking — `CenterBookingPolicy`.**
   - Aggregate mới: entity, repo, service, impl, controller, mapper, DTO.
   - `GET/PUT /api/v1/center-booking-policies/{centerId}`.
   - Trường:
     - min advance theo loại
     - các timeout xác nhận / phân công / thanh toán đầu
     - hạn phần còn lại
     - cọc %
     - check-in sớm / trễ
     - mốc hoàn + % hoàn trước/sau mốc
     - mốc nhắc
   - Validate tổ hợp. Mặc định từ `BookingProperties`.
4. **Booking — deadline + scheduler hết hạn phân công.** Lưu `remaining_payment_due_at` (dùng ở đợt 4).
5. **Booking — `payment-context`** thêm `bookingType`, `initialPaymentPercent`, `remainingPaymentDueAt`. Payment chưa dùng các trường này, nên vẫn tương thích ngược.
6. **Schema:** `booking.sql` + `schema/changes/2026-11-xx_booking_spec_v1.sql` (cột, bảng, CHECK, map dữ liệu cũ, exclusion constraint theo trạng thái chiếm lịch mới).
7. **Tương thích:**
   - Payment `enums/BookingStatus` + các chỗ dùng.
   - Provider `CareCenterServiceImpl.java:288`.
   - FE: cập nhật `openapi.yaml`, `pnpm api:generate`, nhãn, ma trận nút; manager được "xác nhận không caregiver" và "phân công" cho trạng thái mới; **bỏ hardcode cọc 30%** (B15, vì đang chặn nút đặt lịch).
   - Mobile: §7.5 mục 1–3.
8. **Test:**
   - BookingService: state machine, generator, API, conflict.
   - Payment + Provider: test liên quan.
   - FE: `pnpm check`. Mobile: `npm run check`.

**Đợt 2 — ProviderService**
- API customer xem schedule của caregiver (`scheduleId`, giờ, trạng thái).
- API token SERVICE lấy schedule theo id.
- Availability kiểm thêm trạng thái caregiver và center (B17).

**Đợt 3 — Booking API "schedule đã bị giữ" + FE wizard**
- `GET /api/v1/bookings/held-schedules`.
- Wizard SHIFT/RECURRING/CUSTOM: một booking nhiều buổi, khóa slot đã giữ, chọn dịch vụ trước rồi lọc caregiver (Q10).

**Chờ bạn xác nhận trước khi bắt đầu đợt 1:**
- **X1.** Sửa cấu hình issuer của PaymentService (Q0.1) ngay bây giờ: `.env`, `.env.example`, bỏ default trong `application.yaml`, restart container?
- **X2.** Trung tâm ARCHIVED (Q0.2): chọn (a) khôi phục / (b) làm rõ UI / (c) cả hai? Làm trong đợt nào? Đây là ngoài phạm vi booking.
- **X3.** Mobile **chưa phát hành**, đúng không?
- **X4.** Chốt M1–M7 ở §7.4.
- **X5.** Đồng ý mở rộng đợt 1 thêm "lớp tương thích tối thiểu" cho Payment / Provider / FE / mobile như trên?

### 7.7 Đề xuất mới (chưa làm)

| # | Vị trí | Vấn đề | Đề xuất | Lợi ích / rủi ro |
|---|---|---|---|---|
| G10 | Payment / chưa có module | Phí phát sinh (làm thêm giờ); tính công caregiver cho buổi CUSTOMER_NO_SHOW | Thiết kế riêng sau, ngoài phạm vi spec hiện tại | Đủ nghiệp vụ; cần spec bổ sung |
| G11 | mobile `src/features/profile/schedule.ts:44` | Lịch làm việc không cho caregiver biết slot đã có booking SHIFT | Hiển thị cờ "đã có lịch đặt" (cần Booking/Provider trả thông tin) | UX caregiver; thêm API |
| G12 | `PaymentService/src/main/resources/application.yaml:31-32` | Default issuer và JWKS `localhost` che giấu lỗi cấu hình (nguyên nhân Q0.1) | Bỏ default; fail khi khởi động nếu thiếu (như Booking) | Lỗi lộ ngay khi deploy |
| G13 | `ProviderService/.../CareCenterServiceImpl.java:288` | Hardcode chuỗi trạng thái booking của service khác | Hỏi Booking một endpoint SERVICE "count active bookings by center" (đã có `SCHEDULE_HOLDING` ở Booking) | Không gãy khi Booking đổi trạng thái |


### 7.8 Vòng 3 — quyết định X1–X5 (chốt 2026-11-02)

| Mục | Quyết định |
|---|---|
| M1 | Quá hạn trả phần còn lại → booking `EXPIRED`, lý do `PAYMENT_OVERDUE` (đúng spec §11.1, §15) |
| M2 | Buổi chuyển `SCHEDULED` ngay khi trả khoản đầu (booking `CONFIRMED`). Không giữ `RESERVED` tới khi trả đủ |
| M3 | Khách chủ động hủy → hoàn theo bậc 100/70. Quá hạn thanh toán (EXPIRED) → khách mất cọc theo trường policy mới **`overduePaymentForfeitPercent`** (mặc định 100%). **Câu hỏi cho người viết spec:** xác nhận mất cọc khi quá hạn thanh toán (spec chưa nêu) |
| M4 | % hoàn tính trên **tiền đã trả** phân bổ cho buổi |
| M5 | Có ≥ 1 buổi đã diễn ra (COMPLETED / CUSTOMER_NO_SHOW) → booking `COMPLETED`; buổi bị hủy ghi ở cấp buổi. Không có → `CANCELLED` (giữ quy tắc hiện có) |
| M6 | Mốc nhắc trả phần còn lại nằm trong center policy (mặc định 72h, 48h trước hạn) |
| M7 | Đợt 1 chỉ lưu trường policy và deadline; thu tiền / quá hạn / hoàn tiền làm ở đợt 4 |
| X1 | Sửa issuer PaymentService trước đợt 1: chỉ `.env.example` + `application.yaml` (bỏ default). **Không sửa / commit `.env`**; chủ dự án tự sửa `.env` và restart container |
| X2 | Trung tâm ARCHIVED: backlog, ngoài phạm vi. Chỉ báo nếu chặn test booking |
| X3 | Mobile **chưa phát hành** → Q1(a), sửa mobile trong đợt 1 |
| X5 | Đồng ý lớp tương thích tối thiểu Payment / Provider / FE / mobile: chỉ đổi enum, nhãn, điều kiện trạng thái; không thêm tính năng |
| Git | **Không commit, không push** sau mỗi đợt. Tôi chỉ đề xuất commit message theo từng repo |

**Câu hỏi gửi người viết spec (tổng hợp):**
1. §5.1 thứ tự chọn caregiver ↔ dịch vụ tự mâu thuẫn (Q10).
2. Admin hủy booking (hoàn 100%) — đang là phần ngoài spec (Q9).
3. Quá hạn thanh toán phần còn lại → khách mất cọc (M3).


---

## 8. Kết quả X1 + đợt 1–3 (2026-11-02) — CHỜ DUYỆT, chưa commit / push

> Chưa commit, chưa push (theo yêu cầu). Commit message đề xuất cho từng repo ở §8.6.

### 8.1 Danh sách thay đổi

| # | Repo / file | Thay đổi | Lý do (vấn đề) |
|---|---|---|---|
| X1 | `BE/PaymentService/.env.example`, `src/main/resources/application.yaml` | `PAYMENT_JWT_ISSUER` để trống kèm comment; bỏ default `careconnect-identity` → thiếu cấu hình thì app dừng khi khởi động | Q0.1 (mọi API Payment trả 401) |
| 1 | `BookingService/enums/BookingStatus`, `OccurrenceStatus`, `BookingAction` | Trạng thái theo spec §11; thêm `ACCEPT_WITHOUT_CAREGIVER`, `ASSIGN_CAREGIVER`; `PAY_DEPOSIT` → `PAY_INITIAL_PAYMENT`; EXPIRE / REJECT nhận thêm `PENDING_CAREGIVER_ASSIGNMENT`; tập `CAREGIVER_HOLDING`, `WORKABLE` | B1, B2, B10, B11 |
| 2 | `enums/BookingType`, `RecurrenceFrequency` (mới), xóa `ScheduleType`; `dto/request/BookingCreateRequest`, `BookingQuoteRequest`, `BookingScheduleRequest`; `service/OccurrenceGenerator(+Impl)` | SHIFT / RECURRING / CUSTOM; `checkShape` (field phải khớp loại); CUSTOM validate từng buổi + không chồng giờ; SHIFT sinh một buổi từ lịch Provider | B3, B4 (phía BE), Q2 |
| 3 | `entity/CenterBookingPolicy`, `repository/…`, `service/CenterBookingPolicyService(+Impl)`, `controller/CenterBookingPolicyController`, `mapper/CenterBookingPolicyMapper`, DTO request / response, `config/BookingProperties.PolicyDefaults`, `application.yaml`, `.env.example` | Aggregate policy theo trung tâm (đủ trường Q4 / Q5 / M3 / M6); GET (khách / manager trung tâm mình / admin), PUT (manager / admin) có `version`; kiểm tổ hợp; mặc định từ env. Bỏ `minLeadTime`, `acceptanceTimeout`, `depositTimeout` | B7, B8, B9 |
| 4 | `service/Impl/BookingServiceImpl`, `BookingLifecycleServiceImpl`, `BookingNotificationServiceImpl` | Đặt trước tối thiểu theo loại; mốc chốt thanh toán (SHIFT: giờ buổi đầu, RECURRING / CUSTOM: `remainingPaymentDueAt`); hạn xác nhận / phân công / thanh toán đầu theo policy; xác nhận không caregiver; phân công chuyển `AWAITING_INITIAL_PAYMENT`; trả khoản đầu → buổi `SCHEDULED`; hết hạn → buổi `EXPIRED`; `initialPaymentPercent` (SHIFT 100) | B1, B6 (lưu tỷ lệ), B8, B9, B10, B11, M2 |
| 5 | `entity/Booking`, `BookingOccurrence`, mapper, `dto/response/*` | Cột / field `booking_type`, `recurrence_frequency`, `initial_payment_percent`, `assignment_due_at`, `remaining_payment_due_at`, `caregiver_schedule_id` (thay `availability_slot_id`); `payment-context` thêm `bookingType`, `initialPaymentPercent`, `remainingPaymentDueAt` | B3, B5 (chuẩn bị cho đợt 4) |
| 6 | `client/ProviderServiceClient`, `client/dto/ProviderScheduleResponse`, `service/CaregiverPort(+Impl)` | Tra lịch SHIFT (`GET /caregivers/schedules/{id}`, token SERVICE), kiểm trung tâm / caregiver / SCHEDULED | B12 |
| 7 | `repository/BookingOccurrenceRepository`, `GlobalExceptionHandler`, `ErrorCode` | Một lịch chỉ một buổi giữ (kiểm ở service + unique index → 409 `5411`); mã lỗi mới `5200` (đổi tên), `5237–5241`, `5409–5411`, `5413–5414` | B12, spec §6, §15 |
| 8 | `controller/BookingController`, `BookingService`, `BookingMapper`, `dto/response/CaregiverHeldScheduleResponse` | **Đợt 3:** `GET /api/v1/bookings/held-schedules` | B12 |
| 9 | `schema/booking.sql`, `schema/changes/2026-11-02_booking_spec_v1.sql` | DDL mới + change script đầy đủ (map dữ liệu cũ) | Q6 |
| 10 | `PaymentService/enums/BookingStatus`, `OccurrenceStatus`, `PaymentServiceImpl`, `RefundServiceImpl`, `RemainingInvoiceServiceImpl` + test | **Tương thích:** tên trạng thái mới; `NOT_STARTED` gồm `RESERVED` (lúc thanh toán buổi đang RESERVED); `CAREGIVER_MISSED` → final `MISSED` nội bộ Payment | X5 |
| 11 | `ProviderService/CareCenterServiceImpl` | **Tương thích:** chuỗi trạng thái booking khi kiểm "trung tâm còn booking" | X5 |
| 12 | `ProviderService/CaregiverController`, `CaregiverScheduleService(+Impl)`, `CaregiverService(+Impl)`, `CaregiverScheduleMapper`, `dto/response/BookableScheduleResponse` + test | **Đợt 2:** `GET /caregivers/{id}/schedules` (khách), `GET /caregivers/schedules/{id}` (SERVICE / ADMIN); availability / availability-check trả 404 khi caregiver hoặc trung tâm không còn nhận booking | B12, B17 |
| 13 | `ApiGateway/config/GatewayPaths` | Route `/api/v1/center-booking-policies/**` về BookingService. **Ngoài danh sách file dự kiến** — bắt buộc, không có thì FE không gọi được API policy | B7 |
| 14 | FE `openapi/openapi.yaml` + `generated/` (sinh lại) | Enum, request / record mới, 4 endpoint mới | Q1, Q2 |
| 15 | FE `entities/booking/*`, `shared/i18n/*`, trang manager / customer / admin, `features/customer/payment/*` | **Tương thích:** nhãn trạng thái, ma trận nút. Manager xác nhận không caregiver + "Phân người chăm sóc" cho `PENDING_CAREGIVER_ASSIGNMENT`; hạn phân công / thanh toán / phần còn lại; nhãn loại booking | X5, B1, B2 |
| 16 | FE `features/customer/booking/*` (hook, 3 component mới, sidebar, page) + test | **Đợt 3:** wizard SHIFT / RECURRING / CUSTOM, một booking cho mọi loại; chọn dịch vụ trước rồi lọc caregiver (Q10); chọn ca + khóa ca đã giữ; CUSTOM không chồng giờ; **bỏ hardcode cọc 30%** | B4, B12, B15, Q10 |
| 17 | mobile `src/features/visits/models.ts`, `src/core/l10n.ts`, `src/core/ui.tsx` | **Tương thích:** `MISSED` → `CAREGIVER_MISSED`; nhãn `EXPIRED`, `RESERVED`, `PENDING_ASSIGNMENT` | X3, X5 |
| 18 | Tài liệu: `BookingService/README.md`, `AGENTS.md`; `ProviderService/README.md`; FE `ARCHITECTURE.md`; mobile `ARCHITECTURE.md` | Cập nhật theo contract mới (rule #31) | — |

### 8.2 Kiểm thử đã chạy

| Phần | Lệnh | Kết quả |
|---|---|---|
| BookingService | `mvnw test` (H2) | **113 test, 0 lỗi, 2 skip** (2 test chỉ chạy với PostgreSQL). Test mới: state machine đầy đủ, generator 3 loại, xác nhận không caregiver → phân công → thanh toán, hết hạn phân công + từ chối, SHIFT (giữ lịch một lần, thiếu field, sai caregiver, lịch CANCELLED, held-schedules), CUSTOM (giờ riêng, chồng giờ, min advance 48h), policy (phạm vi, version, tổ hợp sai, policy quyết định % cọc) |
| BookingService | `mvnw test -Dbooking.testing.postgres=true` (container PostgreSQL 17 tạm, cổng 55496, đã dừng) | **112 / 113.** Lỗi duy nhất `forgottenCareSessionIsAutoClosedFourHoursAfterTheVisitEnds` **có sẵn từ trước**: test tự đặt `endAt` trước `startAt`, vi phạm CHECK `ck_booking_occurrences_range` vốn có. Xem G14 |
| Schema | Container PostgreSQL tạm: áp `booking.sql` mới; áp `booking.sql` cũ (HEAD) + dữ liệu mẫu + change script | Cả hai chạy được; schema sau migration **trùng khớp** schema tạo mới; dữ liệu cũ map đúng (ONE_TIME → CUSTOM, PENDING → PENDING_CENTER_CONFIRMATION, SCHEDULED chưa trả → RESERVED / PENDING_ASSIGNMENT, MISSED → CAREGIVER_MISSED) |
| PaymentService | `mvnw test` | **43 / 43** |
| ProviderService | `mvnw test` | **66 / 66** (thêm 1 test cho 2 endpoint mới + kiểm trạng thái) |
| ApiGateway | `mvnw test` | **20 / 20** |
| FE | `pnpm lint`, `pnpm typecheck`, `pnpm test`, `pnpm build` | Đều pass — **338 test**. `pnpm prettier --check` chỉ báo `pnpm-lock.yaml` (không sửa, lỗi có sẵn — G16) |
| Mobile | `npm ci`, `npm run lint`, `typecheck`, `test` | lint 0 lỗi (104 cảnh báo định dạng có sẵn), typecheck pass, **101 / 101 test**. `prettier --check` báo gần như mọi file do CRLF của bản checkout (có sẵn — G17) |

**Chưa làm:** chạy app thật end-to-end trên trình duyệt (cần chủ dự án sửa `.env`, áp migration / reset DB dev, build lại container).

### 8.3 Việc chủ dự án cần làm trước khi thử

1. `BE/PaymentService/.env`: đặt `PAYMENT_JWT_ISSUER=http://localhost:8081` (bằng `IDENTITY_JWT_ISSUER`) — X1.
2. DB dev BookingService: reset rồi áp `schema/booking.sql`, **hoặc** áp `schema/changes/2026-11-02_booking_spec_v1.sql`.
3. Build lại + restart: booking-service, payment-service, provider-service, api-gateway.
4. `BE/BookingService/.env` còn `BOOKING_MIN_LEAD_TIME`, `BOOKING_ACCEPTANCE_TIMEOUT`, `BOOKING_DEPOSIT_TIMEOUT`. Không còn dùng, vô hại; có thể xóa.

### 8.4 Tồn đọng / lưu ý (để đợt 4–5)

| # | Nội dung | Ảnh hưởng hiện tại |
|---|---|---|
| T1 | **SHIFT trả 100%** mới thể hiện ở Booking (quote, `initialPaymentPercent`). PaymentService **vẫn tính cọc theo `platform_policies` (30%)** khi checkout | Tới đợt 4, khách đặt SHIFT thực tế chỉ trả 30% lúc checkout. Trang đặt cọc FE vẫn ước tính theo Payment |
| T2 | Trả phần còn lại trước buổi đầu, quá hạn → EXPIRED + mất cọc, hoàn 100 / 70, nhắc 72h / 48h: **chỉ mới lưu trường policy và `remainingPaymentDueAt`** | Đúng M7 — làm ở đợt 4. Hóa đơn REMAINING vẫn sinh sau khi hoàn tất như cũ |
| T3 | `checkInEarlyMinutes` / `checkInLateMinutes` của policy **chưa được áp** — check-in / MISSED vẫn dùng env `BOOKING_SESSION_START_WINDOW` / `BOOKING_MISSED_GRACE` | Cần bạn chọn làm ở đợt nào (xem Q12) |
| T4 | Chưa có màn hình FE để manager / admin xem / sửa center policy; `/admin/policies` vẫn là mock tĩnh | API đã có; UI thuộc đợt 5 |
| T5 | Booking cũ (trước migration) có `remaining_payment_due_at = NULL` → giữ quy tắc cũ (hóa đơn sau hoàn tất) | Đợt 4 phải xử lý NULL như dữ liệu cũ |
| T6 | Lịch tháng (admin / manager) hiện CUSTOM chỉ ở ngày đầu (danh sách không có giờ từng buổi) | Hiển thị gần đúng; chi tiết vẫn đủ |

### 8.5 Câu hỏi mới / đề xuất (chưa làm)

- **Q11 (lệch spec phát hiện khi làm):** spec §7.2 tính đơn vị HOUR = "số phút / 60, làm tròn theo quy tắc tiền tệ" (90 phút = 1,5 giờ). Code đang **làm tròn lên số giờ** (`BookingPriceCalculatorImpl`: 90 phút = 2 giờ). Có sửa theo spec không? Không có trong kế hoạch đã duyệt nên tôi chưa sửa. `booking_details.quantity` / `units_per_occurrence` đang là số nguyên → sửa cần đổi kiểu cột.
- **Q12:** áp `checkInEarly` / `checkInLate` của policy vào check-in và job MISSED ở đợt nào (T3)?

| # | Vị trí | Vấn đề | Đề xuất | Lợi ích / rủi ro |
|---|---|---|---|---|
| G14 | `BookingApiTests.forgottenCareSessionIsAutoClosedFourHoursAfterTheVisitEnds` (~dòng 1750) | Chỉ hỏng ở chế độ PostgreSQL: đặt `endAt = now − 3h` khi `startAt ≈ now`, vi phạm `ck_booking_occurrences_range` | Dời cả `startAt` khi chỉnh `endAt` trong test | Bộ test PostgreSQL xanh; không đổi code chính |
| G15 | BookingService `BookingSummaryResponse` | Danh sách không có giờ từng buổi nên lịch tháng FE không vẽ đủ CUSTOM (T6) | Thêm danh sách khoảng giờ rút gọn vào summary, hoặc API lịch tháng riêng | Lịch đúng; tăng payload |
| G16 | FE `pnpm-lock.yaml` | `prettier --check` báo lỗi, làm `pnpm check` dừng sớm (có sẵn) | Thêm vào `.prettierignore` hoặc format lại | `pnpm check` chạy hết |
| G17 | mobile toàn repo | Bản checkout CRLF, prettier `endOfLine` LF → `npm run check` dừng ở bước format (có sẵn) | `.gitattributes` `eol=lf` hoặc cấu hình prettier `endOfLine: auto` | Check chạy được trên Windows |
| G18 | BookingService `PaymentPort.findEffective` + `PaymentClient` `GET /payment-policies/effective` | Quote không còn dùng (policy chuyển sang Booking — Q4a), code còn lại không ai gọi | Xóa ở đợt 4 khi Payment đọc policy từ `payment-context` | Gọn code |

### 8.6 Commit message đề xuất (bạn tự commit)

**BE** (`CareConnect/BE`) — có thể tách 2 commit:

```
fix(payment): require PAYMENT_JWT_ISSUER to match IdentityService issuer

The default "careconnect-identity" made PaymentService reject every user token (401),
so checkout and booking creation (debt check -> 503) failed.
```

```
feat(booking): booking spec v1.0 - statuses, SHIFT/RECURRING/CUSTOM, center booking policy

- Booking statuses PENDING_CENTER_CONFIRMATION / PENDING_CAREGIVER_ASSIGNMENT /
  AWAITING_INITIAL_PAYMENT; occurrence PENDING_ASSIGNMENT / RESERVED / EXPIRED / CAREGIVER_MISSED
- Confirm without caregiver then assign within the policy deadline; expiry of assignment
- Booking types SHIFT (caregiver schedule), RECURRING (daily/weekly), CUSTOM (own slots, no overlap)
- CenterBookingPolicy aggregate + GET/PUT /api/v1/center-booking-policies/{centerId}
- GET /api/v1/bookings/held-schedules; one booking per caregiver schedule
- ProviderService: bookable schedules + schedule lookup for SHIFT, availability checks caregiver/center status
- PaymentService / ProviderService compatibility with the renamed statuses
- Gateway route for center booking policies; schema + change script 2026-11-02_booking_spec_v1.sql
```

**FE** (`FE-careconnect`):

```
feat(booking): booking spec v1.0 statuses and SHIFT/RECURRING/CUSTOM booking form

- Regenerate API client: new booking/occurrence statuses, booking types, policy and held-schedule endpoints
- Center manager confirms without caregiver and assigns caregivers for PENDING_CAREGIVER_ASSIGNMENT
- Booking form: one booking per request, services before caregivers, SHIFT shift picker with held shifts
  locked, CUSTOM visits with their own times; initial payment percent from the center policy (no fixed 30%)
```

**Mobile** (`careconnect-mobile`):

```
fix(visits): follow booking spec v1.0 occurrence statuses (CAREGIVER_MISSED, EXPIRED, RESERVED)
```


---

## 9. Kết quả đợt 4 — PaymentService (2026-11-02) — CHỜ DUYỆT, chưa commit / push

> Phạm vi: §4 đợt 4 (B5, B6, B9, B13, B14) theo quyết định Q3, Q4a, Q5, M1–M6. Không làm Q11, Q12, G14–G18 (chưa duyệt).
> Đợt này giải quyết T1, T2 và T5 ở §8.4.

### 9.1 Cách triển khai (chi tiết kỹ thuật tôi chọn trong phạm vi kế hoạch)

| Điểm | Cách làm | Lý do |
|---|---|---|
| Nguồn % và chính sách hoàn | Booking gửi qua `payment-context`: `initialPaymentPercent` (đã có từ đợt 1), cùng 4 trường mới là `refundThresholdMinutes`, `refundPercentBeforeThreshold`, `refundPercentAfterThreshold`, `overduePaymentForfeitPercent`. Payment chụp tất cả vào `policy_snapshot` lúc mở phiên thanh toán đầu | Q4a: policy nằm ở Booking. Đổi policy sau khi khách đã trả không làm đổi số tiền của booking đó |
| Hóa đơn phần còn lại | Tạo **ngay trong transaction IPN** của khoản đầu, hạn = `remainingPaymentDueAt`. Dùng lại bảng `remaining_invoices` (vẫn một hóa đơn mỗi booking). Phân biệt bằng `booking_financials.remaining_due_at` (NULL nghĩa là SHIFT hoặc booking cũ) | Không thêm bảng hay API; booking cũ giữ nguyên quy tắc thu sau hoàn tất (T5) |
| Ai quyết "quá hạn" | **PaymentService.** Job quá hạn khóa dòng hóa đơn, là cùng dòng mà IPN trả phần còn lại cũng khóa. Payment giữ cọc, hoàn phần dư, rồi báo Booking qua outbox `/payment-overdue` | Không đua với khách vừa trả: trả trước thì không quá hạn; trả sau thì hóa đơn đã đóng nên khoản đó được hoàn lại. Nếu Booking tự quyết thì có thể vừa EXPIRED vừa thu tiền |
| Trạng thái hóa đơn | Thêm `EXPIRED` (quá hạn trước buổi đầu, **không phải nợ**) và `CANCELLED` (không còn gì phải trả). `OVERDUE` (nợ, chặn đặt lịch mới) chỉ còn dùng cho hóa đơn sau hoàn tất của booking cũ | Khách bỏ booking chưa trả đủ không bị coi là đang nợ |
| Booking biết "đã trả đủ" | Cột mới `bookings.fully_paid_at`, chỉ PaymentService đặt: `deposit-paid {fullyPaid:true}` cho SHIFT 100 %, hoặc `/remaining-paid`. Check-in bị chặn (409 `5242`) khi có `remainingPaymentDueAt` mà chưa trả đủ | Spec §9.1, §14: phải FULLY PAID trước khi chăm sóc |
| Quá hạn ở Booking | `EXPIRE` nhận thêm nguồn `CONFIRMED`. Buổi `SCHEDULED` / `ACKNOWLEDGED` chuyển `EXPIRED`, lý do `PAYMENT_OVERDUE`, giải phóng caregiver. Booking **không** xếp hoàn tiền vì Payment đã làm | M1 |
| Hoàn tiền khi đã trả đủ | Số hoàn **chia qua các payment đã thu**, REMAINING trước rồi DEPOSIT (giống khiếu nại). Bất biến: tổng hoàn ≤ tổng còn hoàn được | Phát hiện khi viết test: nếu chỉ hoàn từ khoản cọc thì hủy buổi đã trả đủ sẽ báo 409, và outbox của Booking retry mãi |
| Nhắc trả phần còn lại | Booking gửi (`REMAINING_DUE`), chạy cùng chu kỳ `BookingExpiryScheduler`, mỗi mốc của `remainingPaymentReminderMinutes` nhắc một lần. Cột `bookings.remaining_reminder_minutes` lưu mốc đã nhắc. Mốc đã qua lúc trả khoản đầu thì bỏ qua (M6) | Thông báo nằm ở BookingService; Payment không có module thông báo |

### 9.2 Danh sách thay đổi

| # | File | Thay đổi | Vấn đề |
|---|---|---|---|
| 1 | Payment `client/dto/BookingPaymentContextResponse`, `client/dto/DepositPaidRequest`, `client/BookingClient` | Đọc `initialPaymentPercent`, `remainingPaymentDueAt` và 4 trường policy. Gửi `fullyPaid`; thêm lời gọi `/remaining-paid`, `/payment-overdue` | B6, B9 |
| 2 | Payment `dto/response/PolicySnapshot`, `mapper/PaymentPolicyMapper`, `entity/BookingFinancial`, `mapper/FinancialMapper` | Snapshot có mốc / % hoàn, % giữ cọc, và `depositPercent` = % khoản đầu thật đã thu. Cột `remaining_due_at` | B6, B13 |
| 3 | Payment `service/Impl/PaymentServiceImpl` | Checkout dùng `initialPaymentPercent` (SHIFT 100 %). Booking cũ dùng % dự phòng của `platform_policies`. IPN khoản đầu mở hóa đơn trước buổi đầu. Doanh thu cho manager | B6, B5, B14 |
| 4 | Payment `service/Impl/RemainingInvoiceServiceImpl`, `service/RemainingInvoiceService` | `openBeforeService`. `markPaid` ghi đủ giá từng buổi và báo Booking. Quá hạn trước buổi đầu thì EXPIRED, giữ cọc, báo Booking. Phiên trả chỉ mở tới hạn. Hóa đơn sau hoàn tất không áp cho booking mới | B5, M1, M3 |
| 5 | Payment `util/RefundCalculator` (viết lại), `service/Impl/RefundServiceImpl`, `service/RefundService` | Hoàn 100 / 70 theo mốc của snapshot; bỏ cooling-off và bậc 72h. `forfeit` tính phần giữ cọc. `refundOverdueForfeit`. Hủy buổi thì giảm hóa đơn chưa trả, về 0 thì CANCELLED. Hoàn chia qua các payment. Booking đã quá hạn thì không hoàn thêm | B13, Q5, M3, M4 |
| 6 | Payment `service/Impl/PaymentOutboxServiceImpl`, `service/PaymentOutboxService` | Outbox 3 loại: `DEPOSIT_CONFIRMATION`, `REMAINING_CONFIRMATION`, `PAYMENT_OVERDUE`. Booking từ chối khoản vừa thu thì hoàn `BOOKING_NOT_CONFIRMABLE`; với báo quá hạn thì chỉ ghi lỗi | B5 |
| 7 | Payment `enums/InvoiceStatus`, `OutboxEventType`, `RefundReason`, `PaymentType` | `EXPIRED`, `CANCELLED`; 2 loại outbox mới; `PAYMENT_OVERDUE` (chỉ Payment tạo) | — |
| 8 | Payment `controller/PaymentController`, `service/PaymentService` | `GET /payments/revenue` cho CENTER_MANAGER; `centerId` lấy từ token | B14 |
| 9 | Payment `schema/payment.sql`, `schema/changes/2026-11-02-payment-spec-v1.sql` (mới) | Cột `remaining_due_at`; CHECK của trạng thái hóa đơn, lý do hoàn, loại outbox (đặt tên); bổ sung snapshot cũ thành 1440 / 100 / 70 / 100. Script chạy lại được | Q6 |
| 10 | Booking `entity/Booking`, `schema/booking.sql`, `schema/changes/2026-11-02_booking_spec_v1_payment.sql` (mới), `mapper/IgnoreServerManagedBookingFields`, `dto/response/BookingDetailResponse` | `fully_paid_at`, `remaining_reminder_minutes`; chi tiết booking trả `fullyPaidAt` | B5 |
| 11 | Booking `enums/BookingAction`, `enums/OccurrenceStatus` | `EXPIRE` nhận thêm `CONFIRMED`; buổi `SCHEDULED` / `ACKNOWLEDGED` được chuyển `EXPIRED` | M1 |
| 12 | Booking `controller/BookingController`, `service/BookingService`, `service/Impl/BookingServiceImpl`, `dto/request/DepositPaidRequest` | `POST /bookings/{id}/remaining-paid`, `POST /bookings/{id}/payment-overdue` (SERVICE, idempotent); `deposit-paid` nhận `fullyPaid`; `payment-context` mang policy | B5, M1 |
| 13 | Booking `service/Impl/BookingLifecycleServiceImpl`, `service/BookingLifecycleService`, `service/BookingNotificationService(+Impl)`, `repository/BookingRepository`, `scheduler/BookingExpiryScheduler` | FINALIZE chỉ sinh hóa đơn sau hoàn tất cho booking cũ. Nhắc trả phần còn lại theo mốc policy | B5, M6, T5 |
| 14 | Booking `service/Impl/CareSessionServiceImpl`, `exception/ErrorCode` | Check-in đòi trả đủ (409 `5242 BOOKING_NOT_FULLY_PAID`) | B5 |
| 15 | Hợp đồng `contracts/payment/refund-quote-response.json` (cả 2 repo), Booking `client/dto/RefundQuoteResponse` (comment) | Mã quy tắc mẫu `BEFORE_THRESHOLD` | B13 |
| 16 | Tài liệu: Payment `README.md`, `AGENTS.md`; Booking `README.md`, `AGENTS.md` | Khoản đầu, bảng hoàn mới, hóa đơn trước buổi đầu, outbox, endpoint mới, job nhắc, mã lỗi `5242` | rule #31 |

Không đổi: ApiGateway (các path mới nằm trong `/api/v1/bookings/**` và `/api/v1/payments/**` đã có), ProviderService, FE, mobile.

### 9.3 Kiểm thử đã chạy

| Phần | Lệnh | Kết quả |
|---|---|---|
| PaymentService | `mvnw test` (H2) | **48 / 48**. Test mới: SHIFT trả 100 % và báo `fullyPaid`; RECURRING cọc 40 % thì mở hóa đơn đúng số và hạn, không thành nợ, trả xong báo Booking, sau đó hủy buổi được hoàn 70 % số đã trả đủ; hủy buổi thì hóa đơn giảm rồi CANCELLED; quá hạn thì EXPIRED, giữ 40 % theo policy, báo Booking, lệnh hủy tới sau không hoàn, khách trả phiên cũ sau hạn được hoàn lại; manager xem doanh thu đúng trung tâm. `RefundCalculatorTests` viết lại theo mốc 100 / 70 và giữ cọc |
| PaymentService | `mvnw test -Dpayment.testing.postgres=true` (PostgreSQL 17 tạm, đã dừng) | **48 / 48** (kể cả các CHECK mới) |
| BookingService | `mvnw test` (H2) | **116 test, 0 lỗi, 2 skip.** Test mới: check-in cần trả đủ, `/remaining-paid` idempotent; `/payment-overdue` cho ra EXPIRED, buổi EXPIRED, lý do `PAYMENT_OVERDUE`, không có outbox hoàn tiền, caregiver được giải phóng, `payment-context` mang policy; nhắc 72h / 48h mỗi mốc một lần. Hai test hóa đơn sau hoàn tất chuyển sang giả lập **booking cũ**; hai test khách vắng giờ kiểm "không còn hóa đơn sau hoàn tất" |
| BookingService | `mvnw test -Dbooking.testing.postgres=true` | **115 / 116.** Lỗi duy nhất vẫn là `forgottenCareSession…`, có sẵn từ trước (G14) |
| Change script | PostgreSQL tạm: schema HEAD + change script so với tạo mới (cột, kiểu, null, default, constraint, index) | Booking: **trùng khớp** (HEAD + `spec_v1` + `spec_v1_payment`). Payment: **trùng khớp**; chạy lại 2 lần không lỗi; snapshot cũ được bổ sung đúng 1440 / 100 / 70 / 100 |

### 9.4 Việc chủ dự án cần làm trước khi thử

1. DB dev **BookingService**: áp `schema/changes/2026-11-02_booking_spec_v1_payment.sql` (sau `2026-11-02_booking_spec_v1.sql`), hoặc reset rồi áp `schema/booking.sql`.
2. DB dev **PaymentService**: áp `schema/changes/2026-11-02-payment-spec-v1.sql`, hoặc reset rồi áp `schema/payment.sql`.
3. Build lại và restart booking-service, payment-service. Không cần biến môi trường mới.
4. Thử nhanh: đặt RECURRING → trung tâm xác nhận → trả cọc → vào trang "Thanh toán" của khách sẽ thấy hóa đơn phần còn lại (FE đã có sẵn nút trả) → trả → caregiver check-in được.

### 9.5 Tồn đọng sau đợt 4

| # | Nội dung | Ảnh hưởng | Đợt |
|---|---|---|---|
| T3 | `checkInEarly` / `checkInLate` của policy chưa áp | Như §8.4 — chờ Q12 | ? |
| T4 | Chưa có UI policy cho manager / admin | Như §8.4 | 5 |
| T7 | **FE khung "Chính sách hoàn tiền"** (`entities/payment/refund-policy.tsx`) vẫn đọc `/payment-policies/effective` và hiện bậc cũ 0 / 10 / 30 % cùng cooling-off | **Hiển thị sai so với số tiền hoàn thật** (100 / 70). Cần đọc `GET /center-booking-policies/{centerId}` | 5 (ưu tiên) |
| T8 | FE chưa có nhãn cho trạng thái hóa đơn `EXPIRED` / `CANCELLED` và mã `BEFORE_THRESHOLD` / `AFTER_THRESHOLD` / `PAYMENT_OVERDUE` | Hóa đơn hiện key thô; dòng hoàn tiền hiện nhãn "khác". Không vỡ trang | 5 |
| T9 | FE chi tiết booking chưa có nút trả phần còn lại, hạn trả và `fullyPaidAt`. Trang cọc vẫn ước tính theo % của Payment | Khách trả được qua trang "Thanh toán". Số tiền checkout thật do server tính nên đúng (SHIFT 100 %), chỉ con số ước tính trên trang cọc có thể lệch | 5 |
| T10 | Hóa đơn bị giảm (hủy buổi / khiếu nại) trong lúc khách đang mở phiên trả với số cũ | Khách trả số cũ thì hóa đơn vẫn PAID; phần trả dư **không tự hoàn** (hành vi này có sẵn ở luồng khiếu nại). Xem G19 | đề xuất |
| T11 | Thứ tự khóa: lệnh hoàn khóa `booking_financials` rồi hóa đơn, còn IPN / job quá hạn khóa hóa đơn rồi `booking_financials` | Hiếm khi xảy ra deadlock: PostgreSQL hủy một bên, outbox / VNPay thử lại. Luồng khiếu nại có sẵn cùng kiểu. Xem G20 | đề xuất |
| T12 | `platform_policies.cooling_off_*`, `refund_tiers`, `deposit_due_hours` không còn dùng để tính tiền | Chỉ còn được trả qua `/payment-policies/effective` cho FE (T7). Xem G21 | đề xuất |

### 9.6 Đề xuất mới (chưa làm)

| # | Vị trí | Vấn đề | Đề xuất | Lợi ích / rủi ro |
|---|---|---|---|---|
| G19 | Payment `RemainingInvoiceServiceImpl.markPaid` | T10 | Số tiền phiên khác số hóa đơn hiện tại → nhận phần đúng, hoàn phần dư (hoặc hoàn cả rồi để khách trả lại) | Đúng tiền; thêm nhánh xử lý |
| G20 | Payment `RefundServiceImpl.create` / `createComplaintRefund` | T11 | Khóa hóa đơn trước rồi mới khóa `booking_financials` ở mọi luồng | Hết deadlock; phải sửa cả luồng khiếu nại |
| G21 | Payment `platform_policies` + `GET /payment-policies/effective` | T12 | Sau đợt 5 (FE đọc center policy), bỏ các cột và trường không dùng, cùng change script | Gọn schema; phá contract `payment-policies` |
| G22 | Booking `TestPlatformServer` (stub Payment trong test) | Stub vẫn trả mã quy tắc cũ `TIER_*` | Đổi sang mã mới cho đồng bộ | Chỉ ảnh hưởng test |

Câu hỏi còn mở: **Q11** (làm tròn giờ), **Q12** (cửa sổ check-in theo policy), cùng G14–G18 ở §8.5.

### 9.7 Commit message đề xuất (bạn tự commit)

**BE** (`CareConnect/BE`), commit riêng sau các commit của §8.6:

```
feat(payment,booking): booking spec v1.0 payments - initial payment, remaining before first visit, 100/70 refunds

- PaymentService charges the initial payment percent from Booking (SHIFT 100 %), snapshots the center
  booking policy (refund threshold 100/70, overdue forfeit) at checkout
- Remaining invoice opens with the initial payment, due before the first visit; paying it notifies
  Booking (/remaining-paid); cancelling visits shrinks it; overdue -> invoice EXPIRED, deposit kept per
  policy, rest refunded, Booking notified (/payment-overdue -> booking EXPIRED)
- Refunds split across DEPOSIT and REMAINING payments; cooling-off and 72h tiers removed
- Revenue readable by center managers for their own center
- BookingService: fully_paid_at gates check-in (5242), EXPIRE from CONFIRMED, remaining payment reminders,
  post-completion invoice only for pre-v1 bookings
- Change scripts 2026-11-02-payment-spec-v1.sql and 2026-11-02_booking_spec_v1_payment.sql
```


---

## 10. Kết quả đợt 5 — FE (2026-11-03) — CHỜ DUYỆT, chưa commit / push

> Phạm vi: §4 đợt 5 (mục 1–7) và các tồn đọng FE T4, T7, T8, T9 ở §9.5. Không làm Q11, Q12, G14–G22 (chưa duyệt).
> Chỉ sửa repo `FE-careconnect`. BE và mobile không đổi trong đợt này.

### 10.1 Danh sách thay đổi

| # | File | Thay đổi | Vấn đề |
|---|---|---|---|
| 1 | `openapi/openapi.yaml` + `app/shared/api/generated/` (sinh lại) | `BookingDetailRecord.fullyPaidAt`; `InvoiceStatus` thêm `EXPIRED`, `CANCELLED`; thêm `GET /v1/payments/revenue` (`getPaymentRevenue`, `RevenueResponse` / `RevenueRecord`) | Đợt 4, B14 |
| 2 | `entities/payment/payment-api.ts` | `initialPaymentOf` thay `useDepositAmount`: lấy % khoản đầu từ `initialPaymentPercent` của booking (SHIFT 100), tính luôn phần còn lại; không còn gọi `/payment-policies/effective`. Thêm `fetchOpenInvoiceOfBooking`, `fetchRevenue`, key `bookingInvoice` / `revenue`, mã lỗi `6027` | B6, B15, T9 |
| 3 | `entities/payment/refund-policy.tsx` (viết lại) | Khung "Hoàn tiền khi bạn hủy" đọc center booking policy (`GET /center-booking-policies/{id}`): hoàn từ mốc / dưới mốc, % giữ khi quá hạn trả nốt. Bỏ bậc 72h và cooling-off. `refundRuleLabel` hiểu `BEFORE_THRESHOLD`, `AFTER_THRESHOLD`, `PAYMENT_OVERDUE` | **T7**, T8 |
| 4 | `features/customer/bookings/components/booking-status-panel.tsx`, `booking-detail-summary.tsx` | Booking CONFIRMED chưa trả đủ: ghi số tiền và hạn trả phần còn lại, nút "Thanh toán phần còn lại" mở thẳng phiên VNPay của hóa đơn (không tìm thấy hóa đơn thì dẫn sang trang Thanh toán). Tóm tắt: khoản đầu (x %), phần còn lại + hạn, "Đã thanh toán đủ". **Sửa sót từ đợt 1:** `WAITS_FOR_ASSIGNMENT` còn dùng tên trạng thái cũ `PENDING` / `AWAITING_DEPOSIT`, nên ô người chăm sóc không hiện khi booking đang chờ | T9, B5 |
| 5 | `features/customer/payment/deposit-page.tsx` | Trang "Thanh toán khoản đầu": hiện % tổng giá trị, phần còn lại cùng hạn trả (SHIFT: "toàn bộ giá trị, không thu thêm") | B6, T9 |
| 6 | `features/customer/booking/components/booking-summary-sidebar.tsx` | Khi tạo booking: "Phần còn lại … trả trước … (trước buổi đầu)" = buổi đầu − `remainingPaymentDueBeforeMinutes` của policy | §4 đợt 5 mục 2 |
| 7 | `features/center-manager/booking-management/components/manager-occurrence-actions.tsx`, `manager-booking-detail-page.tsx`, `manager-booking-summary.tsx` | **Hủy từng buổi** (B16) khi booking đã trả (CONFIRMED / IN_PROGRESS), gửi `occurrenceIds`; khách được hoàn 100 % theo chính sách trung tâm hủy. Tóm tắt: hạn trả nốt, "Khách đã thanh toán đủ" | B16 |
| 8 | `entities/booking/center-booking-policy-form.tsx` (mới) + `booking-api.ts` (`updateCenterBookingPolicyRequest`) | Form chính sách đặt lịch dùng chung cho manager và admin: 5 nhóm của spec §8. Giờ hiển thị theo giờ, lưu theo phút. Kiểm từng ô và các ràng buộc của BE (đặt trước > hạn trả nốt, hoàn sau mốc ≤ trước mốc, mốc nhắc không trùng / ≤ 5). **Cảnh báo tổ hợp quá ngắn** (không chặn). Gửi `version` để báo xung đột 409 `5006` | **T4**, B7, §4 đợt 5 mục 5 |
| 9 | `features/center-manager/policy/manager-policy-page.tsx` (mới), `routes/center-manager/policy.tsx` (mới), `routes.ts`, `manager-navigation.constants.ts` | Trang `/center-manager/policy` cho trung tâm của chính manager (lấy từ phiên đăng nhập), có trong menu | T4 |
| 10 | `features/admin/policies/admin-policies-page.tsx` (viết lại), xóa `policies-preview.ts`, `admin-navigation.constants.ts` | Thay trang mock bằng trang thật: chọn trung tâm đang hoạt động rồi sửa chính sách; đưa vào menu "Vận hành" | T4 |
| 11 | `features/center-manager/statistics/components/center-revenue-panel.tsx` (mới), `manager-statistics-page.tsx` | Doanh thu thuần của trung tâm (đã thu, đã hoàn, số giao dịch) theo tháng này / 30 ngày / năm nay | B14, §4 đợt 5 mục 6 |
| 12 | i18n: `payment-resources.ts`, `booking-resources.ts` (`booking.policy`, mã lỗi `5242` / `5413` / `5414`), `customer/booking-detail-resources.ts`, `customer/payment-resources.ts`, `customer/booking-resources.ts`, `center-manager/booking-detail-resources.ts`, `center-manager/statistics-resources.ts`, `center-manager/policy-resources.ts` (mới), `admin/policies-resources.ts` (viết lại), `admin/layout-resources.ts`, `resources.ts` | Copy vi / en: "khoản thanh toán đầu" thay "cọc", trạng thái hóa đơn mới, lý do và quy tắc hoàn mới; bỏ các key cooling-off / tier | T8 |
| 13 | `entities/booking/booking-format.ts` | Thêm `5242`, `5413`, `5414` vào danh sách mã lỗi có câu dịch | — |
| 14 | Test: `booking-detail-page.test.tsx`, `payment-pages.test.tsx`, `payments-page.test.tsx`, `manager-booking-pages.test.tsx`, `admin-policies-page.test.tsx` (viết lại), mới: `center-booking-policy-form.test.tsx`, `manager-policy-page.test.tsx`, `center-revenue-panel.test.tsx` | Xem §10.2 | — |
| 15 | `ARCHITECTURE.md` | Luồng thanh toán đầu / phần còn lại, khung hoàn tiền, form policy, hủy từng buổi, doanh thu manager | rule tài liệu |

### 10.2 Kiểm thử đã chạy

| Lệnh | Kết quả |
|---|---|
| `pnpm api:validate`, `pnpm api:generate` | Pass |
| `pnpm typecheck` | Pass |
| `pnpm lint` | Pass (0 lỗi) |
| `pnpm vitest run` | **84 file, 350 test pass**. Test mới: SHIFT trả 100 %; trang thanh toán đầu hiện % và hạn trả nốt; khung hoàn tiền đọc center policy (100 % / 70 %, giữ 100 %), không còn cooling-off; nút trả phần còn lại mở đúng hóa đơn; booking đã trả đủ không hỏi thêm; form policy đổi giờ ↔ phút, kiểm ràng buộc, cảnh báo tổ hợp, gửi `version`, báo 409; trang admin chọn trung tâm; trang manager (có / không có trung tâm); doanh thu manager và lỗi `6027`; hủy từng buổi chỉ khi đã trả |
| `pnpm build` | Pass |
| `prettier --check` (mọi file đã sửa / thêm) | Pass. `pnpm check` toàn repo vẫn dừng ở `pnpm-lock.yaml` (G16, có sẵn) |

**Chưa làm:** kiểm trên trình duyệt thật. Stack CareConnect chưa chạy trên máy này; cần chủ dự án áp change script của đợt 1–4 và build lại container (§8.3, §9.4).

### 10.3 Tồn đọng sau đợt 5

| # | Nội dung | Ảnh hưởng |
|---|---|---|
| T3 | `checkInEarly` / `checkInLate` vẫn chưa áp vào check-in (chờ Q12). Form policy ghi rõ điều này dưới nhóm "Cửa sổ check-in" | Hai ô lưu được nhưng chưa có tác dụng |
| T6 | Lịch tháng chỉ vẽ booking CUSTOM ở ngày đầu (G15) | Như §8.4 |
| T13 | Trang doanh thu của admin (`/admin/revenue`) vẫn là bản mock, chưa có trong menu | Không nằm trong kế hoạch đợt 5. API thật đã có (`fetchRevenue` nhận `centerId` cho admin) |
| T14 | `fetchOpenInvoiceOfBooking` đọc 100 hóa đơn PENDING đầu tiên của khách (Payment chưa có lọc theo booking) | Khách có hơn 100 hóa đơn chờ cùng lúc thì nút trả trên trang booking chuyển sang link "Xem hóa đơn ở trang Thanh toán" |
| T15 | Generated client và `openapi.yaml` vẫn giữ `GET /payment-policies/effective` (FE không còn dùng) | Dọn cùng G21 |

### 10.4 Đề xuất mới (chưa làm)

| # | Vị trí | Vấn đề | Đề xuất | Lợi ích / rủi ro |
|---|---|---|---|---|
| G23 | FE `entities/provider/provider-status-badge.tsx` | Bảng màu còn key cũ `AWAITING_DEPOSIT`, `MISSED`. Màn hình provider không gặp các trạng thái này nên không lỗi | Bỏ hai key cũ | Gọn; không đổi hành vi |
| G24 | PaymentService `GET /remaining-invoices` | Không lọc được theo booking (T14) | Thêm `bookingId` vào bộ lọc (BE + contract) | FE tìm hóa đơn chắc chắn hơn |
| G25 | FE `/admin/revenue` | T13 | Nối API thật cho admin (lọc trung tâm, khoảng ngày) và đưa vào menu | Admin xem doanh thu thật; cần thiết kế lại trang mock |

Câu hỏi còn mở: **Q11** (làm tròn giờ), **Q12** (cửa sổ check-in theo policy), G14–G25.

### 10.5 Commit message đề xuất (bạn tự commit)

**FE** (`FE-careconnect`), commit riêng sau commit của §8.6:

```
feat(payment,booking): booking spec v1.0 payments and center booking policy UI

- Initial payment from the booking's initialPaymentPercent (SHIFT 100%); the rest is due before the first
  visit, paid from the booking detail (remaining-balance invoice) and shown as paid in full afterwards
- Refund policy panel and refund rule labels follow the center booking policy (100/70, overdue forfeit);
  cooling-off and 72h tiers removed; invoice statuses EXPIRED / CANCELLED
- Center booking policy form shared by /center-manager/policy and /admin/policies (replaces the mock):
  hours in the UI, minutes in the API, client checks of the backend rules, warnings for tight deadlines
- Center managers cancel a single paid visit (B16) and see the center's net revenue
- Fix the caregiver card still keyed on the pre-v1 statuses in the customer booking summary
```


---

## 11. Kết quả đợt 6 — Tài liệu (2026-11-03)

Tài liệu luồng sau khi sửa tách thành file riêng **`2026-11-03-BOOKING_FLOW_V1.md`** (file này đã gần 1000 dòng và là
nhật ký rà soát; file mới mô tả trạng thái hiện tại của code để tra cứu). Nội dung:

| Mục | Nội dung |
|---|---|
| §1 | Ba loại booking, các mốc thời gian lưu trên booking và cách tính |
| §2 | Luồng theo vai trò (khách, manager, caregiver, admin, hệ thống), kèm màn hình FE và API |
| §3 | Mermaid `stateDiagram-v2` + bảng chuyển cho booking, buổi chăm sóc, hóa đơn phần còn lại |
| §4 | Sơ đồ tuần tự: RECURRING chưa có caregiver tới trả đủ; SHIFT 100 %; quá hạn phần còn lại; hủy một buổi đã trả |
| §5 | API contract của Booking và phần Payment / Provider dùng, body tạo booking, center policy (mặc định, giới hạn, ràng buộc), mã lỗi thường gặp |
| §6 | Edge case (spec §15 và phát sinh khi làm) |
| §7 | Hướng dẫn test thủ công: chuẩn bị và 14 kịch bản |
| §8 | Tồn đọng tổng hợp và câu hỏi còn mở |

Mọi giá trị (trạng thái, cạnh chuyển, role, mặc định policy, mã lỗi) đối chiếu lại với code lúc viết. Không sửa code
trong đợt này, trừ một comment cũ trong `BookingService/src/main/resources/application.yaml` (giới hạn booking chờ
còn ghi tên trạng thái trước v1).

Trung tâm ARCHIVED (X2) đã làm ngoài phạm vi booking: khôi phục về `INACTIVE` (D31, `IMPLEMENTATION_PROGRESS.md`).

---

## 12. Q11 + Q12 (2026-11-03) — chưa commit

Chủ dự án chốt: Q11 tính giờ lẻ theo spec (90 phút = 1,5 giờ); các câu hỏi khác theo đề xuất của tôi. Q12 → áp ngay
cửa sổ check-in của center policy. Q9 / Q10 / M3 giữ quyết định đã chốt (§7.1, §7.8), chỉ còn chờ người viết spec xác nhận.

| # | Quyết định / cách làm | Lý do |
|---|---|---|
| Q11 | HOUR: số giờ = phút / 60 (lưu 4 chữ số thập phân), tiền dòng = đơn giá × phút / 60 tính chính xác, giá buổi làm tròn đến đồng. `booking_details.quantity` / `units_per_occurrence` sang `numeric` | Spec §7.2 "làm tròn theo quy tắc tiền tệ" áp cho tiền, không cho số giờ. Booking cũ giữ số nguyên đã tính, tiền không đổi |
| Q12 | `bookings.check_in_early_minutes` / `check_in_late_minutes` chụp từ policy lúc tạo. Check-in `[start − early, start + late)`, báo vắng `[start, start + late)`, job MISSED khi `start + late ≤ now` (HQL `o.startAt + (b.checkInLateMinutes) minute`). Buổi của caregiver trả `checkInOpensAt` / `checkInClosesAt`; web và mobile dùng mốc này (30' chỉ là dự phòng) | Job MISSED quét bằng một query nên cần mốc theo từng dòng; chụp lúc tạo giống các hạn khác và `policy_snapshot` của Payment — đổi policy không đổi cửa sổ của ca đã đặt. Bỏ env `BOOKING_SESSION_START_WINDOW`, `BOOKING_MISSED_GRACE` |

| Repo | File chính |
|---|---|
| BE Booking | `BookingPriceCalculatorImpl`, `ServiceQuote`, `BookingDetail`, `BookingItemResponse`, `QuoteLineResponse`; `Booking`, `IgnoreServerManagedBookingFields`, `BookingServiceImpl`, `CareSessionServiceImpl`, `BookingOccurrenceServiceImpl`, `BookingOccurrenceRepository.findMissedIds`, `CaregiverOccurrenceResponse`, `OccurrenceMapper`, `ErrorCode` (5222, 5817 không còn ghi 30'), `BookingProperties`, `application.yaml`, `.env.example`; `schema/booking.sql`, `schema/changes/2026-11-03_booking_spec_v1_q11_q12.sql`; README, AGENTS |
| FE | `openapi.yaml` (+ generate): units `number`, `checkInOpensAt` / `checkInClosesAt`; `caregiver/visits/components/visit-actions.tsx`; câu chữ check-in, mã 5222 / 5817, ghi chú form policy |
| Mobile | `core/time.ts` `checkInAllowed`, `visits/models.ts`, `visits/screens.tsx`, `core/l10n.ts` |

**Kiểm thử.** BookingService H2 **118 / 118** (2 skip); PostgreSQL 17 tạm **117 / 118** — lỗi duy nhất vẫn là G14 có
sẵn. Test mới: 90 phút = 1,5 giờ / 150.000đ, 70 phút = 116.667đ; báo giá và booking 150 phút = 2,5 giờ; cửa sổ 60' / 10'
theo policy, đổi policy sau khi đặt không ảnh hưởng, job MISSED theo mốc mới; mapper trả mốc mở / đóng. Change script:
schema HEAD + script **trùng khớp** schema tạo mới (pg_dump). FE: test web liên quan pass, test mới cho cửa sổ theo
policy. Mobile 101 / 101, tsc pass.

**Cần làm trên DB dev:** áp `schema/changes/2026-11-03_booking_spec_v1_q11_q12.sql` rồi build lại booking-service (thiếu
script thì service dừng ở bước `ddl-auto: validate`).

