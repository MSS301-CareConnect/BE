# GAP ANALYSIS — CareConnect Backend

> Ngày khảo sát: **2026-10-21** · Nhánh `dev` @ `1609b4f` · Phạm vi: toàn bộ `BE/` (7 service) và đối chiếu `../FE-careconnect`.
> Tài liệu chỉ đọc code, **không sửa code**. Mọi kết luận "có / chưa có" đều dẫn đường dẫn file (dòng nếu cần).
> Kế hoạch triển khai tương ứng: `docs/WORK_PLAN.md`.

**Viết tắt đường dẫn** (tính từ `BE/`):

| Viết tắt | Đường dẫn |
|---|---|
| `ID/` | `IdentityService/src/main/java/mss301/identityservice/` |
| `CR/` | `CareRecipientService/src/main/java/mss301/carerecipientservice/` |
| `CC/` | `CareCatalogService/src/main/java/mss301/carecatalogservice/` |
| `PV/` | `ProviderService/src/main/java/mss301/providerservice/` |
| `BK/` | `BookingService/src/main/java/mss301/bookingservice/` |
| `MD/` | `MediaService/src/main/java/mss301/mediaservice/` |
| `GW/` | `ApiGateway/src/main/java/mss301/apigateway/` |
| `BSI` | `BK/service/Impl/BookingServiceImpl.java` |
| `BC` | `BK/controller/BookingController.java` |

---

## 0. Nguồn đã tham khảo

| Nguồn | Thông tin quan trọng rút ra |
|---|---|
| `AGENTS.md` (gốc) | Quy ước bắt buộc: aggregate → Repository/Service/ServiceImpl/Controller, `/api/v1/<resource>`, `APIResponse<T>`, MapStruct (`MapStructConfig`, `@IgnoreServerManagedFields`), `AppException` + `ErrorCode` với dải mã riêng từng service, soft delete, **không Flyway** (SQL áp tay + `schema/changes/`), `ddl-auto: validate`, không I/O mạng trong transaction (rule #24), gọi service khác qua Gateway bằng OpenFeign + `FeignAuthForwardingInterceptor`. Bảng "chức năng → domain" giao care session / report cho BookingService. Mục "chưa chốt" gồm % cọc/hoàn tiền, ai sửa giá, ranh giới service. |
| `BookingService/AGENTS.md`, `README.md` | State machine chỉ nằm ở `BookingAction`, advisory lock chống trùng lịch, hai id caregiver (Provider + Identity). **Lỗi thời**: còn mô tả `/internal/**`, `X-Internal-Secret`, `BookingLifecycleService`, `ScheduleConflictService`, path `/center/bookings`. |
| `ProviderService/AGENTS.md`, `README.md` | **Lỗi thời**: path `/admin/centers`, `/catalog/caregivers`, `/internal/**`, `InternalProviderApiTests` (đã bị xóa ở commit `bfcd2e4`). TODO: `caregiver_services`, API lịch trống. |
| `CareRecipientService/AGENTS.md`, `README.md` | OWNER/EDITOR/VIEWER, OWNER cuối cùng. **Lỗi thời**: `/internal/**`, `CaregiverAssignmentService` (không còn). |
| `CareCatalogService/README.md`, `note.md`, `copy_base.py` | `note.md` lỗi thời (`/internal/center-services/quote`, `HOURLY/DAILY/SESSION`). `copy_base.py` hardcode `c:\Users\Juuga\...` — file rác. Không có `AGENTS.md` dù README tham chiếu. |
| `MediaService/AGENTS.md`, `README.md` | **Sai hoàn toàn** so với code: ghi port 8085/DB 5445/`/internal/media`; code thật port 8086/DB 5446/`/api/v1/media`. Dải lỗi 5000–5999 **trùng BookingService**. |
| `ApiGateway/README.md`, `AGENTS.md`, `DOCKER.md` | README liệt kê route cũ; `DOCKER.md` ghi gateway chưa route Catalog/Provider/Media — sai, code đã route đủ 6 service (`GW/config/GatewayPaths.java`). |
| `docs/PLAN.md` | Checklist tiến độ. Mục mở: `caregiver_services`, nối Payment, start/end ca, `occurrence_services`, `care_reports`, `feedbacks`, Flyway, rate limiting. Mục "nối CareCatalog" còn `[ ]` dù code đã nối (`BK/service/Impl/CareServiceCatalogPortImpl.java`). |
| `docs/2026-10-09-PLAN_BOOKING.md` | §0 quyết định giai đoạn 1: PENDING → ACCEPT → AWAITING_DEPOSIT → PAY_DEPOSIT → CONFIRMED; tối đa 60 buổi/90 ngày, báo trước 12h, hạn cọc 24h, hạn duyệt 24h; `RefundPolicy` theo bên hủy; thiết kế dự kiến `occurrence_services`, `care_reports`, `feedbacks`. §6/§10 mô tả 5 controller tách role — đã gộp ở refactor 2026-10-18/19. |
| `docs/2026-10-09-BOOKING_MANUAL_TEST.md` | Hướng dẫn test tay — path cũ + internal secret, không còn chạy đúng. |
| `docs/2026-10-03-PLAN_CARE_RECIPIENT_GATEWAY.md`, `docs/2026-10-03-MAPPER_REVIEW.md` | Chuẩn MapStruct áp cho Identity + CareRecipient; Catalog chưa theo. Nợ: đồng bộ `recipient_managers` khi user bị khóa, audit hồ sơ. |
| `schema/*.sql` của từng service | Nguồn sự thật về bảng/CHECK/unique (bảng ở §3). Provider có `provider_upgrade_v2.sql`, `provider_upgrade_v3_media.sql` không theo quy ước `schema/changes/`. Media tự áp schema qua `docker-entrypoint-initdb.d`. |
| `application.yaml`, `.env.example`, `compose.yaml`, `*/compose.stack.yaml` | Provider `ddl-auto: update` (trái quy ước). `BOOKING_DEPOSIT_RATE` mặc định 0.30 (`BookingService/src/main/resources/application.yaml:105`). Docker: `BOOKING_CARE_CATALOG_URL` và `PROVIDER_MEDIA_URL` không được override → sai trong mạng container. |
| `git log --oneline -50`, `git branch -a`, `git ls-remote` | Chỉ có `dev`, `main` (local + origin). **Không có commit/branch nào về Payment** (`git log --all -i --grep=pay` rỗng). |
| Test (`*/src/test`) | Identity 59+ test, CareRecipient ~55, Booking 66, Provider 24, Gateway 14, Catalog 3 (context test `@Disabled`), Media 3. Một số test **khẳng định hành vi sai** (vd customer gọi `deposit-paid` được — `BookingService/src/test/java/mss301/bookingservice/BookingApiTests.java:781`). |
| `scripts/e2e/Run-E2E.ps1`, `e2e.py` | Lỗi thời: dùng `*_INTERNAL_SECRET`, path `/api/v1/center/bookings`, `/caregiver/occurrences`; không có Catalog/Media → nhiều khả năng fail nếu chạy lại. |
| `../FE-careconnect` | React 19 + React Router 8 (SPA web, không có app mobile). Client sinh bằng Orval từ `openapi/openapi.yaml` — **chỉ có contract IdentityService** (24 operation) và dùng **path cũ không `/api/v1`** (`/users/register`, `/users/forgot-password`…). Mọi màn hình Booking / Payment / Caregiver / Center Manager / Admin (trừ accounts, roles) dùng dữ liệu mock (`*-preview.ts`, `mock-*.ts`). Trang cọc giả lập thanh toán bằng `setTimeout` (`app/features/customer/booking-deposit/hooks/use-booking-deposit.ts:30`). Không có realtime. |
| `.agents/teamwork/**` | Ghi chú của đợt refactor 2026-10-18 (gộp controller theo aggregate, bỏ `/internal`). Chỉ dùng để hiểu bối cảnh; code là nguồn sự thật. |

**Phần việc dở dang rút ra:** đợt refactor 2026-10-18/19 đã bỏ cơ chế `X-Internal-Secret` nhưng **chưa thay bằng danh tính service**: các endpoint trước đây là "internal" giờ chỉ còn `isAuthenticated()` → hàng loạt lỗ hổng ở §7. Tài liệu từng service chưa cập nhật theo refactor.

---

## 1. Các điểm nghiệp vụ chưa rõ và quyết định đã chốt (2026-10-21)

### 1.1 Câu hỏi và quyết định

| # | Vấn đề | Hiện trạng code | Quyết định chốt |
|---|---|---|---|
| D1 | Thứ tự duyệt ↔ thanh toán | `PENDING → ACCEPT → AWAITING_DEPOSIT → PAY_DEPOSIT → CONFIRMED` (`BK/enums/BookingAction.java:17-35`) — ngược với prompt (`PENDING_PAYMENT` trước) | **Giữ thứ tự của code**: PENDING → (manager duyệt + gán caregiver) → AWAITING_DEPOSIT (hạn 24h) → CONFIRMED → IN_PROGRESS → COMPLETED. Phiên thanh toán khi khách bấm "Thanh toán" hết hạn sau **15 phút**; `deposit_due_at` vẫn 24h. Tên trạng thái giữ `AWAITING_DEPOSIT` (không đổi thành `PENDING_PAYMENT`). |
| D2 | Cọc / toàn bộ, phần còn lại | Cọc = `subtotal × BOOKING_DEPOSIT_RATE` (0.30 hardcode qua env, `BK/config/BookingProperties.java:43`) | Mọi số là cấu hình **PlatformPolicy** (Admin sửa, có hiệu lực theo ngày), **snapshot vào booking lúc khách đặt cọc**; bỏ env 0.30. Cọc 30% tổng, phân bổ theo buổi `deposit_i = round(30% × price_i)` (làm tròn đồng, phần lệch dồn buổi cuối). REMAINING = Σ giá buổi COMPLETED (và CUSTOMER_NO_SHOW) − cọc tương ứng, trả online sau khi booking COMPLETED, hạn 48h; quá hạn → nợ, chặn tạo booking mới. Buổi hủy / MISSED do caregiver không tính vào REMAINING. Bảng phí hủy: §1.2. |
| D3 | Hủy theo booking hay từng buổi | Chỉ hủy cả booking; khách chỉ hủy được khi chưa buổi nào bắt đầu (`BSI:444-454`) | Hỗ trợ **cả hai**. Hủy booking = áp bảng phí cho từng buổi chưa diễn ra rồi cộng. Có **preview-cancel** trả số tiền hoàn dự kiến trước khi khách xác nhận. |
| D4 | Đánh giá theo booking hay buổi | Chưa có | Mỗi cặp **(booking, caregiver)** đánh giá **1 lần**, khi booking ở trạng thái kết thúc và caregiver đó có ≥ 1 buổi COMPLETED. Cập nhật điểm trung bình caregiver. |
| D5 | Đổi caregiver giữa chừng | `caregiver-access` tính theo caregiver **cấp booking** (`BK/repository/BookingRepository.java:55-69`) | Review và thống kê tính theo `booking_occurrences.caregiver_id` của từng buổi COMPLETED. Quyền xem hồ sơ người bệnh tính **theo từng buổi**. |
| D6 | Ranh giới service | Chưa có Payment / Review / Complaint / Notification | **Tạo `PaymentService`** (port 8087, dải lỗi 6000–6999): Payment, Transaction, PlatformPolicy, Settlement, doanh thu. **Không** tạo FeedbackService / NotificationService: Review (bảng `feedbacks`), Complaint (bảng `complaints` tách luồng escalate), Notification (bảng `notifications`, polling + SSE) **nằm trong BookingService**. CareSession / CareTask / CareReport nằm trong BookingService. MediaService đổi dải lỗi sang **7000–7999**. |
| D7 | Danh tính service-to-service | Đã gỡ `X-Internal-Secret`; Feign chỉ chuyển tiếp token người dùng → job/IPN không có token | Identity cấp **token service** (role `SERVICE`, kiểu client-credentials). Endpoint chỉ dành cho service dùng `hasRole('SERVICE')`. Webhook IPN VNPay xác thực bằng **chữ ký HMAC-SHA512** của cổng, không cần token. |
| D8 | Cổng thanh toán | Không có code | **VNPay sandbox** (thanh toán, IPN, return URL, API hoàn tiền, querydr). |
| D9 | Xác minh chứng chỉ | `VerificationStatus` PENDING/VERIFIED/REJECTED, chỉ ADMIN duyệt (`PV/controller/CertificateController.java:71-78`) | Thêm `CENTER_VERIFIED`, đổi `VERIFIED` → `ADMIN_VERIFIED`: PENDING → CENTER_VERIFIED (manager) → ADMIN_VERIFIED (admin); REJECTED từ cả hai bước. Customer chỉ thấy bản `ADMIN_VERIFIED`. |
| D10 | Trạng thái trung tâm | `CenterStatus` PENDING/ACTIVE/INACTIVE/SUSPENDED/REJECTED/ARCHIVED, chuyển tùy ý (`PV/service/Impl/CareCenterServiceImpl.java:118-135`) | Giữ enum của code; **`SUSPENDED` = LOCKED**. Trung tâm do Admin tạo (không có luồng tự đăng ký công khai). Khóa trung tâm → khóa tài khoản manager + caregiver bên Identity và hủy (hoàn 100%) các buổi chưa diễn ra. |
| D11 | Lịch làm việc / ngày nghỉ | Provider có `work_shifts → shift_registrations → caregiver_schedules` với vòng đời SCHEDULED→IN_PROGRESS→COMPLETED trùng lặp với occurrence | `caregiver_schedules` chỉ là **lịch rảnh** (bỏ IN_PROGRESS/COMPLETED); thêm bảng `caregiver_time_offs`. Booking chỉ cho đặt khi buổi nằm trong lịch rảnh và không trùng ngày nghỉ. |
| D12 | Cách tính giá | HOUR: `ceil(tổng phút mọi buổi / duration)`; chọn price plan "đầu tiên" không `ORDER BY` (`CC/repository/PricePlanRepository.java:19`) | Khách chọn **`pricePlanId`** cụ thể cho từng dịch vụ. Giá buổi = Σ_dịch vụ (đơn giá × số đơn vị **của buổi đó**); tổng = Σ giá buổi. |
| D13 | Ai sửa giá dịch vụ trung tâm | Chỉ CENTER_MANAGER (`CC/service/Impl/PricePlanServiceImpl.java:98-102`) | Chỉ **CENTER_MANAGER**; ADMIN quản lý danh mục chuẩn + chính sách nền tảng. |
| D14 | Nhóm / tiến độ Payment | Không có code Payment ở bất kỳ nhánh nào | Thành viên [Người A], [Người B], [Người C]. [Người A] nhận Payment, **chưa bắt đầu**, không có nhánh local. |

### 1.2 Bảng mức phí hủy / hoàn tiền (chốt — nguồn cho PAY-09)

Thời gian tính từ lúc hủy đến giờ bắt đầu của **từng buổi** bị hủy; phí tính trên **giá trị buổi đó**, không bao giờ vượt số tiền khách đã trả cho buổi đó.

| Trường hợp | Ai hủy | Khách mất | Hoàn lại |
|---|---|---|---|
| PENDING (trung tâm chưa duyệt) | Khách | 0% | Chưa trả tiền, hủy miễn phí |
| AWAITING_DEPOSIT (đã duyệt, chưa cọc) | Khách | 0% | Chưa trả tiền, hủy miễn phí |
| AWAITING_DEPOSIT quá 24h | Hệ thống | 0% | Tự chuyển EXPIRED, giải phóng lịch |
| CONFIRMED, hủy trong 1h sau khi cọc **và** buổi đầu còn ≥ 24h | Khách | 0% | 100% (thời gian cân nhắc) |
| CONFIRMED, buổi còn ≥ 72h | Khách | 0% | 100% cọc của buổi đó |
| CONFIRMED, buổi còn 24h → < 72h | Khách | 10% giá trị buổi | Cọc buổi đó − 10% |
| CONFIRMED, buổi còn < 24h | Khách | 30% giá trị buổi (= toàn bộ cọc buổi) | 0% |
| Buổi đã bắt đầu (có Session) | Khách | Không được hủy | Buổi tính đủ tiền |
| CUSTOMER_NO_SHOW (caregiver báo kèm ghi chú, manager xác nhận) | — | 100% giá trị buổi | 0%, phần còn lại cộng vào REMAINING |
| Trung tâm từ chối hoặc hủy, bất kể thời điểm | Trung tâm | 0% | 100% số đã trả cho các buổi chưa diễn ra |
| MISSED do caregiver, trung tâm không thay được người | — | 0% | 100% cọc buổi đó, buổi không tính tiền |
| Trung tâm / tài khoản trung tâm bị khóa | Admin | 0% | 100% các buổi chưa diễn ra |
| Khiếu nại được Admin chấp nhận | Admin | Theo quyết định | Admin nhập số tiền hoàn, ≤ số đã trả |

Quy tắc áp dụng: hủy cả booking = cộng từng buổi; có preview-cancel; phí giữ lại chia trung tâm ↔ nền tảng theo **% hoa hồng snapshot**; hoàn tiền là bản ghi REFUND riêng `REFUND_PENDING → REFUNDED`, gọi API hoàn tiền VNPay, idempotent theo (booking/occurrence, lý do).

### 1.3 Điểm còn mở (đã đặt mặc định trong WORK_PLAN, cần nhóm xác nhận khi review)

| # | Điểm | Mặc định trong kế hoạch |
|---|---|---|
| O1 | Cửa sổ bắt đầu ca | Từ 30 phút trước `start_at` đến `end_at` (theo prompt §2.5). Cấu hình `BOOKING_SESSION_START_WINDOW_MINUTES`. |
| O2 | Mốc đánh MISSED | Quá `start_at + 30 phút` mà chưa có Session → MISSED (cấu hình `BOOKING_MISSED_GRACE_MINUTES`). |
| O3 | Sửa care report sau khi gửi | Không cho sửa (report SUBMITTED là bất biến); muốn bổ sung thì caregiver thêm ghi chú mới qua manager — chưa làm. |
| O4 | Khiếu nại: thời hạn mở / escalate | Không giới hạn thời gian; customer escalate được khi complaint đã RESOLVED cấp 1 nhưng không đồng ý. |
| O5 | Kỳ đối soát | Theo tháng (cấu hình trong PlatformPolicy `settlement_cycle = MONTHLY`). |
| O6 | Ghi nhận doanh thu | Doanh thu (và phần chia trung tâm) chỉ ghi khi tiền đã thu và dịch vụ đã cung cấp / phí hủy đã giữ lại — xem PAY-13. |
| O7 | Lọc giá khi tìm caregiver | Giá thuộc dịch vụ trung tâm, không thuộc caregiver → lọc khoảng giá áp ở **danh sách trung tâm** (SRCH-02), không ở danh sách caregiver trong cùng trung tâm. |

### 1.4 Mâu thuẫn tài liệu repo ↔ nghiệp vụ prompt

| Mâu thuẫn | Xử lý |
|---|---|
| Prompt: `PENDING_PAYMENT → CONFIRMED`; code/plan Booking: manager duyệt trước rồi mới cọc | Đã chốt D1 (giữ code). |
| Prompt: Center `PENDING_APPROVAL/ACTIVE/LOCKED/REJECTED`; code: 6 trạng thái | D10: giữ code, SUSPENDED = LOCKED. |
| Prompt: Occurrence có `ACKNOWLEDGED`; code: `OccurrenceStatus` không có (`BK/enums/OccurrenceStatus.java`) | Thêm `ACKNOWLEDGED` và `CUSTOMER_NO_SHOW` (OPS-01, OPS-06). |
| Prompt: Credential `CENTER_VERIFIED → ADMIN_VERIFIED`; code: chỉ admin duyệt | D9. |
| Prompt: Caregiver có lịch làm việc / ngày nghỉ; code Provider: ca do trung tâm công bố, không có ngày nghỉ | D11. |
| `AGENTS.md`: "tỷ lệ cọc chưa chốt, không tự đặt số"; code: hardcode 0.30 | D2: chuyển vào PlatformPolicy. |
| `AGENTS.md` §1 "Review & complaint (chưa có service)", "Notification (chưa có service)" | D6: đưa vào BookingService — cần cập nhật bảng domain trong `AGENTS.md` (INF-01). |
| `MediaService/AGENTS.md` dải 5000–5999 trùng Booking | D6: Media → 7000–7999 (MED-01). |

---

## 2. Kiến trúc và tech stack

| Hạng mục | Hiện trạng |
|---|---|
| Ngôn ngữ / framework | Java 21, Spring Boot 4.1.1, Spring Cloud BOM; mỗi service là Maven project độc lập (`mvnw`) |
| Kiến trúc | Microservice, layer `controller → service (interface) → service/Impl → repository`, MapStruct mapper, `APIResponse<T>` envelope, `AppException` + `ErrorCode` |
| ORM / DB | Spring Data JPA (Hibernate), PostgreSQL 17 riêng từng service; schema áp tay từ `schema/*.sql`, `ddl-auto: validate` (riêng Provider đang `update` — `ProviderService/src/main/resources/application.yaml:17`) |
| Auth | IdentityService phát JWT RS256 (JWKS `/.well-known/jwks.json`), mọi service là OAuth2 Resource Server (kiểm iss, aud `careconnect-api`, `token_type=access`, sub UUID). Phân quyền `@PreAuthorize(hasRole…)`; manager scope bằng claim `center_id` (Booking, Catalog) hoặc `care_centers.manager_user_id` (Provider — `PV/security/ProviderAuthorization.java:24-35`) → **2 nguồn sự thật** |
| Phiên đăng nhập | Redis của Identity: session store, refresh **opaque** lưu hash, blacklist access, rate limit đăng nhập (`ID/service/Impl/RedisTokenStore.java`, `RedisSessionStore.java`, `RedisRateLimiter.java`) |
| Service-to-service | OpenFeign qua ApiGateway, **chuyển tiếp token người dùng** (`*/client/FeignAuthForwardingInterceptor.java`), Resilience4j circuit breaker, ErrorDecoder + FallbackFactory. **Không có danh tính service** sau khi bỏ `X-Internal-Secret` |
| Gateway | Spring Cloud Gateway Server MVC, port 8080, route `/api/v1/**` tới 6 service, không cắt prefix, CORS tập trung, thay `X-User-*` (`GW/config/GatewayPaths.java`, `GW/security/SecurityConfig.java`). Không rate limiting |
| Realtime | **Không có** (không WebSocket/SSE/STOMP/FCM ở BE lẫn FE) |
| Job scheduler | Chỉ `BK/scheduler/BookingExpiryScheduler.java` (`@Scheduled fixedDelay` 5 phút, EXPIRE booking quá hạn duyệt/cọc) |
| Message broker / outbox | **Không có** (chỉ comment TODO tại `BSI:666`, `CR/service/Impl/RecipientManagerServiceImpl.java:38-40`) |
| File | MediaService → Cloudflare R2 (AWS SDK S3), presigned GET 300s; chỉ ProviderService đang dùng |
| Email | Brevo HTTP API trong Identity (chỉ OTP) |
| Thanh toán | **Không có** — chỉ stub `POST /api/v1/bookings/{id}/deposit-paid` |
| Test | JUnit 5 + MockMvc, H2 (+ alias PG function), stub HTTP bằng `HttpServer`; chạy được trên PostgreSQL thật bằng cờ `-D<service>.testing.postgres=true` |
| Docker | `compose.yaml` include `<Service>/compose.stack.yaml` cho 7 service + hạ tầng; `start.bat` / `stop.bat` |

| Service | Port | DB (dev) | Redis | Dải ErrorCode | Controller |
|---|---|---|---|---|---|
| ApiGateway | 8080 | — | — | 9000–9099 | — |
| IdentityService | 8081 | 5441 | 6381 | 1000–1999 | Authentication, User, Role, Permission, Jwks |
| CareRecipientService | 8082 | 5442 | — | 2000–2999 | CareRecipient |
| CareCatalogService | 8083 | 5443 | — | 3000–3999 | CatalogItem, CenterOffering, PricePlan |
| ProviderService | 8084 | 5444 | — | 4000–4999 | CareCenter, Caregiver, Certificate, WorkShift, ProviderMedia |
| BookingService | 8085 | 5445 | — | 5000–5999 | Booking (một controller cho cả `/bookings` và `/booking-occurrences`) |
| MediaService | 8086 | 5446 | — | **5000–5999 (trùng Booking)** | Media |
| *PaymentService (mới)* | *8087* | *5447* | — | *6000–6999* | — |

---

## 3. Entity / bảng: đã có, thiếu, thiếu trường

### 3.1 Bảng hiện có

| Service | Bảng (entity) | Enum trạng thái | Ghi chú / thiếu trường so với nghiệp vụ |
|---|---|---|---|
| Identity | `users` (`User`) | `UserStatus` ACTIVE, LOCKED | Thiếu: lý do khóa (`status_reason`), loại tài khoản SERVICE. `center_id` không FK, không đối chiếu Provider |
| Identity | `roles`, `permissions`, `user_roles`, `role_permissions`, `identity_audit` | `RoleCode` CUSTOMER, CAREGIVER, CENTER_MANAGER, ADMIN; `AuditAction` (12) | Thiếu role `SERVICE`, bảng `service_clients`; permission chỉ có 5 mã Identity (`ID/security/Permissions.java`) |
| CareRecipient | `care_recipients` | `RecipientStatus` ACTIVE, INACTIVE, ARCHIVED | Đủ cho Patient (bệnh nền, dị ứng, tiền sử, ghi chú đặc biệt, địa chỉ + tọa độ). Bệnh/dị ứng là text tự do. Một địa chỉ/hồ sơ |
| CareRecipient | `medications`, `emergency_contacts`, `recipient_managers` | `RecipientAccessLevel` OWNER, EDITOR, VIEWER; `MedicationRoute`, `RelationshipType`… | Đủ. `recipient_managers` xóa vật lý (không BaseEntity) |
| CareCatalog | `care_services` (`CatalogItem`) = **StandardService** | `ServiceStatus` ACTIVE, INACTIVE | Thiếu bảng **ServiceCategory** (category là chuỗi tự do), không unique tên |
| CareCatalog | `center_services` (`CenterOffering`) = **CenterService** | `ServiceStatus` | Đủ trường; unique (center, care_service) chưa xóa |
| CareCatalog | `price_plans` (`PricePlan`) | `PricingUnit` HOUR, DAY, SHIFT | Đủ; thiếu ràng buộc một plan active/đơn vị, `duration_minutes` ↔ đơn vị |
| Provider | `care_centers` (`CareCenter`) | `CenterStatus` PENDING, ACTIVE, INACTIVE, SUSPENDED, REJECTED, ARCHIVED | Thiếu: tỉnh/quận có cấu trúc (lọc khu vực), `rating_average` trung tâm. Email unique toàn cục kể cả bản đã xóa (`ProviderService/schema/provider.sql:29`) |
| Provider | `caregiver_profiles` (`CaregiverProfile`) | `CaregiverStatus` ACTIVE, INACTIVE, SUSPENDED, ARCHIVED; `VerificationStatus` PENDING, VERIFIED, REJECTED; `Gender` | Thiếu: kỹ năng (→ `caregiver_services`), avatar riêng. `rating_average/count` có nhưng **không có nguồn cập nhật** |
| Provider | `certificates` (`Certificate`) | `VerificationStatus` | Thiếu: bước `CENTER_VERIFIED`, `center_verified_by/at`, loại bằng cấp |
| Provider | `work_shifts`, `shift_registrations`, `caregiver_schedules` | `ShiftStatus`, `RegistrationStatus`, `ScheduleStatus` SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED | `caregiver_schedules` có vòng đời chồng lấn occurrence (D11). Không có ngày nghỉ |
| Provider | `provider_media` (`ProviderMedia`) | `MediaOwnerType`, `MediaFileType` | Không BaseEntity, `OffsetDateTime` |
| Booking | `bookings` (`Booking`) | `BookingStatus` PENDING, AWAITING_DEPOSIT, CONFIRMED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED, EXPIRED | Thiếu: `policy_snapshot`, `deposit_payment_id`, `deposit_paid_at`, `started_at`, `completed_at`, trạng thái REMAINING. `cancelled_by` chỉ lưu UUID, không lưu bên hủy |
| Booking | `booking_occurrences` (`BookingOccurrence`) | `OccurrenceStatus` SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED, MISSED | Thiếu: `ACKNOWLEDGED`, `CUSTOMER_NO_SHOW`, `price_amount`, `deposit_amount`, `acknowledged_at`, `cancelled_at/by_party`, `missed_reason`, `no_show_*`. `actual_start_at/actual_end_at` có cột nhưng chưa API nào ghi |
| Booking | `booking_details` (`BookingDetail`) = **BookingService** (snapshot giá) | `UnitType` HOUR, DAY, SHIFT | Có snapshot tên/đơn vị/đơn giá; `unit_price` NULL khi cờ catalog tắt; thiếu `units_per_occurrence` |
| Booking | `booking_status_history` | — | Không có API đọc; không ghi việc đổi caregiver |
| Media | `media_files` (`MediaFile`) | `MediaStatus` ACTIVE, DELETE_PENDING, DELETED; `MediaPurpose` (có `CARE_REPORT_IMAGE/VIDEO`, `FEEDBACK_ATTACHMENT`) | Thiếu kiểm soát sở hữu |

### 3.2 Bảng chưa có (so với mô hình §2.2 của prompt)

| Mô hình nghiệp vụ | Trạng thái | Nơi đặt (theo D6) |
|---|---|---|
| ServiceCategory | ❌ chưa có (chuỗi `category`) | Catalog — **không làm** trong kế hoạch này (P2 ngoài phạm vi), giữ chuỗi tự do + lọc theo `category` |
| CaregiverService (N-N) | ❌ | Provider `caregiver_services` (PRV-01) |
| Caregiver time-off | ❌ | Provider `caregiver_time_offs` (PRV-02) |
| CareSession | ❌ | Booking `care_sessions` (OPS-02) |
| CareTask | ❌ | Booking `care_tasks` (OPS-02/03) |
| CareReport (+ ảnh) | ❌ | Booking `care_reports`, `care_report_media` (OPS-05) |
| Payment / Transaction | ❌ | Payment `payments`, `payment_transactions` (PAY-05/06) |
| Refund | ❌ | Payment `refunds` (PAY-10) |
| Remaining invoice / nợ | ❌ | Payment `remaining_invoices` (PAY-11) |
| PlatformPolicy | ❌ (env 0.30) | Payment `platform_policies` (PAY-02) |
| Snapshot tài chính booking | ❌ | Payment `booking_financials`, `occurrence_financials`; Booking `bookings.policy_snapshot`, `booking_occurrences.deposit_amount` (PAY-03/04/08) |
| Ledger hoa hồng | ❌ | Payment `ledger_entries` (PAY-13) |
| Settlement | ❌ | Payment `settlements` (PAY-14) |
| Review | ❌ | Booking `feedbacks` (FB-01) |
| Complaint | ❌ | Booking `complaints`, `complaint_events` (FB-02) |
| Notification | ❌ | Booking `notifications` (NTF-01) |
| Service client (token service) | ❌ | Identity `service_clients` (IAM-01) |

---

## 4. Endpoint hiện có và mức độ hoàn thiện

Ký hiệu mức độ: **Đủ** · **Thiếu validate** · **Thiếu nghiệp vụ** · **Stub-TODO** · 🔴 = lỗ hổng phân quyền (chi tiết §7).

### 4.1 IdentityService — `ID/controller/`

| Method | Path | Handler | Role | Mô tả | Mức độ |
|---|---|---|---|---|---|
| GET | `/api/v1/auth/csrf` | `AuthenticationController.csrf` | public | Lấy CSRF token | Đủ |
| GET | `/api/v1/auth/me` | `.me` | authenticated | Trùng `/users/me` | Đủ (thừa) |
| GET | `/api/v1/auth/sessions` | `.sessions` | authenticated | Danh sách phiên Redis | Đủ (DTO dựng tay, `AuthenticationServiceImpl.java:229-237`) |
| POST | `/api/v1/auth/register`, `/register/resend-otp`, `/register/verify`, `/verify-email` | `.register/.resend/.verify/.verifyEmail` | public | Đăng ký CUSTOMER bằng OTP | Đủ |
| POST | `/api/v1/auth/forgot-password`, `/reset-password` | `.forgotPassword/.resetPassword` | public | Quên / đặt lại mật khẩu | 🔴 Thiếu nghiệp vụ: không thu hồi refresh (SEC-05) |
| POST | `/api/v1/auth/login` | `.login` | public | Login hợp nhất web/mobile (`X-Client-Type`) | Đủ (B6: tin `X-Forwarded-For`) |
| POST | `/api/v1/auth/refresh` | `.refresh` | public | Xoay refresh opaque | 🔴 Không nguyên tử, không kiểm `authz_version`, reuse không thu hồi (SEC-05) |
| POST | `/api/v1/auth/logout` (`allSessions` thay `/logout-all`) | `.logout` | public | Đăng xuất | Đủ |
| POST | `/api/v1/auth/mobile/login`, `/mobile/refresh` (`/mobile/logout` bỏ 2026-10-22, dùng `/auth/logout`) | `.mobile*` | public | Legacy, header `Deprecation` | Đủ |
| DELETE | `/api/v1/auth/sessions/{sessionId}` | `.revokeSession` | authenticated + owner | Hủy một phiên | Thiếu nghiệp vụ: access của phiên vẫn dùng được (SEC-06) |
| GET | `/.well-known/jwks.json` | `JwksController.get` | public | JWK Set | Đủ |
| GET | `/api/v1/users/me` | `UserController.me` | authenticated | Hồ sơ mình | Đủ |
| PUT | `/api/v1/users/me` | `.update` | authenticated | Sửa fullName/phone | Đủ |
| POST | `/api/v1/users/me/change-password` | `.changePassword` | authenticated | Đổi mật khẩu | 🔴 Không thu hồi refresh (SEC-05) |
| GET | `/api/v1/users/{id}` | `.get` (`UserController.java:52`) | **không có** | Tra cứu user | 🔴 SEC-04 |
| GET | `/api/v1/users/by-email` | `.getByEmail` (`:57`) | **không có** | Tra theo email | 🔴 SEC-04 (dò tài khoản) |
| POST | `/api/v1/users/batch` | `.listBatch` (`:62`) | **không có** | Batch ≤ 100 | 🔴 SEC-04 |
| GET | `/api/v1/users` | `.list` | `user:read` + ADMIN/MANAGER | Danh sách user | Thiếu nghiệp vụ: không lọc role/status/keyword/center, không trả tổng số (`UserServiceImpl.java:194-214`) |
| POST | `/api/v1/users` | `.create` | ADMIN / CENTER_MANAGER | Provision CUSTOMER/MANAGER/CAREGIVER | Đủ (không xác minh `centerId` ở Provider) |
| PUT | `/api/v1/users/{id}` | `.update` | `user:manage` | Sửa hồ sơ trong phạm vi | Đủ |
| PATCH | `/api/v1/users/{id}/status` | `.updateStatus` | `user:manage` | Khóa / mở | Thiếu nghiệp vụ: không lưu lý do; không khóa theo trung tâm |
| DELETE | `/api/v1/users/{id}` | `.delete` | `user:manage` | Xóa mềm | Đủ |
| PUT / DELETE | `/api/v1/users/{id}/roles/{roleId}` | `.assign/.revoke` | ADMIN + `role:manage` | Gán / thu hồi role | Thiếu: thu hồi không tăng `authz_version` (`UserServiceImpl.java:323-340`) |
| GET / POST | `/api/v1/roles` | `RoleController.list/.create` | ADMIN + `role:manage` | Role | Đủ |
| PUT | `/api/v1/roles/{id}/permissions` | `.updatePermissions` | ADMIN + `role:manage` | Gán permission cho role bổ sung | Đủ |
| GET | `/api/v1/permissions` | `PermissionController.list` | ADMIN + `role:manage` | Danh sách permission | Đủ |

### 4.2 CareRecipientService — `CR/controller/CareRecipientController.java` (base `/api/v1/care-recipients`)

| Method | Path | Handler (dòng) | Role | Mô tả | Mức độ |
|---|---|---|---|---|---|
| GET | `` | `list` | CUSTOMER, ADMIN | CUSTOMER: hồ sơ tôi quản lý; ADMIN: toàn hệ thống (+ `createdBy`, `includeDeleted`). Lọc + phân trang (2026-10-22: gộp `/admin`) | Đủ |
| GET | `/{id}` | `get` | CUSTOMER (VIEWER), ADMIN, CAREGIVER | CUSTOMER/ADMIN: chi tiết + thuốc + liên hệ; CAREGIVER: `careSnapshot` khi Booking cho phép (token SERVICE) (2026-10-22: gộp `/admin/{id}`, `/caregiver/{id}`) | Đủ |
| GET | `/{id}/snapshot` | `getSnapshot` (72) | **isAuthenticated** | Snapshot y tế cho Booking | 🔴 SEC-02 |
| GET | `/{id}/access` | `checkAccess` (78) | **isAuthenticated** | Kiểm quyền user bất kỳ | 🔴 SEC-02 |
| POST | `/batch` | `listBatch` (140) | **isAuthenticated** | Tóm tắt nhiều hồ sơ | 🔴 SEC-02 |
| GET/POST/PUT/PATCH/DELETE | `/{rid}/medications/**` | (87, 96, 146, 184, 215, 261) | CUSTOMER (VIEWER/EDITOR) | CRUD thuốc, bật/tắt | Đủ |
| GET/POST/PUT/PATCH/DELETE | `/{rid}/emergency-contacts/**` | (105, 113, 155, 194, 225, 270) | CUSTOMER | CRUD liên hệ, đặt liên hệ chính | Đủ |
| GET/POST/PATCH/DELETE | `/{rid}/managers/**` | (122, 164, 234, 244, 279) | CUSTOMER (OWNER) | Người thân cùng quản lý | Thiếu nghiệp vụ: thêm thẳng, không lời mời |
| POST | `` | `create` (132) | CUSTOMER | Tạo hồ sơ + OWNER | Đủ |
| PUT | `/{id}` | `update` (175) | CUSTOMER (EDITOR) | Sửa hồ sơ (version) | Đủ |
| PATCH | `/{id}/status` | `updateStatus` (206) | CUSTOMER (OWNER) | ACTIVE ↔ INACTIVE | Thiếu nghiệp vụ: INACTIVE không chặn sửa/đặt lịch |
| DELETE | `/{id}` | `delete` (255) | CUSTOMER (OWNER) | ARCHIVED + xóa mềm | Đủ |
| — | *(không có)* | — | CENTER_MANAGER | Xem hồ sơ người bệnh của booking thuộc trung tâm | ❌ |

### 4.3 CareCatalogService — `CC/controller/`

| Method | Path | Handler | Role | Mô tả | Mức độ |
|---|---|---|---|---|---|
| GET | `/api/v1/catalog-items` (alias `/care-services`) | `CatalogItemController.listAll` | authenticated | Mọi item kể cả INACTIVE | Thiếu nghiệp vụ: không lọc/phân trang |
| GET | `/api/v1/catalog-items/{id}` | `.getById` | authenticated | Chi tiết | Đủ |
| POST | `/api/v1/catalog-items` | `.create` | ADMIN | Tạo dịch vụ chuẩn | Thiếu validate (`@Size`, trùng tên) |
| PUT | `/api/v1/catalog-items/{id}` | `.update` | ADMIN | Sửa + status | Thiếu nghiệp vụ: vô hiệu không ảnh hưởng offering |
| DELETE | `/api/v1/catalog-items/{id}` | `.delete` | ADMIN | Xóa mềm | Thiếu nghiệp vụ: không chặn khi đang được dùng |
| GET | `/api/v1/center-services/center/{centerId}` | `CenterOfferingController.listByCenter` | authenticated | Gồm cả INACTIVE | Thiếu nghiệp vụ |
| GET | `/api/v1/center-services/{id}` | `.getById` | authenticated | | Đủ |
| POST | `/api/v1/center-services` | `.create` | CENTER_MANAGER | Chọn item từ danh mục | ⚠️ Cho chọn item INACTIVE (`CenterOfferingServiceImpl.java:116-119`) |
| PUT | `/api/v1/center-services/{id}` | `.update` | CENTER_MANAGER | | như trên |
| DELETE | `/api/v1/center-services/{id}` | `.delete` | CENTER_MANAGER | Xóa mềm, không xử lý plan con | Thiếu nghiệp vụ |
| POST | `/api/v1/center-services/quote` | `.quote` | authenticated | Báo giá cho Booking | ⚠️ Plan "đầu tiên" không `ORDER BY`; controller dựng `APIResponse` (`CenterOfferingController.java:67`) |
| GET | `/api/v1/price-plans/center-offering/{id}` (alias `/center-service/{id}`) | `PricePlanController.listByCenterOffering` | authenticated | Gồm cả inactive | Thiếu nghiệp vụ |
| GET | `/api/v1/price-plans/{id}` | `.getById` | authenticated | | Đủ |
| POST / PUT | `/api/v1/price-plans`, `/{id}` | `.create/.update` | CENTER_MANAGER | Bảng giá | ⚠️ Không gửi `isActive` → vi phạm NOT NULL → 400 (`PricePlanMapperImpl` sinh `setIsActive(null)`) |
| DELETE | `/api/v1/price-plans/{id}` | `.delete` | CENTER_MANAGER | Xóa mềm | Đủ |

### 4.4 ProviderService — `PV/controller/`

| Method | Path | Handler | Role | Mô tả | Mức độ |
|---|---|---|---|---|---|
| GET | `/api/v1/care-centers` | `CareCenterController.list` (40) | ADMIN, CENTER_MANAGER | Lọc status/keyword, phân trang | 🔴 Manager xem mọi trung tâm, kể cả đã xóa (SEC-08) |
| GET | `/api/v1/care-centers/{centerId}` | `.get` (52) | authenticated | Chi tiết | ⚠️ Không lọc `deleted_at` (`CareCenterServiceImpl.java:84`), lộ email/managerUserId |
| GET | `/api/v1/care-centers/{centerId}/schedules` | `.listSchedules` (59) | ADMIN, MANAGER | Lịch caregiver trung tâm | Đủ (thiếu lọc ngày) |
| POST | `/api/v1/care-centers` | `.create` (72) | ADMIN | Tạo trung tâm PENDING | Đủ; logic gọi Identity nằm trong controller (`:77-79`) |
| PUT | `/api/v1/care-centers/{centerId}` | `.update` (85) | ADMIN | Sửa + đổi manager | ⚠️ `managerUserId=null` gỡ manager âm thầm |
| PATCH | `/api/v1/care-centers/{centerId}/status` | `.updateStatus` (99) | ADMIN | Đổi trạng thái | Thiếu nghiệp vụ: không state machine, không lan sang tài khoản/booking |
| PATCH | `/api/v1/care-centers/schedules/{id}/status` | `.updateScheduleStatus` (108) | ADMIN, MANAGER | Đổi/hủy lịch | Trùng chức năng, sai aggregate |
| DELETE | `/api/v1/care-centers/{centerId}` | `.delete` (120) | ADMIN | ARCHIVED | Thiếu: không kiểm booking/caregiver đang hoạt động |
| GET | `/api/v1/caregivers` | `CaregiverController.catalog` (41) | authenticated | VERIFIED + ACTIVE + center ACTIVE; lọc centerId, keyword | Thiếu nghiệp vụ: không lọc dịch vụ/giới tính/kinh nghiệm/rating/lịch trống; sort cố định |
| GET | `/api/v1/caregivers/centers/{centerId}` | `.listManaged` (52) | ADMIN, MANAGER | Caregiver của trung tâm | ⚠️ Truyền cả `verification` và `status` thì bỏ qua `status` (`CaregiverServiceImpl.java:90-96`) |
| GET | `/api/v1/caregivers/me`, `/me/schedules` | `.myProfile/.mySchedules` (65, 72) | CAREGIVER | Hồ sơ / lịch của tôi | Đủ |
| GET | `/api/v1/caregivers/{caregiverId}`, `/by-user/{userId}` | `.get/.getByUser` (82, 89) | authenticated | Chi tiết | 🔴 Mọi user xem hồ sơ chưa xác minh, lý do từ chối (SEC-08) |
| POST | `/api/v1/caregivers/centers/{centerId}` | `.create` (98) | ADMIN, MANAGER | Tạo hồ sơ cho user CAREGIVER | Thiếu: không đối chiếu `center_id` Identity |
| PUT | `/api/v1/caregivers/{caregiverId}` | `.update` (110) | ADMIN, MANAGER | Sửa + `caregiverStatus` | 🔴 Manager đặt ACTIVE cho hồ sơ chưa xác minh (SEC-08) |
| PATCH | `/api/v1/caregivers/{id}/verification` | `.verify` (121) | ADMIN | Duyệt hồ sơ | Thiếu nghiệp vụ: không kiểm trạng thái hiện tại (kích hoạt lại ARCHIVED/SUSPENDED) |
| PATCH | `/api/v1/caregivers/schedules/{id}/status` | `.updateScheduleStatus` (130) | CAREGIVER, MANAGER, ADMIN | SCHEDULED→IN_PROGRESS→COMPLETED | Chồng lấn occurrence (D11) |
| DELETE | `/api/v1/caregivers/{caregiverId}` | `.archive` (142) | ADMIN, MANAGER | ARCHIVED | Thiếu: không hủy lịch/booking |
| GET | `/api/v1/certificates/caregivers/{caregiverId}` | `CertificateController.list` (32) | ADMIN, MANAGER, CAREGIVER | Mọi chứng chỉ | Đủ |
| GET | `/api/v1/certificates/caregivers/{caregiverId}/verified` | `.verifiedList` (40) | authenticated | Chứng chỉ VERIFIED | Đủ |
| POST | `/api/v1/certificates/caregivers/{caregiverId}` | `.create` (49) | ADMIN, MANAGER, CAREGIVER | Tạo chứng chỉ | Thiếu validate (message key, `CertificateRequest.java:12-14`) |
| PUT | `/api/v1/certificates/{id}` | `.update` (60) | ADMIN, MANAGER, CAREGIVER | Sửa → PENDING | Đủ |
| PATCH | `/api/v1/certificates/{id}/verification` | `.verify` (71) | ADMIN | Duyệt | Thiếu nghiệp vụ: không có bước manager (D9) |
| DELETE | `/api/v1/certificates/{id}` | `.delete` (82) | ADMIN, MANAGER, CAREGIVER | Xóa mềm | Đủ |
| GET | `/api/v1/work-shifts`, `/{id}` | `WorkShiftController.list/.get` (38, 51) | mọi role | Danh sách / chi tiết ca | 🔴 Không giới hạn trung tâm, lộ ca DRAFT (SEC-08); N+1 |
| GET | `/api/v1/work-shifts/registrations/me` | `.myRegistrations` (58) | CAREGIVER | | Đủ |
| GET | `/api/v1/work-shifts/{id}/registrations` | `.listRegistrationsForShift` (68) | ADMIN, MANAGER | | Đủ |
| POST | `/api/v1/work-shifts/centers/{centerId}` | `.create` (81) | ADMIN, MANAGER | Tạo ca | ⚠️ Lỗi múi giờ (`WorkShiftServiceImpl.java:160-166`) |
| POST | `/api/v1/work-shifts/{shiftId}/registrations` | `.register` (90) | CAREGIVER | Đăng ký ca | Thiếu: ca đã qua, đăng ký lại sau hủy |
| PUT | `/api/v1/work-shifts/{id}` | `.update` (102) | ADMIN, MANAGER | Sửa ca | Thiếu nghiệp vụ |
| PATCH | `/api/v1/work-shifts/registrations/{id}/review` | `.reviewRegistration` (113) | ADMIN, MANAGER | Duyệt đăng ký → schedule | ⚠️ Đếm capacity hai lần (`ShiftRegistrationServiceImpl.java:183-194`) |
| DELETE | `/api/v1/work-shifts/{id}`, `/registrations/{id}` | `.delete/.cancelRegistration` (125, 134) | MANAGER / CAREGIVER | Hủy ca / đăng ký | Đủ |
| POST / GET / DELETE | `/api/v1/provider-media/{ownerType}/{ownerId}[/{mediaFileId}]` | `ProviderMediaController` (31, 40, 48) | ADMIN, MANAGER, CAREGIVER | Ảnh trung tâm/caregiver/chứng chỉ | Thiếu nghiệp vụ: CUSTOMER không xem được; không transaction (orphan) |

### 4.5 BookingService — `BC` (`@RequestMapping("/api/v1")`)

| Method | Path | Handler (dòng) | Role | Mô tả | Mức độ |
|---|---|---|---|---|---|
| GET | `/api/v1/bookings` | `listByCustomer` (46) | CUSTOMER | Booking của tôi, lọc status/recipient, phân trang | Thiếu nghiệp vụ: không lọc ngày/trung tâm, không sort |
| GET | `/api/v1/bookings/{id}` | `getByCustomer` (57) | CUSTOMER | Chi tiết + dịch vụ + buổi | Đủ |
| GET | `/api/v1/bookings/{id}/occurrences` | `listOccurrences` (64) | CUSTOMER | Danh sách buổi | Đủ (không phân trang) |
| GET | `/api/v1/bookings/center`, `/center/{id}` | `listByCenter/getByCenter` (71, 84) | CENTER_MANAGER | Booking trung tâm | ⚠️ Lọc `caregiverId` bỏ sót buổi đã đổi người (`BookingSpecifications.java:37-39`) |
| GET | `/api/v1/bookings/admin`, `/admin/{id}` | `listAll/getAny` (91, 105) | ADMIN | Toàn hệ thống | Đủ |
| GET | `/api/v1/bookings/caregiver-access` | `checkCaregiverAccess` (112) | **isAuthenticated** | CareRecipient hỏi quyền caregiver | 🔴 SEC-03 |
| GET | `/api/v1/booking-occurrences` | `listCaregiverOccurrences` (120) | CAREGIVER | Lịch được giao (≤ 62 ngày) | Đủ |
| GET | `/api/v1/booking-occurrences/{id}` | `getCaregiverOccurrence` (131) | **isAuthenticated** | Chi tiết buổi + snapshot | 🔴 SEC-03 (nhánh không phải "chỉ CAREGIVER" không kiểm scope) |
| POST | `/api/v1/bookings` | `create` (150) | CUSTOMER | Tạo booking + Idempotency-Key | Thiếu nghiệp vụ: không kiểm lịch rảnh/ngày nghỉ, kỹ năng luôn bị bỏ qua (`careServiceIds` null), giá NULL khi cờ catalog tắt |
| POST | `/api/v1/bookings/{id}/cancel` | `cancelByCustomer` (159) | CUSTOMER | Hủy cả booking trước buổi đầu | Thiếu nghiệp vụ: không hủy từng buổi, hoàn tiền chỉ log |
| POST | `/api/v1/bookings/center/{id}/accept`, `/reject`, `/cancel` | `accept/reject/cancelByCenter` (168, 178, 188) | CENTER_MANAGER | Duyệt / từ chối / hủy đặc biệt | Đủ phần trạng thái; hoàn tiền chỉ log |
| POST | `/api/v1/bookings/{id}/deposit-paid` | `markDepositPaid` (198) | **isAuthenticated** | Đánh dấu đã cọc → CONFIRMED | 🔴 **Stub-TODO** + SEC-01 |
| PATCH | `/api/v1/bookings/center/{id}/caregiver` | `updateCaregiver` (208) | CENTER_MANAGER | Đổi caregiver booking + buổi tương lai | Thiếu: không audit |
| PATCH | `/api/v1/bookings/center/occurrences/{occurrenceId}/caregiver` | `updateOccurrenceCaregiver` (218) | CENTER_MANAGER | Đổi caregiver một buổi | Thiếu: không audit, không cập nhật `caregiver-access` |

### 4.6 MediaService — `MD/controller/MediaController.java`

| Method | Path | Role | Mô tả | Mức độ |
|---|---|---|---|---|
| POST | `/api/v1/media` (multipart, `uploadedBy`, `sourceService`, `referenceType`, `referenceId`) | isAuthenticated | Upload R2 | 🔴 Client tự khai `uploadedBy` (SEC-07) |
| GET | `/api/v1/media/{id}`, `/{id}/download-url` | isAuthenticated | Metadata / presigned URL | 🔴 Đọc file của bất kỳ ai (SEC-07) |
| DELETE | `/api/v1/media/{id}` | isAuthenticated | Xóa | 🔴 Xóa file của người khác (SEC-07) |

### 4.7 Endpoint FE đang chờ mà BE chưa có / lệch path

FE (`../FE-careconnect/openapi/openapi.yaml`) gọi `/users/register`, `/users/register/verify`, `/users/register/resend-otp`, `/users/forgot-password`, `/users/reset-password` — BE đã chuyển sang `/api/v1/auth/**`; mọi path FE thiếu tiền tố `/api/v1`. Các màn hình đang mock và cần API mới: tìm caregiver, đặt lịch + cọc, danh sách/chi tiết booking, care report (customer & caregiver), review, hồ sơ bệnh nhân (API đã có nhưng FE chưa nối), lịch caregiver, ca đang chạy, lịch sử chăm sóc, caregiver/booking của center manager, admin dashboard / doanh thu / dịch vụ / trung tâm / chính sách / chất lượng / khiếu nại, thông báo (icon chưa có logic).

---

## 5. Chức năng nghiệp vụ theo role

Ký hiệu: ✅ Đã có · ⚠️ Có nhưng thiếu/sai · ❌ Chưa có. Cột "Task" trỏ sang `docs/WORK_PLAN.md`.

### 5.1 CUSTOMER

| Chức năng | Trạng thái | Bằng chứng / thiếu gì | Task |
|---|---|---|---|
| Đăng ký, đăng nhập, sửa hồ sơ, đổi mật khẩu | ⚠️ | Có (`ID/controller/AuthenticationController.java`, `UserController.java`); đổi/reset mật khẩu không thu hồi refresh | IAM-02 |
| Hồ sơ người bệnh (nhiều hồ sơ, thuốc, liên hệ, người thân OWNER/EDITOR/VIEWER) | ✅ | `CR/controller/CareRecipientController.java`; INACTIVE chưa có tác dụng | CR-01 |
| Xem danh sách trung tâm ACTIVE | ❌ | `GET /care-centers` chỉ ADMIN/MANAGER (`PV/controller/CareCenterController.java:40`) | SRCH-02 |
| Xem dịch vụ ACTIVE + giá của trung tâm | ⚠️ | Có API nhưng trả cả INACTIVE, không phân trang (`CC/controller/CenterOfferingController.java:45`) | CAT-02 |
| Tìm caregiver phủ **tất cả** dịch vụ đã chọn, lọc giới tính/kinh nghiệm/rating, loại người trùng lịch/nghỉ | ❌ | `GET /caregivers` chỉ lọc centerId + keyword (`PV/service/Impl/CaregiverServiceImpl.java:131-139`); chưa có `caregiver_services`, chưa có lịch rảnh | PRV-01, PRV-02, SRCH-01 |
| Xem chi tiết caregiver (bằng cấp đã xác minh, đánh giá phân trang) | ⚠️ | `GET /caregivers/{id}` lộ cả hồ sơ chưa xác minh; chưa có review | SRCH-01, FB-01 |
| Xem chi phí tạm tính trước khi đặt | ❌ | Báo giá chỉ chạy bên trong `create`; công thức HOUR làm tròn trên tổng (`CareServiceCatalogPortImpl.java:138-146`) | BK-01 |
| Tạo booking (patient của mình, dịch vụ, caregiver, kiểu giờ/ngày/ca, lặp) | ⚠️ | Có (`BSI` `create`), chống trùng caregiver/người bệnh, 60 buổi; thiếu kiểm lịch rảnh/nghỉ, kỹ năng bị bỏ qua, không chặn nợ | BK-02 |
| Thanh toán cọc qua cổng | ❌ | Chỉ có stub `deposit-paid` | PAY-05, PAY-06, PAY-08 |
| Thanh toán phần còn lại sau khi hoàn thành | ❌ | — | PAY-11 |
| Hủy booking / từng buổi, xem trước tiền hoàn | ⚠️ | Chỉ hủy cả booking trước buổi đầu; hoàn tiền chỉ log (`BSI:666-671`) | BK-03, PAY-09, PAY-10 |
| Lịch sử đặt lịch (lọc, phân trang) | ⚠️ | Có phân trang + lọc status/recipient; thiếu lọc ngày/trung tâm, lịch sử trạng thái | BK-06 |
| Theo dõi realtime ca đang chạy | ❌ | Không có realtime | NTF-01, OPS-03 |
| Xem care report từng buổi | ❌ | — | OPS-05 |
| Đánh giá caregiver | ❌ | — | FB-01 |
| Khiếu nại | ❌ | — | FB-02 |
| Nhận thông báo (xác nhận, hủy, hoàn thành, report mới) | ❌ | — | NTF-01, NTF-02 |
| Xem lịch sử thanh toán / hoàn tiền | ❌ | — | PAY-16 |

### 5.2 CAREGIVER (mobile)

| Chức năng | Trạng thái | Bằng chứng / thiếu gì | Task |
|---|---|---|---|
| Xem hồ sơ của mình, đăng ký ca làm việc | ✅ | `GET /caregivers/me`, `/work-shifts/{id}/registrations` | — |
| Xem lịch được giao, chi tiết ca (người bệnh, địa chỉ, dịch vụ, ghi chú) | ⚠️ | `GET /booking-occurrences` (`BC:120`), chi tiết có snapshot; caregiver vẫn thấy snapshot booking CANCELLED (`BK/enums/BookingStatus.java:21`) | OPS-01 |
| Xác nhận nhận ca (acknowledge) | ❌ | `OccurrenceStatus` không có ACKNOWLEDGED | OPS-01 |
| Bắt đầu ca (check-in), sinh checklist | ❌ | Action START không ai gọi (`BSI:636-638`) | OPS-02 |
| Đánh dấu task, ghi chú tình trạng | ❌ | — | OPS-03 |
| Kết thúc ca (check-out) | ❌ | `actual_end_at` chưa có API ghi | OPS-04 |
| Viết, gửi care report kèm ảnh | ❌ | Media có `CARE_REPORT_IMAGE` nhưng chưa ai dùng | OPS-05 |
| Báo khách vắng mặt (CUSTOMER_NO_SHOW) | ❌ | — | OPS-06 |
| Lịch sử ca đã hoàn thành | ❌ | — | OPS-01 |
| Quản lý ngày nghỉ | ❌ | — | PRV-02 |
| Nhận thông báo lịch mới / đổi lịch | ❌ | — | NTF-02 |

### 5.3 CENTER_MANAGER

| Chức năng | Trạng thái | Bằng chứng / thiếu gì | Task |
|---|---|---|---|
| Tạo tài khoản caregiver | ✅ | `POST /users` (Identity) + `POST /caregivers/centers/{centerId}` (Provider) — hai bước rời | — |
| Sửa / vô hiệu caregiver của trung tâm | ⚠️ | Có; lách xác minh qua `caregiverStatus` (`CaregiverServiceImpl.java:152`) | PRV-06 |
| Gán dịch vụ cho caregiver (CaregiverService) | ❌ | — | PRV-01 |
| Xác minh chứng chỉ cấp 1 | ❌ | Chỉ ADMIN duyệt | PRV-05 |
| Lịch làm việc caregiver, ngày nghỉ | ⚠️ | Có ca/đăng ký/lịch; lỗi capacity + múi giờ; không có ngày nghỉ | PRV-02, PRV-03 |
| Chọn dịch vụ từ danh mục, cấu hình giá giờ/ngày/ca, vô hiệu dịch vụ | ⚠️ | Có (`CC/`); lỗi `isActive`, chọn được item INACTIVE | CAT-01 |
| Xem / xác nhận / từ chối / hủy đặc biệt booking | ⚠️ | Có; hoàn tiền chỉ log | BK-04 |
| Phân công / đổi caregiver (cả booking hoặc từng buổi) | ⚠️ | Có; không audit, không kiểm lịch rảnh | BK-05, BK-02 |
| Theo dõi ca, xử lý MISSED / xác nhận khách vắng | ❌ | — | OPS-06 |
| Xem hồ sơ người bệnh của booking trung tâm | ❌ | — | CR-01 |
| Phản hồi đánh giá | ❌ | — | FB-01 |
| Xử lý khiếu nại cấp 1, escalate | ❌ | — | FB-02 |
| Thống kê trung tâm | ❌ | — | STAT-01, PAY-15 |
| Xem đối soát của trung tâm | ❌ | — | PAY-14 |
| Sửa thông tin trung tâm của mình | ❌ | `PUT /care-centers/{id}` chỉ ADMIN | PRV-04 |

### 5.4 ADMIN

| Chức năng | Trạng thái | Bằng chứng / thiếu gì | Task |
|---|---|---|---|
| Xem danh sách user toàn hệ thống | ⚠️ | Không lọc role/status/keyword, không tổng số | IAM-05 |
| Khóa / mở khóa khách hàng | ⚠️ | Có; không lưu lý do, refresh token sống lại sau khi mở khóa | IAM-02, IAM-05 |
| Tạo, duyệt, khóa / mở khóa trung tâm | ⚠️ | Có `PATCH status` nhưng không state machine, không khóa tài khoản / hủy booking dây chuyền | PRV-04, IAM-06 |
| CRUD danh mục dịch vụ chuẩn | ⚠️ | Có; thiếu lọc, không chặn xóa khi đang dùng | CAT-02 |
| Audit hồ sơ / bằng cấp caregiver | ⚠️ | Có duyệt hồ sơ + chứng chỉ; không kiểm trạng thái hiện tại, không có bước trung tâm | PRV-05 |
| Cấu hình % cọc, bảng hoàn tiền, hoa hồng | ❌ | Env 0.30 | PAY-02 |
| Đối soát nền tảng ↔ trung tâm | ❌ | — | PAY-14 |
| Khiếu nại cấp cao, quyết định hoàn tiền | ❌ | — | FB-02, PAY-12 |
| Thống kê booking/khách/caregiver/trung tâm | ❌ | — | STAT-01 |
| Thống kê doanh thu toàn nền tảng, theo trung tâm | ❌ | — | PAY-15 |
| Xem toàn bộ booking | ✅ | `GET /bookings/admin` | — |
| Quản lý role / permission | ✅ | `RoleController`, `PermissionController` | — |

---

## 6. State machine: hiện trạng ↔ đề xuất

### 6.1 Booking (`BK/enums/BookingAction.java`)

| Chuyển | Code hiện tại | Đề xuất (theo D1–D5) |
|---|---|---|
| PENDING → AWAITING_DEPOSIT | ✅ ACCEPT (manager, cần caregiver) | Giữ; thêm kiểm lịch rảnh caregiver (BK-02) |
| PENDING → REJECTED | ✅ REJECT | Giữ; thêm thông báo |
| PENDING / AWAITING_DEPOSIT → CANCELLED (khách) | ✅ | Giữ, phí 0 |
| PENDING / AWAITING_DEPOSIT → EXPIRED | ✅ scheduler | Giữ |
| AWAITING_DEPOSIT → CONFIRMED | ⚠️ PAY_DEPOSIT qua stub không bảo vệ | Chỉ qua `deposit-confirmations` (role SERVICE, PAY-08) |
| CONFIRMED → CANCELLED (khách) | ⚠️ chỉ khi chưa buổi nào tới giờ | Hủy cả booking hoặc từng buổi, tính phí theo §1.2 (BK-03) |
| CONFIRMED / IN_PROGRESS → CANCELLED (trung tâm) | ⚠️ hoàn tiền chỉ log | Hoàn 100% buổi chưa diễn ra (BK-04) |
| CONFIRMED → IN_PROGRESS | ❌ START không ai gọi | Buổi đầu tiên bắt đầu Session (OPS-02) |
| IN_PROGRESS → COMPLETED | ❌ COMPLETE không ai gọi | Mọi buổi ở trạng thái kết thúc và có ≥ 1 buổi được tính tiền (OPS-04, action mới `FINALIZE`) |
| IN_PROGRESS → CANCELLED | ❌ | Mọi buổi kết thúc mà không buổi nào được tính tiền (FINALIZE) |

### 6.2 Occurrence (`BK/enums/OccurrenceStatus.java`)

| Chuyển | Code | Đề xuất |
|---|---|---|
| SCHEDULED → ACKNOWLEDGED | ❌ (không có trạng thái) | Caregiver xác nhận (OPS-01) |
| SCHEDULED/ACKNOWLEDGED → IN_PROGRESS | ❌ (enum cho phép nhưng không API) | Start session (OPS-02) |
| IN_PROGRESS → COMPLETED | ❌ | End session (OPS-04) |
| SCHEDULED/ACKNOWLEDGED → CANCELLED | ⚠️ chỉ khi cả booking bị hủy | Hủy từng buổi (BK-03, BK-04) |
| SCHEDULED/ACKNOWLEDGED → MISSED | ❌ (không job) | Job MISSED (OPS-06) |
| SCHEDULED/ACKNOWLEDGED → CUSTOMER_NO_SHOW | ❌ | Caregiver báo + manager xác nhận (OPS-06) |

### 6.3 Các state machine khác

| Đối tượng | Code | Đề xuất |
|---|---|---|
| Session | ❌ | IN_PROGRESS → ENDED (OPS-02, OPS-04) |
| Report | ❌ | DRAFT → SUBMITTED (OPS-05) |
| Payment | ❌ | PENDING → SUCCESS / FAILED / EXPIRED; SUCCESS → PARTIALLY_REFUNDED → REFUNDED (PAY-05/06/10) |
| Refund | ❌ | REFUND_PENDING → REFUNDED / FAILED (FAILED → REFUND_PENDING khi retry) (PAY-10) |
| Remaining invoice | ❌ | PENDING → PAID / OVERDUE; OVERDUE → PAID (PAY-11) |
| Settlement | ❌ | DRAFT → CONFIRMED → PAID (PAY-14) |
| Center | ⚠️ chuyển tùy ý (`CareCenterServiceImpl.java:118-135`) | PENDING → ACTIVE / REJECTED; ACTIVE ↔ SUSPENDED; ACTIVE ↔ INACTIVE; * → ARCHIVED (PRV-04) |
| Caregiver credential | ⚠️ PENDING → VERIFIED / REJECTED | PENDING → CENTER_VERIFIED → ADMIN_VERIFIED; REJECTED (PRV-05) |
| Caregiver profile | ⚠️ verify kích hoạt lại hồ sơ ARCHIVED (`CaregiverServiceImpl.java:160-178`) | Chặn chuyển từ ARCHIVED; ACTIVE chỉ khi VERIFIED (PRV-05, PRV-06) |
| Complaint | ❌ | OPEN → IN_PROGRESS → RESOLVED; OPEN/IN_PROGRESS/RESOLVED → ESCALATED → RESOLVED_BY_ADMIN (FB-02) |
| Caregiver schedule (Provider) | ⚠️ trùng occurrence | Chỉ SCHEDULED / CANCELLED (D11, PRV-02) |

---

## 7. Lỗi bảo mật / phân quyền / ownership

Mọi mục dưới đây là **P0** trong `WORK_PLAN.md`.

| Mã | Mức | Vị trí | Mô tả | Task / người sở hữu |
|---|---|---|---|---|
| SEC-01 | 🔴 Cao | `BC:198-204`, `BSI:512-533`; test `BookingService/src/test/java/mss301/bookingservice/BookingApiTests.java:781-784` | `POST /bookings/{id}/deposit-paid` chỉ `isAuthenticated()`: bất kỳ ai cũng xác nhận cọc cho booking bất kỳ, không đối chiếu số tiền; test hợp thức hóa lỗi | PAY-08 — [Người A] |
| SEC-02 | 🔴 Cao | `CR/controller/CareRecipientController.java:72-85,140-144` | `/{id}/snapshot`, `/{id}/access`, `/batch` chỉ cần đăng nhập → IDOR dữ liệu y tế, địa chỉ, tọa độ | CR-01 — [Người C] |
| SEC-03 | 🔴 Cao | `BC:112-118`, `BC:131-146`, `BK/service/Impl/BookingOccurrenceServiceImpl.java:63-69` | `GET /booking-occurrences/{id}`: người không phải "chỉ CAREGIVER" đi vào nhánh không kiểm scope → lộ snapshot y tế; `caregiver-access` dò được quan hệ caregiver ↔ người bệnh; controller tự so chuỗi `ROLE_CAREGIVER` | BK-07 — [Người B] |
| SEC-04 | 🔴 Cao | `ID/controller/UserController.java:52-65` | `/users/{id}`, `/by-email`, `/batch` không `@PreAuthorize` → đọc email/SĐT/role/permission của bất kỳ ai, dò email | IAM-04 — [Người C] |
| SEC-05 | 🔴 Cao | `ID/service/Impl/AuthenticationServiceImpl.java:92-124`, `ID/service/Impl/CurrentIdentityServiceImpl.java:42-49`, `UserServiceImpl.java:397-399` | Refresh không kiểm `authz_version` → refresh token sống sau đổi/reset mật khẩu, sau khóa + mở khóa; refresh không nguyên tử (2 request đồng thời cùng thắng); phát hiện reuse nhưng không thu hồi (`TOKEN_REUSED` 1085 không dùng) | IAM-02 — [Người C] |
| SEC-06 | 🟠 Trung bình | `AuthenticationServiceImpl.java:244-261, 313-316`, `UserServiceImpl.java:323-340` | Hủy phiên không blacklist access; tin `X-Forwarded-For` client (vượt rate limit); thu hồi role không tăng `authz_version` | IAM-03 — [Người C] |
| SEC-07 | 🔴 Cao | `MD/service/Impl/MediaServiceImpl.java:63-134`, `MD/controller/MediaController.java` | Media không kiểm sở hữu: tự khai `uploadedBy`, đọc / xóa file của người khác (kể cả hồ sơ y tế, chứng chỉ) | MED-01 — [Người C] |
| SEC-08 | 🟠 Trung bình | `PV/controller/CareCenterController.java:42-49`; `PV/service/Impl/CaregiverServiceImpl.java:111-121,152,160-178`; `PV/service/Impl/WorkShiftServiceImpl.java:81-95` | Manager xem mọi trung tâm kể cả đã xóa; mọi user xem hồ sơ caregiver chưa xác minh; manager lách xác minh qua `caregiverStatus`; admin verify kích hoạt lại hồ sơ ARCHIVED; ca DRAFT lộ cho mọi role | PRV-06 — [Người C] |
| SEC-09 | 🟠 Trung bình | `BK/enums/BookingStatus.java:21`, `BK/mapper/OccurrenceMapper.java:66-69` | Caregiver vẫn đọc snapshot y tế của booking CANCELLED | BK-07 — [Người B] |
| SEC-10 | 🟡 Thấp | `GW/security/SecurityConfig.java:87-101` | CORS thiếu `Idempotency-Key` → FE web không gọi được `POST /bookings`; chưa có route public cho IPN | GW-01 — [Người B] |
| SEC-11 | 🟡 Thấp | `BSI:233-237` | `accept` resolve caregiver ngoài khóa dòng (race nhỏ) | BK-02 — [Người B] |
| SEC-12 | 🟡 Thấp | Booking `SCHEDULE_HOLDING` | Không giới hạn số booking PENDING → khách có thể giữ chỗ caregiver tới 24h hàng loạt | BK-02 — [Người B] |

---

## 8. Lệch quy ước `AGENTS.md` và tài liệu (sửa khi chạm tới — không mở task riêng trừ khi ghi rõ)

| Hạng mục | Vị trí | Xử lý |
|---|---|---|
| Provider `ddl-auto: update`, thiếu `@Repository` ở 5 repository, JPQL nhúng literal enum, logic trong controller, `@Operation` khắp controller | `ProviderService/src/main/resources/application.yaml:17`, `PV/repository/*`, `PV/controller/*` | PRV-06 |
| Booking: một controller phục vụ hai resource, `@RequestMapping("/api/v1")`, `BookingOccurrenceServiceImpl` không interface, `BookingSpecifications` là class trong `repository/` | `BC:36`, `BSI:118` | BK-08 |
| CareRecipient: 3 service con không có interface | `CR/service/Impl/CareRecipientServiceImpl.java:77-79` | CR-01 (sửa khi chạm) |
| Catalog: controller dựng `APIResponse`, builder thay mapper, tên method lệch, file rác `copy_base.py`, `note.md` lỗi thời, Feign Identity khai báo nhưng không dùng | `CC/` | CAT-01 |
| Media: dải lỗi trùng, tài liệu sai | `MD/exception/ErrorCode.java:18-29`, `MediaService/AGENTS.md` | MED-01 |
| README / AGENTS / DOCKER / e2e lỗi thời (path cũ, `/internal`, secret) | xem §0 | INF-01 (mỗi người cập nhật tài liệu service mình sở hữu; INF-01 sửa phần dùng chung + e2e) |
| Booking hardcode `deposit-rate` 0.30 | `BK/config/BookingProperties.java:43` | PAY-03 |
| `docs/PLAN.md` chưa phản ánh nối Catalog | `docs/PLAN.md:33` | INF-01 |
