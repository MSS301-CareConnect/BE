# CareConnect — Implementation checklist

Tracker riêng của CareConnect. Chỉ đánh hoàn thành khi hạng mục đã triển khai và kiểm chứng; cập nhật ngày trong cùng thay đổi. Các scope nghiệp vụ tiếp theo bổ sung khi được chốt.

- [x] Tạo hướng dẫn dùng chung `AGENTS.md`, điều chỉnh quy ước từ backend nguồn cho CareConnect/microservice, ghi schema IdentityService và quyết định còn thiếu; đối chiếu source guide, ERD và skeleton hiện có — hoàn thành 2026-09-30.
- [x] Chốt contract IdentityService: ACTIVE/LOCKED, permission catalog, một trung tâm cho manager/caregiver, ADMIN tạo manager và manager tạo caregiver, kiểm trạng thái/quyền hiện hành, schema SQL không Flyway — hoàn thành 2026-09-30.
- [x] Triển khai IdentityService port 8081 và bổ sung dependencies Spring Security Resource Server, Redis, Actuator, MapStruct cùng test dependencies phù hợp — hoàn thành 2026-09-30.
- [x] Đăng ký CUSTOMER, xác thực email OTP Brevo, gửi lại OTP, quên/đặt lại/đổi mật khẩu; OTP giới hạn lượt thử và consume một lần — hoàn thành 2026-09-30.
- [x] JWT RS256/JWKS, web HttpOnly cookies + CSRF, mobile bearer, refresh rotation, logout/logout-all và thu hồi phiên khi khóa/đổi mật khẩu — hoàn thành 2026-09-30.
- [x] RBAC và quyền hiện hành, quản lý tài khoản theo phạm vi trung tâm, soft delete, audit và bảo vệ ADMIN hoạt động cuối cùng — hoàn thành 2026-09-30.
- [x] Schema SQL thủ công, seed role/permission, bootstrap ADMIN qua env, Docker dev và hướng dẫn cấu hình/chạy — hoàn thành 2026-09-30.
- [x] Kiểm chứng 44 tests với PostgreSQL/Redis thật, Maven package và JAR tại 8081; kiểm web CSRF/login/refresh/logout, JWKS và Redis outage trả 503; dọn môi trường test riêng — hoàn thành 2026-09-30.
- [x] Gộp cấu hình Spring vào một application.yaml, chuyển Swagger/Secure cookie sang env, chia .env.example theo nhóm; bổ sung constructors/Builder/FieldDefaults cho entity, giữ builder defaults và MapStruct mapping field kế thừa; clean package với 44 tests mặc định thành công, JAR chỉ có một application.yaml — hoàn thành 2026-10-01.
- [x] Ghi chú từng biến JWT issuer/audience/key ID/private key/public key trong .env.example, đối chiếu luồng issue/validate/JWKS và script tạo khóa; giữ nguyên giá trị cấu hình — hoàn thành 2026-10-01.
- [x] Hỗ trợ Windows PowerShell 5.1 cho scripts dev; tạo RSA PEM bằng Java 21, kiểm cặp khóa/chữ ký RS256, từ chối ghi đè và xác nhận gitignore; kiểm syntax/env split của Start-Dev trên 5.1 — hoàn thành 2026-10-01.
- [x] Đồng bộ Compose và env mẫu với tên DB CARECONNECT_IDENTITY, cấu hình IDENTITY_DB_NAME và hướng dẫn schema/volume; kiểm docker compose config — hoàn thành 2026-10-01.
- [x] Viết lại `AGENTS.md` theo bố cục và quy ước `backend/CLAUDE.md` (§3 Code Conventions, §4 Authentication Flow, §7 Rules) cho microservice, bỏ Flyway, ghi ngữ cảnh role/chức năng → domain sở hữu và danh sách code IdentityService lệch quy ước (§8); đối chiếu với code hiện có — hoàn thành 2026-10-01.
- [x] Refactor IdentityService theo `AGENTS.md` (style, controller mỏng + `@CurrentUserId`, tầng service, MapStruct cho entity liên kết/response, magic string, `SecurityErrorHandler`, `@Repository` + `@Param`, xóa ErrorCode kế thừa AIMA), giữ nguyên API contract; 45/45 tests trên H2 và PostgreSQL/Redis thật, package thành công; ghi nhận API gateway sẽ có (chưa tạo) — hoàn thành 2026-10-01.
- [x] Tách lỗi CSRF thành `CSRF_INVALID` 1111 (403) khỏi `UNAUTHORIZED`; Swagger có scheme `csrf` (header `X-XSRF-TOKEN`) cho mọi request ghi trừ `/auth/mobile/*` để test luồng web/đăng ký trên Swagger; 47/47 tests H2 — hoàn thành 2026-10-01.
- [x] IdentityService: API nội bộ `/internal/users/{id}`, `/by-email`, `/batch` bảo vệ bằng `X-Internal-Secret` (chain riêng, fail-closed khi secret trống); access token thêm audience chung `careconnect-api` (refresh không có, token cũ vẫn hợp lệ ở Identity); OpenAPI có scheme `internalSecret` và server gateway tùy chọn; 56/56 tests H2 — hoàn thành 2026-10-03.
- [x] CareRecipientService (port 8082, PostgreSQL riêng 5442, `schema/care_recipient.sql`): hồ sơ người bệnh, thuốc, liên hệ khẩn cấp, người thân OWNER/EDITOR/VIEWER (quản lý chính, OWNER cuối cùng, 404 khi không có quyền), admin xem toàn hệ thống, API nội bộ snapshot/access/batch; JWT qua JWKS audience `careconnect-api`; Feign → Identity (X-Internal-Secret, ErrorDecoder, timeout, circuit breaker + fallback); 47/47 tests trên H2 và PostgreSQL 17 — hoàn thành 2026-10-03.
- [x] Tạo API gateway `ApiGateway/` (Spring Cloud Gateway Server MVC, port 8080): route `/api/v1/**` cắt prefix, kiểm JWT sớm, thay `X-User-*`, chặn `/internal`, CORS tập trung + xóa Origin, X-Request-Id, lỗi 401/403/404/503/504 theo APIResponse, Swagger tổng hợp, virtual threads; cookie Identity `Path=/` đi qua không cần rewrite; 14/14 tests — hoàn thành 2026-10-03.
- [x] E2E local với PostgreSQL/Redis thật (compose project riêng): luồng mobile, luồng web cookie + CSRF + refresh + logout qua gateway, chặn `/internal`, fallback khi tắt Identity; Identity 56/56 tests trên PostgreSQL 17 + Redis 7 — hoàn thành 2026-10-03.
- [x] Mobile: đăng ký / OTP / quên-đặt lại mật khẩu không cần CSRF khi request không mang cookie (web có cookie giữ nguyên); 58/58 tests Identity. Script e2e `scripts/e2e/Run-E2E.ps1` (dựng hạ tầng riêng, chạy 3 JAR, phase a–d) chạy pass — hoàn thành 2026-10-03.
- [x] Rà soát tầng mapper MapStruct cả IdentityService và CareRecipientService theo quy tắc chung (MapStructConfig, IgnoreServerManagedFields, bỏ @Mapping thừa, list mapper cho con, logic quyền hiệu lực về entity); quy tắc ghi vào AGENTS.md §3; Identity 66/66, CareRecipient 55/55 (H2 + PostgreSQL), e2e PASSED — hoàn thành 2026-10-03 (`docs/2026-10-03-MAPPER_REVIEW.md`).
- [x] ProviderService: API nội bộ chỉ đọc cho Booking (`/internal/caregivers/{id}`, `/internal/caregivers/by-user/{userId}`, `/internal/centers/{id}`, `X-Internal-Secret`; `careServiceIds = null` khi chưa quản lý kỹ năng); 17/17 tests (12 cũ + 5 mới) — hoàn thành 2026-10-09.
- [x] BookingService (port 8085, PostgreSQL riêng 5445, `schema/booking.sql`): booking + buổi chăm sóc (ONE_TIME/DAILY/WEEKLY theo Asia/Ho_Chi_Minh, lưu UTC), state machine tập trung (`BookingAction`) + `booking_status_history`, kiểm trùng caregiver/người bệnh (advisory lock), Idempotency-Key, hạn duyệt/hạn cọc + scheduler EXPIRED, API customer / center manager / admin / caregiver / nội bộ; Feign → Identity, CareRecipient, Provider (secret riêng, ErrorDecoder, timeout, circuit breaker); Catalog qua port tạm (giá NULL); 66/66 tests trên H2 và PostgreSQL 17 — hoàn thành 2026-10-09 (`docs/2026-10-09-PLAN_BOOKING.md`).
- [x] CareRecipientService: caregiver xem hồ sơ người bệnh (`GET /caregiver/care-recipients/{id}`) khi BookingService xác nhận phân công (Feign `caregiver-access`, fail-closed 503); 59/59 tests (55 cũ + 4 mới) — hoàn thành 2026-10-09.
- [x] ApiGateway: route `/api/v1/bookings/**`, `/center/bookings/**`, `/admin/bookings/**`, `/caregiver/occurrences/**` → Booking, `/caregiver/care-recipients/**` → CareRecipient, Swagger `/api-docs/booking`; 16/16 tests — hoàn thành 2026-10-09.
- [x] E2E 5 service (Identity, CareRecipient, Provider, Booking, Gateway) trên PostgreSQL/Redis thật: thêm phase e (luồng booking qua gateway → accept → đặt cọc → caregiver xem lịch + hồ sơ) và f (CareRecipient tắt → 503, không tạo booking); a 19, b 14, c 10, e 30, d 4, f 2 — E2E PASSED — hoàn thành 2026-10-09.
- [x] Stack Docker tổng (`compose.yaml`, project `careconnect`): include `<Service>/compose.stack.yaml` (biến đọc từ `.env` riêng từng service), `Dockerfile` + `.dockerignore` cho 6 service, gọi nhau bằng tên service, health check actuator/pg_isready/redis, dùng lại 6 volume dữ liệu cũ (`external`), schema tự áp chỉ khi volume trống; `start.bat` / `stop.bat` / `DOCKER.md`. Kiểm chứng: stack cô lập trên volume mới (12/12 healthy, internal API + login mobile/JWT qua gateway tới 5 service) và stack thật trên dữ liệu dev (12/12 healthy, không init lại, stop giữ volume) — hoàn thành 2026-10-09.
- [ ] Bảng `caregiver_services` + API quản lý kỹ năng caregiver trong ProviderService (đợt riêng); API kiểm khung giờ làm việc cho Booking (`availability_slot_id`).
- [ ] Booking: nối CareCatalogService (báo giá, % đặt cọc), Payment (thay `deposit-paid`, hoàn tiền theo `RefundPolicy`), bắt đầu/kết thúc ca, `occurrence_services`, `care_reports`, `feedbacks`.
- [ ] Chuyển toàn bộ service sang Flyway trong một đợt riêng (nợ kỹ thuật).
- [ ] Đồng bộ `recipient_managers` khi user bị khóa/xóa ở Identity (event qua broker + outbox).
- [ ] FE dùng gateway: Vite proxy `/api` → gateway `/api/v1`.
- [ ] Rate limiting tại gateway (chưa chốt).
- [ ] Kiểm chứng gửi OTP với Brevo account/sender thật sau khi có cấu hình triển khai; chốt TTL/chính sách production.
- [ ] Tích hợp contract CenterService để xác minh UUID trung tâm và đồng bộ vòng đời membership/trung tâm.
- [ ] Chốt audience và cơ chế kiểm trạng thái/version hiện hành tại các domain service trước khi cam kết thu hồi ngay toàn hệ thống.
- [ ] Bổ sung key ring/overlap nếu cần rotation RSA không gián đoạn production.

---

## Kế hoạch triển khai 2026-10-21 (`docs/WORK_PLAN.md`)

Mỗi task xong và đã kiểm chứng (DoD ở `docs/WORK_PLAN.md` §1.6) thì đánh `[x]` và ghi ngày hoàn thành, ví dụ: `— hoàn thành 2026-10-28`. Chi tiết từng task nằm trong file của người phụ trách. Lỗi bảo mật SEC-xx: `docs/GAP_ANALYSIS.md` §7.

### [Người A] — Payment & tài chính (91 SP · `docs/WORK_PLAN_A_PAYMENT.md`)

**Sprint 1**

- [ ] **PAY-01** — Dựng PaymentService · P0 · 5 SP
- [ ] **PAY-02** — PlatformPolicy có hiệu lực theo thời gian · P0 · 5 SP
- [ ] **PAY-08 (bước 1)** — Hotfix `deposit-paid` chỉ cho ADMIN, sửa test (SEC-01) · P0 · tính trong 5 SP của PAY-08

**Sprint 2**

- [ ] **PAY-03** — Chính sách hiệu lực + snapshot vào booking · P0 · 3 SP
- [ ] **PAY-04** — Phân bổ cọc theo buổi · P0 · 3 SP
- [ ] **PAY-05** — Tạo phiên thanh toán cọc VNPay (15 phút) · P0 · 8 SP

**Sprint 3**

- [ ] **PAY-06** — IPN + return URL, idempotency, đối chiếu tiền · P0 · 8 SP
- [ ] **PAY-07** — Job hết hạn phiên + đối soát querydr · P1 · 3 SP
- [ ] **PAY-08** — Thay stub `deposit-paid` bằng `deposit-confirmations` + `payment-context` (role SERVICE) · P0 · 5 SP

**Sprint 4**

- [ ] **PAY-09** — Preview-cancel: bộ tính phí / hoàn theo bảng 2.2 · P0 · 8 SP
- [ ] **PAY-10** — Thực hiện hoàn tiền + API refund VNPay · P0 · 8 SP

**Sprint 5**

- [ ] **PAY-11** — Thanh toán REMAINING, hạn 48h, nợ · P1 · 8 SP
- [ ] **PAY-12** — Phí CUSTOMER_NO_SHOW + hoàn tiền theo khiếu nại · P1 · 3 SP
- [ ] **PAY-13** — Hoa hồng & sổ cái (ledger) · P1 · 5 SP

**Sprint 6**

- [ ] **PAY-14** — Đối soát (Settlement) theo kỳ · P2 · 8 SP
- [ ] **PAY-15** — Thống kê doanh thu nền tảng / trung tâm · P2 · 5 SP
- [ ] **PAY-16** — Lịch sử giao dịch · P1 · 3 SP
- [ ] **PAY-17** — Kiểm thử end-to-end Payment + tài liệu sandbox · P1 · 3 SP

### [Người B] — Luồng booking end-to-end, catalog & tìm kiếm (104 SP · `docs/WORK_PLAN_B_BOOKING.md`)

**Sprint 1**

- [ ] **IAM-01** — Token service (client-credentials, role SERVICE) · P0 · 5 SP
- [ ] **GW-01** — Gateway: route Payment, IPN public, CORS, SSE · P0 · 3 SP
- [ ] **BK-07** — Sửa phạm vi occurrence + `caregiver-access` (SEC-03, SEC-09) · P0 · 3 SP
- [ ] **BK-08** — Tách `BookingOccurrenceController`, chuẩn hóa layer · P1 · 2 SP
- [ ] **CAT-01** — Sửa lỗi Catalog + quote xác định · P0 · 3 SP

**Sprint 2**

- [ ] **CAT-02** — Duyệt danh mục / dịch vụ trung tâm cho khách, vô hiệu hóa an toàn · P1 · 3 SP
- [ ] **PRV-01** — Kỹ năng caregiver (`caregiver_services`) · P0 · 5 SP
- [ ] **BK-01** — Giá từng buổi + API báo giá tạm tính + `payment-context` · P0 · 5 SP
- [ ] **OPS-01** — Caregiver xác nhận nhận ca + chi tiết ca · P1 · 3 SP

**Sprint 3**

- [ ] **OPS-02** — Bắt đầu ca (check-in), sinh checklist · P0 · 8 SP
- [ ] **OPS-04** — Kết thúc ca, hoàn tất booking (FINALIZE), tạo hóa đơn REMAINING · P0 · 5 SP
- [ ] **PRV-03** — Sửa lỗi ca làm việc (capacity, múi giờ, đăng ký lại) · P1 · 5 SP

**Sprint 4**

- [ ] **PRV-02** — Lịch rảnh + ngày nghỉ caregiver, API kiểm tra lịch · P0 · 8 SP
- [ ] **BK-02** — Kiểm tra khi tạo / duyệt booking: lịch rảnh, kỹ năng, nợ, giới hạn PENDING · P0 · 5 SP
- [ ] **OPS-06** — Job MISSED + báo khách vắng mặt (CUSTOMER_NO_SHOW) · P0 · 5 SP

**Sprint 5**

- [ ] **SRCH-01** — Tìm caregiver (giao tập dịch vụ, lọc, sắp xếp, lịch trống) + chi tiết công khai · P0 · 8 SP
- [ ] **BK-03** — Khách hủy booking / từng buổi + preview-cancel · P0 · 8 SP
- [ ] **SRCH-02** — Danh sách trung tâm ACTIVE cho khách (lọc khu vực, dịch vụ, giá) · P1 · 3 SP

**Sprint 6**

- [ ] **BK-04** — Trung tâm từ chối / hủy, hủy hệ thống (khóa trung tâm) + hoàn tiền · P0 · 5 SP
- [ ] **BK-05** — Audit đổi caregiver + lịch sử trạng thái booking · P1 · 3 SP
- [ ] **BK-06** — Lịch sử booking: lọc, sắp xếp, phân trang · P1 · 3 SP
- [ ] **BK-09** — Chống trùng lịch tầng DB + scheduler nhiều instance · P2 · 3 SP
- [ ] **INF-01** — Tài liệu chung, e2e, Docker cập nhật theo kiến trúc mới · P1 · 3 SP

### [Người C] — Hậu mãi, chất lượng & bảo mật (74 SP · `docs/WORK_PLAN_C_AFTERCARE.md`)

**Sprint 1**

- [ ] **CR-01** — CareRecipient: chặn IDOR snapshot/access/batch (SEC-02) + INACTIVE · P0 · 5 SP
- [ ] **IAM-02** — Refresh an toàn: kiểm `authz_version`, nguyên tử, thu hồi khi reuse / đổi mật khẩu / khóa (SEC-05) · P0 · 5 SP
- [ ] **IAM-03** — Hủy phiên vô hiệu access, IP tin cậy, thu hồi role tăng version (SEC-06) · P0 · 3 SP
- [ ] **IAM-04** — Phân quyền endpoint tra cứu user (SEC-04) · P0 · 3 SP

**Sprint 2**

- [ ] **PRV-06** — Provider: sửa phạm vi + lách xác minh + quy ước (SEC-08) · P0 · 3 SP
- [ ] **MED-01** — Media: quyền sở hữu qua service, đổi dải lỗi 7xxx (SEC-07) · P0 · 5 SP
- [ ] **NTF-01** — Module thông báo: bảng, API, SSE, API cho service · P0 · 5 SP

**Sprint 3**

- [ ] **OPS-03** — Cập nhật task, ghi chú tình trạng, theo dõi realtime · P0 · 3 SP
- [ ] **NTF-02** — Gắn thông báo vào các sự kiện nghiệp vụ + nhắc ca · P1 · 3 SP
- [ ] **PRV-05** — Xác minh chứng chỉ 2 bước + audit hồ sơ caregiver · P1 · 5 SP

**Sprint 4**

- [ ] **OPS-05** — Care report: nháp, ảnh, gửi, khách xem · P0 · 5 SP
- [ ] **FB-01** — Đánh giá caregiver + phản hồi của trung tâm + cập nhật rating · P1 · 5 SP

**Sprint 5**

- [ ] **FB-02** — Khiếu nại cấp 1 / escalate / Admin quyết định hoàn tiền · P1 · 8 SP
- [ ] **IAM-05** — Quản lý user cho Admin: lọc, tổng số, lý do khóa · P1 · 3 SP

**Sprint 6**

- [ ] **PRV-04** — Vòng đời trung tâm + khóa dây chuyền + manager sửa trung tâm · P1 · 5 SP
- [ ] **IAM-06** — Khóa / mở tài khoản theo trung tâm · P1 · 3 SP
- [ ] **STAT-01** — Thống kê vận hành trung tâm / hệ thống · P2 · 5 SP

### Mốc demo chung cuối mỗi sprint

- [ ] **S1** — Hết lỗ hổng SEC-01…08; có token SERVICE; PaymentService chạy trong Docker; stub contract §7 đã publish.
- [ ] **S2** — Báo giá có giá từng buổi; khách nhận URL VNPay; caregiver xác nhận nhận ca; thông báo in-app + SSE.
- [ ] **S3** — Luồng chính end-to-end: đặt → duyệt → cọc VNPay sandbox → CONFIRMED → check-in → task → check-out → COMPLETED; xác minh chứng chỉ 2 bước.
- [ ] **S4** — Kiểm lịch rảnh / kỹ năng / nợ khi đặt; MISSED + khách vắng tự hoàn tiền; care report có ảnh; đánh giá caregiver; preview hoàn tiền đúng bảng 2.2.
- [ ] **S5** — Tìm caregiver đủ dịch vụ / lịch trống; hủy từng buổi / cả booking có hoàn tiền; REMAINING + chặn nợ; khiếu nại có escalate.
- [ ] **S6** — Khóa trung tâm dây chuyền; lịch sử booking; đối soát; dashboard doanh thu / vận hành; e2e toàn hệ thống PASSED.
