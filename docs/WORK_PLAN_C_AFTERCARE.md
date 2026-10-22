# WORK PLAN — [Người C]: Hậu mãi, chất lượng & bảo mật

> Tách từ `docs/WORK_PLAN.md` (2026-10-21; phân lại B / C cùng ngày: B xử lý chính luồng booking, C nhẹ hơn). File này chứa **tổng quan tính năng theo role**, **toàn bộ task chi tiết** của [Người C], các contract liên quan và lịch sprint riêng.
> Phần dùng chung — nguyên tắc chia việc, quy ước ErrorCode / token SERVICE / tiền tệ / thời gian (§1–§3), toàn bộ hợp đồng (§7), lộ trình chung (§8), rủi ro (§9) — nằm ở `docs/WORK_PLAN.md`.
> **Số mục giữ nguyên như bản gốc**, nên mọi tham chiếu `§x`, `C2.3`… trỏ đúng sang `docs/WORK_PLAN.md`. Khảo sát hiện trạng: `docs/GAP_ANALYSIS.md`.

## Tổng quan: [Người C] làm gì

**Vai trò trong nhóm:** phần **hậu mãi và chất lượng** quanh booking cùng **bảo mật nền tảng**. C không điều khiển vòng đời booking (đó là việc của [Người B]), mà làm những gì xảy ra **trong và sau** mỗi buổi chăm sóc: checklist / ghi chú, care report, thông báo realtime, đánh giá, khiếu nại. C cũng sở hữu kiểm soát chất lượng phía trung tâm (xác minh chứng chỉ, vòng đời trung tâm), hồ sơ người bệnh, file / ảnh, và các lỗ hổng bảo mật ở Identity / CareRecipient / Provider / Media. Khối lượng nhẹ hơn B có chủ đích; C là người dự phòng khi B quá tải.

| Role | Tính năng [Người C] làm cho role này | Task |
|---|---|---|
| **CUSTOMER** | Hồ sơ người bệnh được bảo vệ (không ai ngoài người thân / caregiver được giao xem được); theo dõi ca đang diễn ra realtime (task đã làm, ghi chú, chỉ số sức khỏe); xem care report từng buổi kèm ảnh; đánh giá caregiver sau khi hoàn thành; gửi khiếu nại và escalate khi không đồng ý; nhận thông báo in-app / realtime; phiên đăng nhập an toàn | CR-01, OPS-03, OPS-05, FB-01, FB-02, NTF-01, NTF-02, IAM-02, IAM-03 |
| **CAREGIVER** | Đánh dấu từng việc trong checklist (làm / không làm được kèm lý do), ghi chú tình trạng + chỉ số sức khỏe; viết và gửi care report có ảnh; nhận thông báo lịch mới / đổi lịch / nhắc ca; xem phản hồi đánh giá | OPS-03, OPS-05, NTF-02, FB-01 |
| **CENTER_MANAGER** | Phản hồi đánh giá; xử lý khiếu nại cấp 1 và escalate lên Admin; xác minh chứng chỉ caregiver bước 1; sửa thông tin trung tâm của mình; thống kê vận hành trung tâm (booking, tỷ lệ hoàn thành, đánh giá, khiếu nại, top caregiver) | FB-01, FB-02, PRV-05, PRV-04, STAT-01 |
| **ADMIN** | Danh sách user toàn hệ thống (lọc role / trạng thái / trung tâm, tổng số), khóa / mở khóa kèm lý do; duyệt, khóa / mở khóa trung tâm (khóa kéo theo khóa tài khoản trung tâm + hủy và hoàn 100% buổi chưa diễn ra); audit chứng chỉ và hồ sơ caregiver (bước 2); khiếu nại cấp cao, quyết định số tiền hoàn; ẩn đánh giá vi phạm; thống kê hệ thống (user, trung tâm, caregiver, booking) | IAM-05, IAM-06, PRV-04, PRV-05, FB-02, FB-01, STAT-01 |
| **Hệ thống / service khác** | Bảo mật phiên Identity (refresh an toàn, thu hồi phiên, IP tin cậy); phân quyền API tra cứu user; Media chỉ cho service gọi, kiểm sở hữu file; sửa phạm vi / quy ước ProviderService; module thông báo dùng chung (API cho service + SSE) | IAM-02, IAM-03, IAM-04, MED-01, PRV-06, NTF-01 |

**Service đụng tới:** BookingService (care report, checklist / ghi chú trong ca, thông báo, đánh giá, khiếu nại, thống kê — không đụng state machine booking), CareRecipientService, MediaService, ProviderService (vòng đời trung tâm, xác minh, phạm vi, rating), IdentityService (IAM-02 → IAM-06).

---

## 6. Task chi tiết

### 6.2 [Người C] — Hậu mãi, chất lượng & bảo mật (74 SP, 17 task)

| Mã | Tên | Ưu tiên | SP | Sprint |
|---|---|---|---|---|
| CR-01 | CareRecipient: chặn IDOR snapshot/access/batch (SEC-02) + INACTIVE | P0 | 5 | S1 |
| IAM-02 | Refresh an toàn: kiểm `authz_version`, nguyên tử, thu hồi khi reuse / đổi mật khẩu / khóa (SEC-05) | P0 | 5 | S1 |
| IAM-03 | Hủy phiên vô hiệu access, IP tin cậy, thu hồi role tăng version (SEC-06) | P0 | 3 | S1 |
| IAM-04 | Phân quyền endpoint tra cứu user (SEC-04) | P0 | 3 | S1 |
| PRV-06 | Provider: sửa phạm vi + lách xác minh + quy ước (SEC-08) | P0 | 3 | S2 |
| MED-01 | Media: quyền sở hữu qua service, đổi dải lỗi 7xxx (SEC-07) | P0 | 5 | S2 |
| NTF-01 | Module thông báo: bảng, API, SSE, API cho service | P0 | 5 | S2 |
| OPS-03 | Cập nhật task, ghi chú tình trạng, theo dõi realtime | P0 | 3 | S3 |
| NTF-02 | Gắn thông báo vào các sự kiện nghiệp vụ + nhắc ca | P1 | 3 | S3 |
| PRV-05 | Xác minh chứng chỉ 2 bước + audit hồ sơ caregiver | P1 | 5 | S3 |
| OPS-05 | Care report: nháp, ảnh, gửi, khách xem | P0 | 5 | S4 |
| FB-01 | Đánh giá caregiver + phản hồi của trung tâm + cập nhật rating | P1 | 5 | S4 |
| FB-02 | Khiếu nại cấp 1 / escalate / Admin quyết định hoàn tiền | P1 | 8 | S5 |
| IAM-05 | Quản lý user cho Admin: lọc, tổng số, lý do khóa | P1 | 3 | S5 |
| PRV-04 | Vòng đời trung tâm + khóa dây chuyền + manager sửa trung tâm | P1 | 5 | S6 |
| IAM-06 | Khóa / mở tài khoản theo trung tâm | P1 | 3 | S6 |
| STAT-01 | Thống kê vận hành trung tâm / hệ thống | P2 | 5 | S6 |

---

#### CR-01 — CareRecipient: chặn IDOR snapshot/access/batch (SEC-02) + INACTIVE
**Người:** [Người C] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S1 · **Phụ thuộc:** IAM-01 (token SERVICE — làm phần không phụ thuộc trước)

- **Role:** SERVICE, CUSTOMER (VIEWER+), ADMIN.
- **API** (`CR/controller/CareRecipientController.java`):
  - `GET /{id}/snapshot`: `hasAnyRole('SERVICE','CUSTOMER','ADMIN')`. CUSTOMER phải có ≥ VIEWER, ngoài phạm vi → 404.
  - `GET /{id}/access`: **chỉ** `hasRole('SERVICE')`.
  - `POST /batch`: SERVICE, ADMIN → mọi id; CUSTOMER → chỉ trả các hồ sơ mình có quyền (lọc âm thầm).
- **Quy tắc nghiệp vụ:**
  1. Booking gọi `snapshot` / `access` bằng token **SERVICE** (sửa `BK/client/CareRecipientClient.java` sang `ServiceTokenInterceptor`, [Người B] review). Kiểm quyền của **khách** dùng tham số `userId` như hiện tại.
  2. CareRecipient gọi Booking `caregiver-access` và Identity `by-email` / `batch` bằng token SERVICE.
  3. **INACTIVE** = tạm ngưng:
     - không đặt lịch mới (Booking đã chặn, `RECIPIENT_NOT_ACTIVE 5602`);
     - EDITOR / VIEWER chỉ đọc; chỉ OWNER được sửa hoặc kích hoạt lại;
     - snapshot vẫn trả cho booking đã có.

     Sửa / thêm thuốc, liên hệ khi INACTIVE → 409 `RECIPIENT_INACTIVE`.
  4. Center manager xem hồ sơ người bệnh qua `recipientSnapshot` trong `GET /bookings/{id}` (manager cùng trung tâm) (đã lưu trên booking). Kiểm mapper Booking có trả snapshot cho manager; nếu thiếu thì thêm (sửa `BK/mapper/BookingMapper.java`, [Người B] review). **Không** mở API CareRecipient cho manager.
  5. Theo quy ước: tạo interface `MedicationService`, `EmergencyContactService`, `RecipientManagerService` cho 3 class đang không có interface (`CareRecipientServiceImpl.java:77-79`). Cập nhật README / AGENTS bỏ `/internal`.
- **Lỗi:** `RECIPIENT_NOT_FOUND` (mã hiện có, 404), `RECIPIENT_INACTIVE (2700, 409)`.
- **AC:**
  - Given CUSTOMER không liên quan, When `GET /{id}/snapshot`, Then 404.
  - When `GET /{id}/access`, Then 403.
  - When `POST /batch` với id của người khác, Then danh sách rỗng.
  - Given Booking dùng token SERVICE, Then tạo booking vẫn chạy (test `BookingApiTests` xanh).
  - Given hồ sơ INACTIVE, When EDITOR thêm thuốc, Then 409.

#### IAM-02 — Refresh an toàn (SEC-05)
**Người:** [Người C] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S1 · **Phụ thuộc:** —

- **Role:** public (refresh), authenticated.
- **API:** `POST /api/v1/auth/refresh`, `/mobile/refresh`, `/reset-password`, `POST /api/v1/users/me/change-password`, `PATCH /api/v1/users/{id}/status`, `DELETE /api/v1/users/{id}` (hiện có).
- **Quy tắc nghiệp vụ:**
  1. **Refresh nguyên tử:** consume refresh opaque bằng một Lua script (đọc + xóa + kiểm session trong một lệnh) thay cho `getRefreshToken` rồi `revokeRefreshToken` (`AuthenticationServiceImpl.java:92-124`). Hai request đồng thời → đúng một thành công, request còn lại 401.
  2. Session lưu `authzVersion` lúc phát. Refresh so với `users.authz_version` hiện tại; lệch → 401 `UNAUTHENTICATED` và xóa session.
  3. **Reuse detection:** refresh đã bị thu hồi mà vẫn dùng → thu hồi **toàn bộ** session của user + tăng `authz_version` + 401 `TOKEN_REUSED (1085)` + `log.warn` (không log token).
  4. `revokeSessions` (`UserServiceImpl.java:397-399`): ngoài `authzVersion++` còn gọi `sessionStore.revokeAll(userId)`. Áp cho đổi mật khẩu, reset mật khẩu, khóa, xóa mềm.
  5. Test mới:
     - refresh sau đổi mật khẩu → 401;
     - sau khóa rồi mở khóa → 401;
     - 2 refresh đồng thời (test với Redis thật hoặc Redis giả có script);
     - reuse → mọi session mất.
- **Lỗi:** `TOKEN_REUSED (1085, 401)`, `UNAUTHENTICATED (401)`.
- **AC:**
  - Given user đổi mật khẩu, When dùng refresh cũ, Then 401.
  - Given hai thread refresh cùng token, Then một 200, một 401.
  - Given refresh cũ bị dùng lại sau khi xoay, Then mọi phiên của user bị thu hồi.

#### IAM-03 — Hủy phiên vô hiệu access, IP tin cậy, thu hồi role tăng version (SEC-06)
**Người:** [Người C] · **Ưu tiên:** P0 · **SP:** 3 · **Sprint:** S1 · **Phụ thuộc:** IAM-02

- **Quy tắc nghiệp vụ:**
  1. Session lưu `jti` của access token hiện hành. `DELETE /auth/sessions/{id}` blacklist `jti` tới `exp`. `IdentityAuthenticationConverter` (`:43`) kiểm claim `sid` còn tồn tại.
  2. IP client: chỉ đọc `X-Forwarded-For` khi request đến từ proxy tin cậy (`IDENTITY_TRUSTED_PROXIES`, mặc định IP gateway), lấy phần tử cuối do gateway thêm. Gateway (GW-01) ghi đè `X-Forwarded-For` của client. Sửa `AuthenticationServiceImpl.java:313-316`.
  3. Thu hồi role (`UserServiceImpl.java:323-340`): `authzVersion++` (rule #29). Gỡ hai cơ chế trạng thái cũ / mới trùng nhau (B8): logout chỉ dùng `RedisTokenStore`; giữ `IdentityStateService` cho OTP.
  4. Cập nhật `AGENTS.md` §4 và README Identity: refresh opaque + session (thay mô tả JWT `jti`).
- **Lỗi:** —
- **AC:**
  - Given hủy session S, When dùng access của S, Then 401 ở Identity.
  - Given client gửi `X-Forwarded-For` giả qua gateway, Then rate limit vẫn theo IP thật.
  - Given thu hồi role, Then `authz_version` tăng.

#### IAM-04 — Phân quyền endpoint tra cứu user (SEC-04)
**Người:** [Người C] · **Ưu tiên:** P0 · **SP:** 3 · **Sprint:** S1 · **Phụ thuộc:** IAM-01

- **Role:** SERVICE, ADMIN; CENTER_MANAGER chỉ user cùng `center_id`; CUSTOMER chỉ chính mình.
- **API:** `GET /api/v1/users/{id}`, `GET /api/v1/users/by-email`, `POST /api/v1/users/batch` (`ID/controller/UserController.java:52-65`).
- **Quy tắc nghiệp vụ:**
  1. Thêm `@PreAuthorize("hasAnyRole('SERVICE','ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")` và kiểm phạm vi trong `UserServiceImpl`:
     - SERVICE / ADMIN: mọi user;
     - MANAGER: user cùng `center_id` hoặc CUSTOMER có booking ở trung tâm. Do Identity không biết booking, MANAGER chỉ được tra user cùng trung tâm; thông tin khách cho manager lấy qua Booking (Booking gọi `batch` bằng token SERVICE);
     - CUSTOMER / CAREGIVER: chỉ `id = sub`.

     Ngoài phạm vi → 404 `USER_NOT_EXISTED` (không lộ tồn tại).
  2. `by-email`: chỉ SERVICE và ADMIN (dùng cho CareRecipient thêm người thân — CareRecipient chuyển sang token SERVICE).
  3. Bản trả cho SERVICE / ADMIN giữ đủ field; bản trả cho người khác **không** có `permissions`.
  4. Cập nhật client của CareRecipient (`CR/client/IdentityClient.java`), Booking (`BK/client/IdentityClient.java`), Provider (`PV/client/IdentityClient.java`) dùng `ServiceTokenInterceptor` cho các lời gọi này. Phần client Booking do [Người B] review.
- **Lỗi:** `USER_NOT_EXISTED` (mã hiện có, 404), `UNAUTHORIZED (403)`.
- **AC:**
  - Given CUSTOMER, When `GET /users/{id của người khác}`, Then 404.
  - Given CUSTOMER, When `GET /users/by-email`, Then 403.
  - Given SERVICE, Then 200.
  - Given test `IdentityServiceApplicationTests` hiện có cho lookup, Then đổi sang khẳng định phạm vi.

#### PRV-06 — Provider: sửa phạm vi, lách xác minh, quy ước (SEC-08)
**Người:** [Người C] · **Ưu tiên:** P0 · **SP:** 3 · **Sprint:** S2 · **Phụ thuộc:** —

- **Quy tắc nghiệp vụ:**
  1. `GET /care-centers`: MANAGER chỉ thấy trung tâm mình (`manager_user_id = sub`), không có `includeDeleted`.
  2. `GET /care-centers/{id}`: lọc `deleted_at`. Người không phải ADMIN / manager trung tâm nhận response rút gọn (không `email` nội bộ, `managerUserId`, `createdBy`).
  3. `GET /caregivers/{id}`, `/by-user/{userId}`: chỉ ADMIN, manager cùng trung tâm, chính caregiver, SERVICE. Còn lại 404 (khách dùng `/public` ở SRCH-01).
  4. `PUT /caregivers/{id}`:
     - bỏ `caregiverStatus` khỏi request PUT;
     - thêm `PATCH /caregivers/{id}/status` (MANAGER / ADMIN), chỉ cho `ACTIVE ↔ INACTIVE ↔ SUSPENDED`, và ACTIVE chỉ khi `verification_status = VERIFIED`;
     - ARCHIVED chỉ qua DELETE.
  5. `ddl-auto: validate` (`application.yaml:17`). Chạy test trên PostgreSQL thật để bắt lệch schema; thêm change script nếu lệch.
  6. Thêm `@Repository` (5 repository); bind enum bằng `@Param` thay literal (`CaregiverProfileRepository.java:28-30`); chuyển gọi Identity khỏi controller (`CareCenterController.java:77-79,91-93`, `CaregiverController.java:104`); timeout cho Feign `media`; message key cho validation DTO.
- **Lỗi:** `CAREGIVER_INVALID_TRANSITION (4212, 409)`, `CAREGIVER_NOT_VERIFIED (4202, 409)`.
- **AC:**
  - Given manager C2, When `GET /care-centers`, Then chỉ C2.
  - Given CUSTOMER, When `GET /caregivers/{id}`, Then 404.
  - Given manager PATCH ACTIVE cho hồ sơ PENDING, Then 409.
  - Given app khởi động với `validate`, Then không lỗi.

#### MED-01 — Media: quyền sở hữu qua service, đổi dải lỗi (SEC-07)
**Người:** [Người C] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S2 · **Phụ thuộc:** IAM-01

- **Role:** SERVICE (mọi API Media). Người dùng cuối không gọi Media trực tiếp.
- **API:** `MD/controller/MediaController.java`: cả 4 endpoint đổi sang `hasRole('SERVICE')`. Gateway vẫn route nhưng user token nhận 403.
- **Quy tắc nghiệp vụ:**
  1. Service nghiệp vụ kiểm quyền rồi mới gọi Media (Provider: `ProviderMediaServiceImpl`; Booking: care report — OPS-05).
  2. `sourceService` lấy từ claim `client_id` của token. Bỏ tham số `sourceService` của client. `uploadedBy` vẫn do service truyền (actor đã xác thực).
  3. `GET`, `DELETE`: Media kiểm `source_service == client_id` (service này không đọc / xóa file của service khác).
  4. Provider:
     - `MediaClient` dùng `ServiceTokenInterceptor`;
     - `GET /provider-media/...` mở cho CUSTOMER với owner CARE_CENTER / CAREGIVER_PROFILE (ảnh công khai) và chứng chỉ ADMIN_VERIFIED;
     - upload bọc compensation (xóa file Media khi lưu DB lỗi) và log lỗi (`ProviderMediaServiceImpl.java:55-79,195-202`).
  5. ErrorCode Media: 5000–5999 → **7000–7999** (giữ nguyên key; đổi số). Cập nhật `ErrorDecoder` của Provider nếu map theo số.
  6. Viết lại `MediaService/AGENTS.md`, README (port 8086, DB 5446, `/api/v1/media`, token SERVICE).
- **Lỗi:** `MEDIA_ACCESS_DENIED (7202, 404)` cho file của service khác.
- **AC:**
  - Given token CUSTOMER, When `GET /api/v1/media/{id}/download-url`, Then 403.
  - Given token SERVICE của booking, When đọc file do provider tạo, Then 404.
  - Given CUSTOMER, When xem ảnh trung tâm qua Provider, Then 200 với URL tải.

#### NTF-01 — Module thông báo: bảng, API, SSE, API cho service
**Người:** [Người C] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S2 · **Phụ thuộc:** IAM-01, GW-01 (route + SSE)

- **Role:** mọi user đã đăng nhập (thông báo của mình); SERVICE (gửi).
- **API** (`NotificationController`, `/api/v1/notifications`):
  - `GET ?unreadOnly=&type=&page=&size=` → `Page<NotificationResponse>` (`id`, `type`, `title`, `body`, `refType`, `refId`, `readAt`, `createdAt`).
  - `GET /unread-count` → `{count}`.
  - `GET /stream` (SSE, `text/event-stream`): event `notification` (payload `NotificationResponse`), `session-update` (OPS-03), `heartbeat` mỗi 25 giây.
  - `PATCH /{id}/read`, `PATCH /read-all`.
  - `POST /` — SERVICE. Request `{recipientUserIds[1..500], type, title (≤ 200), body (≤ 1000), refType, refId}`.
- **Quy tắc nghiệp vụ:**
  1. Interface nội bộ `NotificationService.notify(List<UUID> userIds, NotificationType type, Map<String,Object> params, String refType, UUID refId)`:
     - nội dung dựng từ `NotificationTemplates` (một nơi, tiếng Việt);
     - gọi **sau commit** (`TransactionSynchronization.afterCommit`); lỗi chỉ log, không làm hỏng nghiệp vụ.
  2. Ghi DB rồi đẩy SSE tới mọi emitter của user (registry trong bộ nhớ, `ConcurrentHashMap<UUID, List<SseEmitter>>`, timeout 30 phút, dọn khi lỗi).

     > ⚠️ Giới hạn: SSE chỉ đúng khi Booking chạy 1 instance. Client luôn có polling `unread-count` mỗi 30 giây làm dự phòng; nhiều instance cần Redis pub/sub (ngoài phạm vi).
  3. User chỉ đọc / đánh dấu thông báo của mình (khác → 404).
  4. `NotificationType`: `BOOKING_CREATED`, `BOOKING_ACCEPTED`, `BOOKING_REJECTED`, `BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `OCCURRENCE_CANCELLED`, `CAREGIVER_ASSIGNED`, `CAREGIVER_UNASSIGNED`, `SESSION_STARTED`, `SESSION_ENDED`, `REPORT_SUBMITTED`, `OCCURRENCE_MISSED`, `NO_SHOW_REPORTED`, `BOOKING_COMPLETED`, `PAYMENT_SUCCEEDED`, `REFUND_COMPLETED`, `REMAINING_DUE`, `REMAINING_OVERDUE`, `COMPLAINT_UPDATED`, `REVIEW_REPLIED`, `SETTLEMENT_UPDATED`, `SHIFT_REMINDER`.
- **DB (Booking):** `notifications`:
  - `notification_id` PK, `recipient_user_id` uuid NN;
  - `type` varchar CHECK, `title`, `body`, `ref_type`, `ref_id`;
  - `read_at`, `created_at`;
  - index `(recipient_user_id, read_at, created_at desc)`.
- **Lỗi:** `NOTIFICATION_NOT_FOUND (5940, 404)`, `TOO_MANY_RECIPIENTS (5941, 400)`.
- **AC:**
  - Given user mở SSE, When booking của họ CONFIRMED, Then nhận event `notification` trong ≤ 2 giây.
  - Given user khác, When PATCH read thông báo không phải của mình, Then 404.
  - Given CUSTOMER gọi `POST /notifications`, Then 403.

#### OPS-03 — Cập nhật task, ghi chú tình trạng, theo dõi realtime
**Người:** [Người C] (thêm endpoint task / ghi chú vào `CareSessionController` của [Người B], B review) · **Ưu tiên:** P0 · **SP:** 3 · **Sprint:** S3 · **Phụ thuộc:** OPS-02, NTF-01

- **Role:** CAREGIVER (ghi); CUSTOMER chủ booking, CENTER_MANAGER, ADMIN (đọc).
- **API:**
  - `PATCH /api/v1/care-sessions/{id}/tasks/{taskId}` — request `{status: DONE|SKIPPED, note? (≤ 1000), skipReason? (bắt buộc khi SKIPPED, ≤ 500)}`.
  - `POST /api/v1/care-sessions/{id}/tasks` — task `CUSTOM` `{title (≤ 200)}`, tối đa 20 task custom.
  - `POST /api/v1/care-sessions/{id}/notes` — request `{content (≤ 2000), vitals?: {systolic, diastolic, heartRate, temperature, spo2, bloodGlucose}}` (khoảng hợp lệ y khoa cơ bản, vd `temperature` 30–45).
  - `GET /api/v1/care-sessions/{id}/notes?page=`.
- **Quy tắc nghiệp vụ:**
  1. Chỉ khi session `IN_PROGRESS` và người gọi là caregiver của session.
  2. Task đổi lại được (DONE ↔ SKIPPED) khi session còn mở.
  3. Mỗi thay đổi đẩy SSE `session-update` (`{sessionId, taskId, status}`) tới khách chủ booking (không tạo bản ghi notification để tránh spam).
- **DB:** `care_session_notes`: `note_id` PK, `session_id` FK, `content`, `vitals` jsonb, `created_by`, `created_at`.
- **Lỗi:** `SESSION_NOT_IN_PROGRESS (5802, 409)`, `TASK_NOT_FOUND (5803, 404)`, `TASK_SKIP_REASON_REQUIRED (5804, 400)`, `INVALID_VITALS (5814, 400)`, `TOO_MANY_CUSTOM_TASKS (5815, 400)`.
- **AC:**
  - Given SKIPPED không lý do, Then 400.
  - Given khách mở SSE, When caregiver DONE một task, Then khách nhận `session-update`.
  - Given khách khác, When `GET /care-sessions/{id}`, Then 404.

#### NTF-02 — Gắn thông báo vào các sự kiện nghiệp vụ + nhắc ca
**Người:** [Người C] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S3 · **Phụ thuộc:** NTF-01

- **Ma trận sự kiện → người nhận:**

  | Sự kiện (nơi phát) | Khách | Manager trung tâm | Caregiver |
  |---|---|---|---|
  | Booking tạo (`create`) | — | ✔ | — |
  | Accept → AWAITING_DEPOSIT | ✔ (mời thanh toán, hạn) | — | ✔ (lịch dự kiến) |
  | Reject / Expire | ✔ | ✔ (expire) | ✔ (nếu đã gán) |
  | CONFIRMED (PAY-08) | ✔ | ✔ | ✔ (lịch mới) |
  | Hủy booking / buổi (BK-03 / 04) | ✔ | ✔ | ✔ |
  | Đổi caregiver (BK-05) | ✔ | — | ✔ cũ (bỏ giao), ✔ mới (lịch mới) |
  | Session bắt đầu / kết thúc | ✔ | — | — |
  | Report gửi | ✔ | — | — |
  | MISSED / báo vắng | ✔ | ✔ | ✔ |
  | Booking COMPLETED | ✔ (mời đánh giá, hóa đơn) | ✔ | — |
  | Payment: cọc OK, hoàn tiền, nhắc nợ (qua `POST /notifications`) | ✔ | — | — |
  | Khiếu nại đổi trạng thái (FB-02) | ✔ | ✔ | — |
  | Phản hồi review (FB-01) | ✔ | — | ✔ |

- **Nhắc ca:** job mỗi 15 phút gửi `SHIFT_REMINDER` cho caregiver khi buổi bắt đầu trong 2 giờ tới và chưa nhắc. Thêm `booking_occurrences.reminder_sent_at`.
- **Quy tắc:** mọi lời gọi `notify` nằm sau commit. Manager nhận được xác định bằng `center_id` → Provider `GET /care-centers/{id}` (`managerUserId`, token SERVICE, cache 10 phút).
- **AC:**
  - Given mỗi sự kiện trong bảng, When xảy ra, Then đúng người nhận đúng một thông báo (test tích hợp mỗi dòng).
  - Given rollback giao dịch, Then không có thông báo.

#### PRV-05 — Xác minh chứng chỉ 2 bước + audit hồ sơ caregiver
**Người:** [Người C] · **Ưu tiên:** P1 · **SP:** 5 · **Sprint:** S3 · **Phụ thuộc:** PRV-06

- **Role:** CENTER_MANAGER (bước 1); ADMIN (bước 2, audit lại).
- **API** (`PV/controller/CertificateController.java`, `CaregiverController.java`):
  - `PATCH /api/v1/certificates/{id}/center-verification` — MANAGER, `{approved (@NotNull), reason?, version}`.
  - `PATCH /api/v1/certificates/{id}/verification` — ADMIN, `{approved, reason?, version}` (sửa endpoint hiện có).
  - `GET /api/v1/certificates/pending-audit?centerId=&page=&size=` — ADMIN (chứng chỉ `CENTER_VERIFIED`).
  - `GET /api/v1/caregivers/pending-verification?centerId=&page=` — ADMIN.
- **Quy tắc nghiệp vụ** (D9):
  1. `PENDING → CENTER_VERIFIED` (manager cùng trung tâm) hoặc `REJECTED` (lý do bắt buộc).
  2. `CENTER_VERIFIED → ADMIN_VERIFIED` hoặc `REJECTED`. Admin được thu hồi `ADMIN_VERIFIED → REJECTED` (audit lại, lý do bắt buộc).
  3. Sửa chứng chỉ → về `PENDING` (đã có).
  4. Chứng chỉ hết hạn (`expiry_date < hôm nay` giờ VN) không duyệt được. Public (SRCH-01) chỉ hiển thị `ADMIN_VERIFIED` còn hạn.
  5. Hồ sơ caregiver (`verification_status` PENDING / VERIFIED / REJECTED, chỉ ADMIN):
     - chặn verify khi `caregiver_status ∈ {ARCHIVED, SUSPENDED}` hoặc trung tâm không ACTIVE;
     - VERIFIED chỉ chuyển INACTIVE → ACTIVE, không đụng SUSPENDED (sửa `CaregiverServiceImpl.java:160-178`);
     - REJECTED → INACTIVE.
  6. Lưu `center_verified_by/at/reason`, `verified_by/at/reason` (admin).
- **DB (Provider):**
  - `certificates.verification_status` CHECK đổi thành (`PENDING`, `CENTER_VERIFIED`, `ADMIN_VERIFIED`, `REJECTED`); backfill `VERIFIED → ADMIN_VERIFIED`;
  - + `center_verified_by uuid`, `center_verified_at`, `center_verification_reason`;
  - enum mới `CertificateVerificationStatus`; `VerificationStatus` giữ cho hồ sơ.
- **Lỗi:** `CERTIFICATE_INVALID_TRANSITION (4253, 409)`, `CERTIFICATE_EXPIRED (4254, 409)`, `VERIFICATION_REASON_REQUIRED (4205, 400)`, `CAREGIVER_INVALID_TRANSITION (4212, 409)`.
- **AC:**
  - Given chứng chỉ PENDING, When admin duyệt thẳng, Then 409.
  - When manager duyệt → admin duyệt, Then ADMIN_VERIFIED và hiện ở `/public`.
  - Given hồ sơ ARCHIVED, When admin verify, Then 409.

#### OPS-05 — Care report: nháp, ảnh, gửi, khách xem
**Người:** [Người C] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S4 · **Phụ thuộc:** OPS-04, MED-01

- **Role:** CAREGIVER (viết); CUSTOMER chủ booking, CENTER_MANAGER trung tâm, ADMIN (đọc).
- **API** (`CareReportController`, `/api/v1/care-reports`):
  - `GET /api/v1/care-reports/{id}`; `GET /api/v1/care-reports?bookingId=&page=` (khách / manager / caregiver theo scope).
  - `POST /api/v1/care-reports` — request `{sessionId, overallCondition: STABLE|IMPROVED|DECLINED|NEEDS_ATTENTION, summary (@NotBlank, ≤ 4000), mealNotes?, medicationNotes?, mobilityNotes?, moodNotes?, recommendations? (≤ 2000)}` → DRAFT.
  - `PUT /api/v1/care-reports/{id}` — chỉ DRAFT (PUT toàn phần, có `version`).
  - `POST /api/v1/care-reports/{id}/photos` — multipart `files` (≤ 5 file / report tổng cộng, mỗi file ≤ 10 MB, jpeg / png / webp).
  - `DELETE /api/v1/care-reports/{id}/photos/{mediaFileId}` — chỉ DRAFT.
  - `POST /api/v1/care-reports/{id}/submit`.
- **Quy tắc nghiệp vụ:**
  1. Một report cho một session (UNIQUE). Tạo được khi session đã có (kể cả đang IN_PROGRESS). **Submit** chỉ khi session `ENDED`.
  2. Ảnh: Booking kiểm quyền (caregiver của session, report DRAFT) → gọi Media `POST /api/v1/media` bằng token SERVICE (`purpose=CARE_REPORT_IMAGE`, `referenceType=CARE_REPORT`, `referenceId=reportId`, `uploadedBy=sub`) **ngoài transaction** → lưu `care_report_media`. DB lỗi → xóa file Media (compensation).
  3. `SUBMITTED` là **bất biến** (O3): `PUT`, thêm / xóa ảnh → 409.
  4. Response đọc trả `photos[]` với presigned URL (Booking gọi Media `download-url` bằng token SERVICE, TTL 300s).
  5. Sau submit: thông báo khách `REPORT_SUBMITTED`.
- **DB (Booking):**
  - `care_reports`:
    - `report_id` PK, `session_id` UNIQUE FK, `occurrence_id`, `booking_id`, `caregiver_user_id`;
    - `overall_condition` CHECK, `summary` NN, `meal_notes`, `medication_notes`, `mobility_notes`, `mood_notes`, `recommendations`;
    - `status` CHECK (`DRAFT`, `SUBMITTED`), `submitted_at`;
    - BaseEntity, `version`.
  - `care_report_media`: `report_id` FK, `media_file_id` uuid, `display_order`, `created_at`; PK `(report_id, media_file_id)`.
- **Lỗi:** `REPORT_NOT_FOUND (5806, 404)`, `REPORT_ALREADY_SUBMITTED (5807, 409)`, `REPORT_SESSION_NOT_ENDED (5809, 409)`, `TOO_MANY_REPORT_PHOTOS (5810, 400)`, `MEDIA_SERVICE_UNAVAILABLE (5811, 503)`, `REPORT_EXISTS (5816, 409)`.
- **AC:**
  - Given session ENDED, When submit, Then SUBMITTED và khách nhận thông báo.
  - Given SUBMITTED, When PUT, Then 409.
  - Given khách khác, When xem report, Then 404.
  - Given upload ảnh thứ 6, Then 400.

#### FB-01 — Đánh giá caregiver + phản hồi của trung tâm + cập nhật rating
**Người:** [Người C] · **Ưu tiên:** P1 · **SP:** 5 · **Sprint:** S4 · **Phụ thuộc:** OPS-04, NTF-01

- **Role:** CUSTOMER (tạo); CENTER_MANAGER (phản hồi); ADMIN (ẩn); mọi user đã đăng nhập (đọc).
- **API** (`FeedbackController`, `/api/v1/feedbacks`):
  - `GET ?caregiverId=&centerId=&rating=&page=&size=` (công khai cho user đăng nhập: tên khách viết tắt "Nguyễn V. A.", ẩn bản HIDDEN).
  - `GET /me` (của tôi).
  - `GET /eligible?bookingId=` → caregiver tôi còn được đánh giá trong booking.
  - `POST` — request `{bookingId, caregiverId, rating (1..5 int), comment (≤ 2000)}`.
  - `POST /{id}/reply` — MANAGER trung tâm của booking, request `{reply (@NotBlank, ≤ 1000)}`, một lần.
  - `PATCH /{id}/visibility` — ADMIN, `{visible, reason}`.
- **Quy tắc nghiệp vụ** (D4, D5):
  1. Booking của khách, status ∈ {COMPLETED, CANCELLED}, và caregiver có ≥ 1 buổi `COMPLETED` trong booking (`booking_occurrences.caregiver_id`).
  2. Mỗi `(booking_id, caregiver_id)` đúng 1 đánh giá (UNIQUE). Không sửa / xóa sau khi tạo.
  3. Sau khi tạo / ẩn / hiện:
     - tính lại `avg` và `count` của caregiver trên các đánh giá VISIBLE;
     - gọi Provider `PUT /api/v1/caregivers/{id}/rating` (SERVICE, `{ratingAverage, ratingCount}`, C5.3) sau commit;
     - lỗi → `booking_outbox` retry.
  4. Provider cài endpoint `PUT /caregivers/{id}/rating` (SERVICE) — thuộc [Người C], làm trong task này.
  5. Thông báo: manager khi có đánh giá mới; khách + caregiver khi có phản hồi.
- **DB (Booking):** `feedbacks`:
  - `feedback_id` PK, `booking_id` FK, `caregiver_id`, `caregiver_user_id`, `customer_id`, `center_id`;
  - `rating smallint` CHECK 1..5, `comment`;
  - `reply`, `replied_by`, `replied_at`;
  - `visibility` CHECK (`VISIBLE`, `HIDDEN`), `hidden_reason`;
  - BaseEntity;
  - UNIQUE `(booking_id, caregiver_id)`, index `(caregiver_id, created_at)`, `(center_id)`.
- **Lỗi:** `REVIEW_NOT_ALLOWED (5900, 409)`, `REVIEW_ALREADY_EXISTS (5901, 409)`, `RATING_OUT_OF_RANGE (5902, 400)`, `REVIEW_NOT_FOUND (5903, 404)`, `REPLY_ALREADY_EXISTS (5904, 409)`.
- **AC:**
  - Given booking COMPLETED có CG1 (2 buổi) và CG2 (1 buổi), When khách đánh giá cả hai, Then 2 bản ghi và rating cả hai được cập nhật ở Provider.
  - Given đánh giá lần 2 cho CG1, Then 409.
  - Given CG3 chỉ có buổi bị hủy, Then 409 `REVIEW_NOT_ALLOWED`.
  - Given manager trung tâm khác phản hồi, Then 404.

#### FB-02 — Khiếu nại cấp 1 / escalate / Admin quyết định hoàn tiền
**Người:** [Người C] · **Ưu tiên:** P1 · **SP:** 8 · **Sprint:** S5 · **Phụ thuộc:** NTF-01, PAY-12

- **Role:** CUSTOMER (tạo, escalate khi đã RESOLVED); CENTER_MANAGER (xử lý cấp 1, escalate); ADMIN (cấp cao).
- **API** (`ComplaintController`, `/api/v1/complaints`):
  - `GET ?status=&level=&centerId=&page=&size=`:
    - khách: của mình;
    - manager: trung tâm mình, chỉ `level = CENTER` hoặc khiếu nại đã escalate từ trung tâm mình (chỉ đọc);
    - admin: tất cả.
  - `GET /{id}` (kèm `events[]`).
  - `POST` — CUSTOMER, request `{bookingId, occurrenceId?, targetType: CAREGIVER_SERVICE|CENTER, category: QUALITY|ATTITUDE|LATE|SAFETY|BILLING|OTHER, title (≤ 200), content (@NotBlank, ≤ 4000)}`.
  - `PATCH /{id}/accept` — MANAGER: `OPEN → IN_PROGRESS`.
  - `PATCH /{id}/resolve` — MANAGER, `{resolution (@NotBlank)}`: `IN_PROGRESS → RESOLVED`.
  - `POST /{id}/escalate` — MANAGER (`OPEN` / `IN_PROGRESS`) hoặc CUSTOMER (`RESOLVED`), `{reason (@NotBlank)}` → `ESCALATED`.
  - `PATCH /{id}/admin-resolve` — ADMIN, `{resolution (@NotBlank), refundAmount? (≥ 0)}`: `ESCALATED → RESOLVED_BY_ADMIN`.
- **Quy tắc nghiệp vụ:**
  1. Booking của khách, status ∈ {CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED}. `occurrenceId` (nếu có) thuộc booking.
  2. `targetType = CENTER` (khiếu nại chính trung tâm) → tạo thẳng ở `ESCALATED`, `level = ADMIN`; manager **không** xử lý, chỉ xem.
  3. Bảng chuyển trạng thái:

     | Từ | Sang | Ai | Điều kiện |
     |---|---|---|---|
     | — | OPEN | Khách | `target = CAREGIVER_SERVICE` |
     | — | ESCALATED | Khách | `target = CENTER` |
     | OPEN | IN_PROGRESS | Manager | cùng trung tâm |
     | IN_PROGRESS | RESOLVED | Manager | có `resolution` |
     | OPEN, IN_PROGRESS | ESCALATED | Manager | có `reason` |
     | RESOLVED | ESCALATED | Khách chủ | có `reason` (O4) |
     | ESCALATED | RESOLVED_BY_ADMIN | Admin | có `resolution`; `refundAmount` tùy chọn |

     Chuyển khác → 409.
  4. `refundAmount > 0`:
     - **trước** transaction: gọi Payment `GET /api/v1/payments?bookingId=` (SERVICE; C2.6) để lấy tổng đã trả − đã hoàn. Vượt → 409 `REFUND_AMOUNT_EXCEEDS_PAID`, trạng thái không đổi;
     - **sau** commit: gọi `POST /refunds/complaints` (PAY-12) qua `booking_outbox` (idempotent theo `complaintId`).
  5. Mọi bước ghi `complaint_events` (actor, action, note) và gửi thông báo cho khách + manager.
  6. Mã `complaint_code` = `CP-yyyyMMdd-XXXX`.
- **DB (Booking):**
  - `complaints`:
    - `complaint_id` PK, `complaint_code` UNIQUE, `booking_id` FK, `occurrence_id`, `customer_id`, `center_id`, `caregiver_id`;
    - `target_type`, `category`, `title`, `content`;
    - `status` CHECK (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `ESCALATED`, `RESOLVED_BY_ADMIN`), `level` CHECK (`CENTER`, `ADMIN`);
    - `resolution`, `resolved_by`, `resolved_at`;
    - `escalated_at`, `escalate_reason`, `refund_amount`;
    - BaseEntity, `version`.
  - `complaint_events`: `event_id` PK, `complaint_id` FK, `actor_id`, `action`, `from_status`, `to_status`, `note`, `created_at`.
- **Lỗi:** `COMPLAINT_NOT_FOUND (5920, 404)`, `INVALID_COMPLAINT_TRANSITION (5921, 409)`, `COMPLAINT_CONTENT_REQUIRED (5922, 400)`, `REFUND_AMOUNT_EXCEEDS_PAID (5923, 409)`, `RESOLUTION_REQUIRED (5924, 400)`, `COMPLAINT_BOOKING_INVALID (5925, 409)`.
- **AC:**
  - Given khiếu nại CAREGIVER_SERVICE, When manager accept → resolve, Then RESOLVED; khách escalate → ESCALATED; admin resolve kèm hoàn 200.000 → RESOLVED_BY_ADMIN và Payment nhận refund 1 lần.
  - Given khiếu nại CENTER, Then manager PATCH accept trả 409 / 404.
  - Given hoàn vượt số đã trả, Then 409 và trạng thái không đổi.

#### IAM-05 — Quản lý user cho Admin: lọc, tổng số, lý do khóa
**Người:** [Người C] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S5 · **Phụ thuộc:** —

- **Role:** ADMIN, CENTER_MANAGER (caregiver trung tâm mình).
- **API:**
  - `GET /api/v1/users?role=&status=&centerId=&keyword=&page=&size=&sort=` → `PageResponse<UserResponse>` (`content`, `page`, `size`, `totalElements`, `totalPages`).

    > ⚠️ Đổi kiểu response → cập nhật FE contract. Thêm field, không đổi field cũ.
  - `PATCH /api/v1/users/{id}/status` — request body `{status, reason}`. Giữ query param `status` thêm một sprint cho FE. LOCKED bắt buộc `reason`.
- **Quy tắc nghiệp vụ:**
  1. JPA Specification; `keyword` khớp email / tên (lower).
  2. Lưu `users.status_reason`; audit `STATUS_CHANGED` ghi lý do vào `detail`.
  3. MANAGER: ép `centerId` = của mình và `role=CAREGIVER`.
- **DB (Identity):** `users` + `status_reason varchar(500)`.
- **Lỗi:** `LOCK_REASON_REQUIRED (1118, 400)`, `INVALID_PAGE (1107, 400)`.
- **AC:**
  - Given admin lọc `role=CUSTOMER&status=LOCKED`, Then đúng danh sách và `totalElements`.
  - Given khóa không lý do, Then 400.

#### PRV-04 — Vòng đời trung tâm + khóa dây chuyền + manager sửa trung tâm
**Người:** [Người C] · **Ưu tiên:** P1 · **SP:** 5 · **Sprint:** S6 · **Phụ thuộc:** IAM-06, BK-04 (`system-cancellations`), PAY-10

- **Role:** ADMIN (trạng thái); CENTER_MANAGER (sửa thông tin trung tâm mình).
- **API:**
  - `PATCH /api/v1/care-centers/{centerId}/status` (hiện có) — `{status, reason, version}`.
  - `PUT /api/v1/care-centers/me` — MANAGER, `{centerName?, phoneNumber, address, operatingHours}` (không email / status / manager).
- **Quy tắc nghiệp vụ** (D10):

  | Từ | Sang | Điều kiện | Side effect |
  |---|---|---|---|
  | PENDING | ACTIVE | có manager, tài khoản manager ACTIVE | thông báo manager |
  | PENDING | REJECTED | lý do | — |
  | ACTIVE | SUSPENDED (khóa) | lý do | khóa tài khoản trung tâm (IAM-06) + hủy hệ thống booking (BK-04, `CENTER_LOCKED` → hoàn 100%) |
  | SUSPENDED | ACTIVE | — | mở khóa tài khoản bị khóa bởi trung tâm (IAM-06); booking đã hủy **không** khôi phục |
  | ACTIVE | INACTIVE | lý do | ngừng nhận booking mới; booking hiện có tiếp tục |
  | INACTIVE | ACTIVE | — | — |
  | PENDING / ACTIVE / INACTIVE / SUSPENDED / REJECTED | ARCHIVED | không còn booking PENDING / AWAITING_DEPOSIT / CONFIRMED / IN_PROGRESS (Booking `GET /bookings?centerId=&status=` bằng token ADMIN) | `deleted_at` |

  1. Side effect gọi mạng **sau commit** qua `provider_outbox` (retry, idempotent theo `(centerId, event, version)`).
  2. Manager sửa trung tâm chỉ khi trung tâm không ARCHIVED / REJECTED.
- **DB (Provider):** `provider_outbox`: `id` PK, `event_type`, `payload` jsonb, `status`, `attempts`, `next_attempt_at`, `last_error`, `created_at`.
- **Lỗi:** `CENTER_INVALID_TRANSITION (4119, 409)`, `CENTER_MANAGER_REQUIRED (4120, 409)`, `CENTER_HAS_ACTIVE_BOOKINGS (4121, 409)`, `CENTER_STATUS_REASON_REQUIRED (4111, 400)`.
- **AC:**
  - Given trung tâm ACTIVE có 3 booking CONFIRMED, When admin khóa, Then mọi tài khoản trung tâm LOCKED, 3 booking bị hủy buổi tương lai và hoàn 100%.
  - When mở khóa, Then tài khoản mở lại, booking vẫn CANCELLED.
  - Given REJECTED → ACTIVE, Then 409.

#### IAM-06 — Khóa / mở tài khoản theo trung tâm
**Người:** [Người C] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S6 · **Phụ thuộc:** IAM-01, IAM-02

- **Role:** SERVICE (Provider), ADMIN.
- **API:** `PATCH /api/v1/users/centers/{centerId}/status` — request `{status: LOCKED|ACTIVE, reason}` → `{affectedUsers}`.
- **Quy tắc nghiệp vụ:**
  1. **LOCKED:** mọi user chưa xóa có `center_id = centerId` và đang ACTIVE → LOCKED, `lock_source = CENTER`, `status_reason = reason`, `authzVersion++`, thu hồi session (IAM-02), audit từng user.
  2. **ACTIVE:** chỉ mở user có `lock_source = CENTER` (không mở người bị admin khóa riêng); xóa `lock_source`.
  3. Không khóa ADMIN (bảo vệ last admin không liên quan vì ADMIN không có `center_id`). Idempotent.
- **DB (Identity):** `users` + `lock_source varchar(20)` CHECK (`ADMIN`, `CENTER`) null.
- **Lỗi:** `LOCK_REASON_REQUIRED (1118, 400)`.
- **AC:**
  - Given trung tâm có 1 manager + 4 caregiver (1 người đã bị admin khóa riêng), When khóa rồi mở, Then 4 người mở lại, người bị khóa riêng vẫn LOCKED.

#### STAT-01 — Thống kê vận hành trung tâm / hệ thống
**Người:** [Người C] · **Ưu tiên:** P2 · **SP:** 5 · **Sprint:** S6 · **Phụ thuộc:** OPS-04, FB-01, FB-02

- **Role:** CENTER_MANAGER (trung tâm mình), ADMIN.
- **API:**
  - Booking: `GET /api/v1/statistics/centers?from=&to=&centerId=` → `{bookingsByStatus, occurrences: {completed, cancelled, missed, customerNoShow}, completionRate, avgRating, reviewCount, complaints: {open, inProgress, resolved, escalated}, topCaregivers[{caregiverId, completedSessions, avgRating}]}`.
  - Booking: `GET /api/v1/statistics/system?from=&to=` (ADMIN) → như trên cho toàn hệ thống + `byCenter[]` phân trang.
  - Provider: `GET /api/v1/care-centers/statistics` (ADMIN) → `{centersByStatus, caregiversByStatus, caregiversByVerification}`.
  - Identity: `GET /api/v1/users/statistics` (ADMIN) → `{usersByRole, usersByStatus, newCustomers(from, to)}` — [Người C] cài (Identity file `UserController`, phối hợp [Người B]).
- **Quy tắc nghiệp vụ:**
  1. Doanh thu **không** nằm ở đây (PAY-15). FE dashboard gọi song song các endpoint.
  2. MANAGER bị ép `centerId` theo claim.
  3. Khoảng ngày ≤ 366, quy đổi giờ VN.
  4. Truy vấn tổng hợp (`count` / `group by`), không load entity.
- **Lỗi:** `INVALID_DATE_FILTER (5219, 400)`, `CENTER_SCOPE_REQUIRED (5007, 403)`.
- **AC:**
  - Given dữ liệu mẫu, When manager C1 gọi, Then chỉ số liệu C1.
  - Given admin gọi `system`, Then tổng `byCenter` = số toàn hệ thống.

---

## Lịch sprint của [Người C]

| Sprint | Task (SP) | Mục tiêu demo chung cuối sprint |
|---|---|---|
| **S1** | CR-01 (5), IAM-02 (5), IAM-03 (3), IAM-04 (3) — 16 SP | Hết toàn bộ lỗ hổng SEC-01…08; có token SERVICE; PaymentService chạy trong Docker; publish stub contract §7 |
| **S2** | PRV-06 (3), MED-01 (5), 🔒 NTF-01 (5) — 13 SP | Báo giá có giá từng buổi; khách nhận URL VNPay; caregiver acknowledge; thông báo in-app + SSE |
| **S3** | OPS-03 (3), NTF-02 (3), PRV-05 (5) — 11 SP | **Luồng chính end-to-end**: đặt → duyệt → cọc VNPay sandbox → CONFIRMED → check-in → task → check-out → COMPLETED; xác minh chứng chỉ 2 bước |
| **S4** | OPS-05 (5), FB-01 (5) — 10 SP | Kiểm lịch rảnh / kỹ năng / nợ khi đặt; MISSED + khách vắng tự động hoàn tiền; care report có ảnh; đánh giá caregiver; preview hoàn tiền đúng bảng 2.2 |
| **S5** | FB-02 (8), IAM-05 (3) — 11 SP | Tìm caregiver đủ dịch vụ / lịch trống; hủy từng buổi / cả booking có hoàn tiền; REMAINING + chặn nợ; khiếu nại có escalate |
| **S6** | PRV-04 (5), IAM-06 (3), STAT-01 (5) — 13 SP | Khóa trung tâm dây chuyền (hủy hệ thống + hoàn 100%); lịch sử booking; đối soát; dashboard doanh thu / vận hành; e2e toàn hệ thống PASSED |
| **Tổng** | **74** |  |

---

## Contract [Người C] cài đặt hoặc sử dụng (trích §7)

Ký hiệu `[A]`, `[B]`, `[C]` là người cài đặt / gọi. Chi tiết đầy đủ và các contract còn lại: `docs/WORK_PLAN.md` §7.

### C2 — Booking → Payment

| # | Chữ ký | Cài đặt | Gọi bởi | Khi nào |
|---|---|---|---|---|
| C2.4 | `POST /api/v1/refunds/complaints` (SERVICE) `{bookingId, complaintId, amount, decidedBy, note}` → `RefundBatchResponse` | [A] PAY-12 | [C] FB-02 | Sau admin-resolve |
| C2.6 | `GET /api/v1/payments/me/outstanding` (CUSTOMER, token khách chuyển tiếp) → `{hasDebt, overdueInvoices[], pendingInvoices[]}`; `GET /api/v1/payments?bookingId=` (thêm quyền SERVICE) → danh sách payment + `refundedAmount` | [A] PAY-11, PAY-16 | [B] BK-02; [C] FB-02 | Tạo booking; trước admin-resolve |

### C4 — Thông báo (BookingService)

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C4.1 | Java trong Booking: `NotificationService.notify(List<UUID> recipientUserIds, NotificationType type, Map<String,Object> params, String refType, UUID refId)` — chỉ gọi sau commit | [C] NTF-01 | [B] (BK-03/04/05, OPS-01/02/04/06, PAY-08), [C] (OPS-03/05, FB, NTF-02) |
| C4.2 | `POST /api/v1/notifications` (SERVICE) `{recipientUserIds[], type, title, body, refType, refId}` → `{created}` | [C] NTF-01 | [A] PAY-06/10/11/14 |
| C4.3 | `GET /api/v1/notifications/stream` (SSE) — event `notification`, `session-update`, `heartbeat` | [C] NTF-01 | FE |

### C5 — Provider ↔ Booking

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C5.3 | `PUT /api/v1/caregivers/{caregiverId}/rating` (SERVICE) `{ratingAverage (0..5, 2 số lẻ), ratingCount}` → 200 | [C] FB-01 | [C] FB-01 (Booking) |
| C5.4 | `POST /api/v1/bookings/system-cancellations` (SERVICE) `{centerId?, caregiverId?, reason: CENTER_LOCKED \| CAREGIVER_DEACTIVATED, note}` → `{affectedBookings, affectedOccurrences}` | [B] BK-04 | [C] PRV-04 |
| C5.6 | `GET /api/v1/care-centers/{id}` (SERVICE được phép) → `managerUserId` | [C] PRV-06 | [C] NTF-02 |

### C6 — Provider → Identity

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C6.1 | `PATCH /api/v1/users/centers/{centerId}/status` (SERVICE / ADMIN) `{status, reason}` → `{affectedUsers}` | [C] IAM-06 | [C] PRV-04 |

### C7 — CareRecipient ↔ Booking

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C7.1 | `GET /api/v1/care-recipients/{id}/snapshot`, `GET /{id}/access?userId&minLevel` (SERVICE) | [C] CR-01 | [B] Booking `create` |
| C7.2 | `GET /api/v1/bookings/caregiver-access?caregiverUserId&recipientId` (SERVICE) → `{allowed}` theo buổi | [B] BK-07 | [C] CareRecipient `getForCaregiver` |

### C8 — Booking / Provider → Media

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C8.1 | `POST /api/v1/media` (SERVICE, multipart `file`, `purpose`, `uploadedBy`, `referenceType`, `referenceId`; `sourceService` = `client_id`) | [C] MED-01 | [C] OPS-05, Provider media |
| C8.2 | `GET /api/v1/media/{id}/download-url`, `DELETE /api/v1/media/{id}` (SERVICE, cùng `source_service`) | [C] MED-01 | như trên |

### Hàm nội bộ dùng chung trong BookingService (cần thống nhất giữa [B] và [C])

| Hàm | Chủ | Dùng bởi |
|---|---|---|
| `OccurrenceTransitions.cancel / markMissed / markNoShow / start / complete / acknowledge / resetAcknowledgement` | [B] OPS-01/02/04/06, BK-03 | [B]; [C] chỉ đọc trạng thái (OPS-03/05, FB-01) |
| `BookingOutbox.enqueue(eventType, payload)` + dispatcher | [B] OPS-04 (tạo) | [B] BK-03/04, OPS-06; [C] FB-01/02 (rating, hoàn tiền khiếu nại) |
