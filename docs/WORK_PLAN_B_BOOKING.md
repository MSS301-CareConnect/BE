# WORK PLAN — [Người B]: Luồng booking end-to-end (xử lý chính), catalog & tìm kiếm

> Tách từ `docs/WORK_PLAN.md` (2026-10-21; phân lại B / C cùng ngày: B xử lý chính luồng booking, C nhẹ hơn). File này chứa **tổng quan tính năng theo role**, **toàn bộ task chi tiết** của [Người B], các contract liên quan và lịch sprint riêng.
> Phần dùng chung — nguyên tắc chia việc, quy ước ErrorCode / token SERVICE / tiền tệ / thời gian (§1–§3), toàn bộ hợp đồng (§7), lộ trình chung (§8), rủi ro (§9) — nằm ở `docs/WORK_PLAN.md`.
> **Số mục giữ nguyên như bản gốc**, nên mọi tham chiếu `§x`, `C2.3`… trỏ đúng sang `docs/WORK_PLAN.md`. Khảo sát hiện trạng: `docs/GAP_ANALYSIS.md`.

## Tổng quan: [Người B] làm gì

**Vai trò trong nhóm:** **người xử lý chính luồng booking**. Một booking đi từ lúc khách tìm trung tâm / caregiver, báo giá, đặt lịch, trung tâm duyệt và phân công, caregiver nhận ca, bắt đầu và kết thúc ca, cho đến khi booking hoàn tất hoặc bị hủy — toàn bộ vòng đời này do [Người B] làm. B cũng sở hữu dữ liệu đầu vào của booking: danh mục dịch vụ / giá (Catalog), kỹ năng và lịch rảnh caregiver (Provider). Ngoài ra B làm token service (Identity) và Gateway, vì cả nhóm phụ thuộc vào hai phần này.

| Role | Tính năng [Người B] làm cho role này | Task |
|---|---|---|
| **CUSTOMER** | Xem danh sách trung tâm đang hoạt động (lọc khu vực, dịch vụ, giá); xem dịch vụ + bảng giá của trung tâm; tìm caregiver làm được **tất cả** dịch vụ đã chọn, lọc giới tính / kinh nghiệm / đánh giá, loại người trùng lịch hoặc đang nghỉ; xem hồ sơ caregiver công khai; xem chi phí tạm tính trước khi đặt; tạo booking (theo giờ / ngày / ca, lặp hằng ngày / theo thứ); hủy cả booking hoặc từng buổi, xem trước tiền hoàn; lịch sử booking (lọc, phân trang) và lịch sử thay đổi | SRCH-01, SRCH-02, CAT-02, BK-01, BK-02, BK-03, BK-05, BK-06 |
| **CENTER_MANAGER** | Chọn dịch vụ từ danh mục chuẩn và cấu hình giá giờ / ngày / ca, bật tắt dịch vụ; gán dịch vụ (kỹ năng) cho caregiver; quản lý ca làm việc, duyệt đăng ký ca, ngày nghỉ của caregiver; duyệt / từ chối booking, phân công và đổi caregiver (cả booking hoặc từng buổi), hủy trong trường hợp đặc biệt (hoàn 100%); xác nhận / bác bỏ báo cáo "khách vắng mặt"; buộc kết thúc ca caregiver quên đóng | CAT-01, CAT-02, PRV-01, PRV-02, PRV-03, BK-02, BK-04, BK-05, OPS-04, OPS-06 |
| **CAREGIVER** | Xem lịch được giao và chi tiết ca (người bệnh, địa chỉ, dịch vụ, ghi chú đặc biệt); xác nhận nhận ca; bắt đầu ca (check-in, tự sinh checklist) và kết thúc ca (check-out); báo khách vắng mặt; đăng ký ca làm việc; khai báo ngày nghỉ | OPS-01, OPS-02, OPS-04, OPS-06, PRV-02, PRV-03 |
| **ADMIN** | Quản lý danh mục dịch vụ chuẩn (thêm, sửa, vô hiệu hóa an toàn); xem toàn bộ booking | CAT-01, CAT-02, BK-06 |
| **Hệ thống / service khác** | Token service (client-credentials) cho mọi service; Gateway (route Payment, IPN public, CORS, SSE); job hết hạn booking và job đánh dấu buổi MISSED (tự hoàn tiền); hoàn tất booking khi mọi buổi kết thúc và tạo hóa đơn phần còn lại; hủy hệ thống khi trung tâm bị khóa; chống trùng lịch tầng DB; tài liệu chung + e2e | IAM-01, GW-01, OPS-04, OPS-06, BK-04, BK-07, BK-08, BK-09, INF-01 |

**Service đụng tới:** BookingService (chủ luồng booking), CareCatalogService, ProviderService (kỹ năng, lịch rảnh, ca làm việc, tìm kiếm, trang trung tâm công khai), IdentityService (chỉ token service), ApiGateway.

---

## 6. Task chi tiết

### 6.1 [Người B] — Luồng booking end-to-end (xử lý chính), catalog & tìm kiếm (104 SP, 23 task)

| Mã | Tên | Ưu tiên | SP | Sprint |
|---|---|---|---|---|
| IAM-01 | Token service (client-credentials, role SERVICE) | P0 | 5 | S1 |
| GW-01 | Gateway: route Payment, IPN public, CORS, SSE | P0 | 3 | S1 |
| BK-07 | Sửa phạm vi occurrence + `caregiver-access` (SEC-03, SEC-09) | P0 | 3 | S1 |
| BK-08 | Tách `BookingOccurrenceController`, chuẩn hóa layer | P1 | 2 | S1 |
| CAT-01 | Sửa lỗi Catalog + quote xác định | P0 | 3 | S1 |
| CAT-02 | Duyệt danh mục / dịch vụ trung tâm cho khách, vô hiệu hóa an toàn | P1 | 3 | S2 |
| PRV-01 | Kỹ năng caregiver (`caregiver_services`) | P0 | 5 | S2 |
| BK-01 | Giá từng buổi + API báo giá tạm tính + `payment-context` | P0 | 5 | S2 |
| OPS-01 | Caregiver xác nhận nhận ca + chi tiết ca | P1 | 3 | S2 |
| OPS-02 | Bắt đầu ca (check-in), sinh checklist | P0 | 8 | S3 |
| OPS-04 | Kết thúc ca, hoàn tất booking (FINALIZE), tạo hóa đơn REMAINING | P0 | 5 | S3 |
| PRV-03 | Sửa lỗi ca làm việc (capacity, múi giờ, đăng ký lại) | P1 | 5 | S3 |
| PRV-02 | Lịch rảnh + ngày nghỉ caregiver, API kiểm tra lịch | P0 | 8 | S4 |
| BK-02 | Kiểm tra khi tạo / duyệt booking: lịch rảnh, kỹ năng, nợ, giới hạn PENDING | P0 | 5 | S4 |
| OPS-06 | Job MISSED + báo khách vắng mặt (CUSTOMER_NO_SHOW) | P0 | 5 | S4 |
| SRCH-01 | Tìm caregiver (giao tập dịch vụ, lọc, sắp xếp, lịch trống) + chi tiết công khai | P0 | 8 | S5 |
| BK-03 | Khách hủy booking / từng buổi + preview-cancel | P0 | 8 | S5 |
| SRCH-02 | Danh sách trung tâm ACTIVE cho khách (lọc khu vực, dịch vụ, giá) | P1 | 3 | S5 |
| BK-04 | Trung tâm từ chối / hủy, hủy hệ thống (khóa trung tâm) + hoàn tiền | P0 | 5 | S6 |
| BK-05 | Audit đổi caregiver + lịch sử trạng thái booking | P1 | 3 | S6 |
| BK-06 | Lịch sử booking: lọc, sắp xếp, phân trang | P1 | 3 | S6 |
| BK-09 | Chống trùng lịch tầng DB + scheduler nhiều instance | P2 | 3 | S6 |
| INF-01 | Tài liệu chung, e2e, Docker cập nhật theo kiến trúc mới | P1 | 3 | S6 |

---

#### IAM-01 — Token service (client-credentials, role SERVICE)
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S1 · **Phụ thuộc:** — (**BLOCKER** cho PAY-05/06/08/10/11, BK-03/04/07, CR-01, MED-01, NTF-01)

- **Role:** public (xác thực bằng client secret).
- **API:** `POST /api/v1/auth/service-token` — `@SecurityRequirements({})`, permit ở `SecurityConfig` và Gateway, miễn CSRF (không cookie).
  - Request `ServiceTokenRequest`: `clientId` (`@NotBlank(message="CLIENT_ID_REQUIRED")`), `clientSecret` (`@NotBlank(message="CLIENT_SECRET_REQUIRED")`).
  - Response `ServiceTokenResponse`: `accessToken`, `tokenType="Bearer"`, `expiresInSeconds`.
- **Quy tắc nghiệp vụ:**
  1. Bảng `service_clients` (seed bằng env `IDENTITY_SERVICE_CLIENTS`, dạng `booking:<bcrypt>,payment:<bcrypt>,...`; seed idempotent, **không** hardcode secret).
  2. So khớp BCrypt. Sai `clientId` hay sai secret đều trả cùng `INVALID_CLIENT_CREDENTIALS`. Rate limit dùng chung `RedisRateLimiter` (5 lần / 15 phút theo `clientId`).
  3. Phát access JWT RS256:
     - `sub` = `service_clients.client_uuid`, `roles=["SERVICE"]`, `permissions=[]`, `client_id`;
     - `aud = [IDENTITY_JWT_AUDIENCE, "careconnect-api"]`, `token_type=access`;
     - TTL `IDENTITY_SERVICE_TOKEN_TTL` (đề xuất 10 phút).

     **Không** phát refresh token.
  4. `IdentityAuthenticationConverter` của Identity: token có `client_id` → không tra bảng `users`; kiểm client còn `active`; authorities = `ROLE_SERVICE`.
  5. Thêm `RoleCode.SERVICE`, hằng `TokenClaims.CLIENT_ID`. **Không** seed `SERVICE` vào bảng `roles` cho user. `POST /users` cấm tạo user với role này (`INVALID_ROLE`).
  6. Mỗi service nghiệp vụ (Booking, Payment, CareRecipient, Provider, Media): thêm `client/ServiceTokenProvider` (cache, làm mới khi còn < 60s, thread-safe) và `client/ServiceTokenInterceptor`, chỉ gắn vào các Feign client cần quyền service. Thêm env `<SERVICE>_SERVICE_CLIENT_ID`, `<SERVICE>_SERVICE_CLIENT_SECRET`. [Người B] làm mẫu ở Booking; người sở hữu từng service chép theo mẫu khi task của họ cần.
- **DB (Identity):** `service_clients`:
  - `client_uuid` uuid PK, `client_id` varchar(50) UNIQUE, `secret_hash` varchar NN;
  - `active` bool NN mặc định true, `description`;
  - `created_at`, `updated_at`.
- **Lỗi:** `INVALID_CLIENT_CREDENTIALS (1114, 401)`, `CLIENT_ID_REQUIRED (1115, 400)`, `CLIENT_SECRET_REQUIRED (1116, 400)`, `SERVICE_CLIENT_DISABLED (1117, 401)`, `TOO_MANY_ATTEMPTS` (mã hiện có).
- **AC:**
  - Given client `booking` hợp lệ, When xin token, Then nhận JWT `roles=["SERVICE"]` và gọi được endpoint `hasRole('SERVICE')` ở service khác.
  - Given sai secret, Then 401.
  - Given token SERVICE gọi `/users/me`, Then 403 / 404 (không phải user).
  - Given token user thường gọi endpoint SERVICE, Then 403.

#### GW-01 — Gateway: route Payment, IPN public, CORS, SSE
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 3 · **Sprint:** S1 · **Phụ thuộc:** PAY-01 (URL)

- **API (Gateway):**
  - route `/api/v1/payments/**`, `/payment-policies/**`, `/refunds/**`, `/refund-quotes/**`, `/remaining-invoices/**`, `/settlements/**`, `/revenue/**` → PaymentService (`GATEWAY_PAYMENT_URL`);
  - route `/api/v1/notifications/**`, `/care-sessions/**`, `/care-reports/**`, `/feedbacks/**`, `/complaints/**`, `/statistics/**` → BookingService;
  - Swagger `/api-docs/payment`.
- **Quy tắc nghiệp vụ:**
  1. Permit (không cần JWT) **chỉ**: `GET /api/v1/payments/vnpay/ipn`, `GET /api/v1/payments/vnpay/return`, `POST /api/v1/auth/service-token`, cùng các path public Identity hiện có, `GET /api/v1/care-centers/public/**` (SRCH-02).
  2. CORS: thêm `Idempotency-Key`, `X-Refresh-Token`, `Last-Event-ID` vào allowed headers (`GW/security/SecurityConfig.java:87-101`).
  3. SSE `GET /api/v1/notifications/stream`: tắt read timeout cho route này (hoặc timeout = 30 phút), không buffer response.
  4. Không cho client gửi header `X-Service-*` (xóa như `X-User-*`).
  5. Test `ApiGatewayTests`: route mới, IPN không cần token, preflight có `Idempotency-Key`.
- **Lỗi:** dùng mã gateway hiện có (9000–9003).
- **AC:**
  - Given preflight `OPTIONS /api/v1/bookings` với header `Idempotency-Key`, Then 200 và header được cho phép.
  - Given `GET /api/v1/payments/vnpay/ipn` không token, Then tới PaymentService.
  - Given `GET /api/v1/payments` không token, Then 401.

#### BK-07 — Sửa phạm vi occurrence + `caregiver-access` (SEC-03, SEC-09)
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 3 · **Sprint:** S1 · **Phụ thuộc:** IAM-01

- **Role:** CAREGIVER (buổi được giao), CUSTOMER (buổi của booking mình), CENTER_MANAGER (buổi của trung tâm), ADMIN, SERVICE.
- **API:**
  - `GET /api/v1/booking-occurrences/{id}`: controller chỉ `return bookingOccurrenceService.get(actorId, roles, id)`. Bỏ so chuỗi `ROLE_CAREGIVER` trong controller (rule #22); service chọn scope theo role. Không có role phù hợp → 404.
  - `GET /api/v1/bookings/caregiver-access`: `hasRole('SERVICE')` (CareRecipient gọi bằng token SERVICE).
- **Quy tắc nghiệp vụ:**
  1. Truy vấn theo scope: `caregiver_user_id = sub`, `booking.customer_id = sub`, `booking.center_id = claim`. ADMIN không giới hạn.
  2. `recipientSnapshot` trong response:
     - chỉ trả cho caregiver khi booking ∈ {CONFIRMED, IN_PROGRESS} **và** buổi chưa kết thúc quá 24h (`BK/enums/BookingStatus.java` `CAREGIVER_RECIPIENT_ACCESS`);
     - booking CANCELLED / COMPLETED → caregiver thấy buổi nhưng **không có snapshot** (SEC-09).
  3. `existsCaregiverAssignment` (`BK/repository/BookingRepository.java:55-69`): đổi sang kiểm **theo buổi** (`booking_occurrences.caregiver_user_id`) với buổi ∈ {SCHEDULED, ACKNOWLEDGED, IN_PROGRESS} hoặc COMPLETED trong 24h (D5).
  4. Sửa test `interServiceEndpointsRequireAuthentication`: customer gọi `caregiver-access` → 403; customer đọc buổi của người khác → 404.
- **Lỗi:** `OCCURRENCE_NOT_FOUND (5217, 404)`.
- **AC:**
  - Given khách K2, When `GET /booking-occurrences/{buổi của K1}`, Then 404.
  - Given caregiver đã bị đổi khỏi mọi buổi, When CareRecipient kiểm access, Then `allowed=false`.
  - Given booking CANCELLED, Then caregiver nhận response không có `recipientSnapshot`.

#### BK-08 — Tách `BookingOccurrenceController`, chuẩn hóa layer
**Người:** [Người B] · **Ưu tiên:** P1 · **SP:** 2 · **Sprint:** S1 · **Phụ thuộc:** BK-07

- **Nội dung:**
  1. `BookingController` → `@RequestMapping("/api/v1/bookings")`. Tạo `BookingOccurrenceController` (`/api/v1/booking-occurrences`). **Path giữ nguyên.**
  2. `BookingOccurrenceService` (interface) + `BookingOccurrenceServiceImpl` (`BSI:118` đang inject class cụ thể).
  3. `BookingSpecifications`: chuyển ra `repository/spec/` hoặc `service/Impl/` (repository chỉ chứa interface).
  4. Bỏ `@Operation`, import thừa (`BSI:6,32,42`), sắp method controller GET → POST → PUT → PATCH → DELETE.
  5. Cập nhật `BookingService/AGENTS.md`, `README.md` theo path thật.
- **AC:** Given toàn bộ test Booking hiện có, When chạy, Then xanh, không đổi path / response.

#### CAT-01 — Sửa lỗi Catalog + quote xác định
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 3 · **Sprint:** S1 · **Phụ thuộc:** —

- **Role:** CENTER_MANAGER (ghi), SERVICE / authenticated (quote).
- **API:** giữ path hiện có (`CC/controller/*`); quote `POST /api/v1/center-services/quote`.
- **Quy tắc nghiệp vụ:**
  1. `PricePlanMapper`: `ignore` cho `isActive` ở create/update. Service gán `isActive = request.isActive != null ? request.isActive : true` (create); khi update thì giữ giá trị cũ nếu null.
  2. Tạo / sửa offering: item danh mục phải `ACTIVE` (`CenterOfferingServiceImpl.java:116-119`) → `SERVICE_NOT_ACTIVE (3105)`.
  3. Quote **bắt buộc** `pricePlanId` cho mỗi item (D12) → bỏ nhánh `activePlans.getFirst()` (`CatalogQuoteServiceImpl.java:74-78`). Plan phải active, thuộc offering, offering ACTIVE, item ACTIVE.
  4. Kiểm `durationMinutes`: HOUR thì bắt buộc 60 (đơn giá theo giờ). DAY / SHIFT thì là thời lượng chuẩn của đơn vị.
  5. `GlobalExceptionHandler`: map vi phạm `uk_center_services_center_care` → `CENTER_SERVICE_ALREADY_EXISTS (3205)`.
  6. Theo quy ước: controller không dựng `APIResponse` (`CenterOfferingController.java:67`); quote dùng mapper thay builder; bỏ `@Operation`; xóa `copy_base.py`, cập nhật / xóa `note.md`; thêm `CareCatalogService/AGENTS.md`; xóa cấu hình Feign Identity không dùng.
- **DB:** `price_plans` CHECK `pricing_unit <> 'HOUR' OR duration_minutes = 60` (change script; backfill plan HOUR khác 60 → đánh `is_active=false` và log danh sách).
- **Lỗi:** `PRICE_PLAN_ID_REQUIRED (3305, 400)`, `PRICE_PLAN_NOT_ACTIVE (3306, 409)`, `INVALID_DURATION (3303, 400)`, `SERVICE_NOT_ACTIVE (3105, 409)`.
- **AC:**
  - Given tạo plan không gửi `isActive`, Then 200 và `isActive=true`.
  - Given item INACTIVE, When tạo offering, Then 409.
  - Given quote thiếu `pricePlanId`, Then 400.
  - Given gọi quote 10 lần, Then cùng kết quả.

#### CAT-02 — Duyệt danh mục / dịch vụ trung tâm cho khách, vô hiệu hóa an toàn
**Người:** [Người B] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S2 · **Phụ thuộc:** CAT-01

- **Role:** mọi user đã đăng nhập (đọc); ADMIN (danh mục); CENTER_MANAGER (dịch vụ trung tâm).
- **API:**
  - `GET /api/v1/catalog-items?status=&category=&keyword=&page=&size=`: CUSTOMER / CAREGIVER chỉ thấy ACTIVE; ADMIN / MANAGER thấy tất cả.
  - `GET /api/v1/center-services/center/{centerId}?status=&page=&size=`: khách chỉ thấy offering ACTIVE có item ACTIVE và ít nhất 1 plan active, kèm `pricePlans[]` active (`id`, `planName`, `pricingUnit`, `durationMinutes`, `basePrice`).
  - `PATCH /api/v1/catalog-items/{id}/status`: ADMIN, `{status}`.
  - `PATCH /api/v1/center-services/{id}/status`: MANAGER, `{status}`.
- **Quy tắc nghiệp vụ:**
  1. Vô hiệu item danh mục: không đổi offering trong DB, nhưng mọi truy vấn cho khách và quote đều loại ra (lọc khi đọc). **Booking đã tạo không bị ảnh hưởng** vì giá đã snapshot trong `booking_details`.
  2. `DELETE /catalog-items/{id}` và `DELETE /center-services/{id}`: chỉ cho xóa mềm khi không còn offering / plan đang active. Ngược lại 409 → hướng dẫn dùng PATCH status.
  3. Xóa offering → xóa mềm các plan con trong cùng transaction.
  4. Phân trang `size ≤ 100`, `INVALID_PAGE`.
- **Lỗi:** `CARE_SERVICE_IN_USE (3110, 409)`, `CENTER_SERVICE_IN_USE (3206, 409)`, `INVALID_PAGE (3003, 400)`.
- **AC:**
  - Given item INACTIVE, When customer liệt kê dịch vụ trung tâm, Then không thấy offering dùng item đó.
  - Given offering có plan active, When DELETE item, Then 409.
  - Given CUSTOMER, When PATCH status, Then 403.

#### PRV-01 — Kỹ năng caregiver (`caregiver_services`)
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S2 · **Phụ thuộc:** CAT-02 (đọc offering), IAM-01

- **Role:** CENTER_MANAGER (trung tâm mình), ADMIN (đọc); CAREGIVER (đọc của mình); mọi user (đọc trong chi tiết công khai).
- **API:**
  - `GET /api/v1/caregivers/{caregiverId}/services` → `[{centerServiceId, careServiceId, serviceName, assignedAt}]`.
  - `PUT /api/v1/caregivers/{caregiverId}/services` — MANAGER. Request `{centerServiceIds: [uuid] (@NotNull, ≤ 50)}` (thay toàn bộ).
  - `GET /api/v1/caregivers/{id}` và catalog: thêm field `careServiceIds` (id dịch vụ **chuẩn**) và `centerServiceIds`.
- **Quy tắc nghiệp vụ:**
  1. Manager chỉ sửa caregiver thuộc trung tâm mình (`ProviderAuthorization.requireCaregiverSelfOrCenterManagement`).
  2. Provider gọi Catalog `GET /api/v1/center-services/{id}` (token SERVICE) để kiểm từng offering thuộc **cùng** `center_id` với caregiver và ACTIVE; lưu kèm `care_service_id` để Booking so khớp. Gọi mạng **trước** transaction.
  3. Thay toàn bộ: xóa dòng không còn trong danh sách, thêm dòng mới, trong một transaction. Ghi `log.info`.
  4. Booking `CaregiverPortImpl` (`BK/service/Impl/CaregiverPortImpl.java:53-58`) nhận `careServiceIds` khác null → kiểm `containsAll` (code đã có). Bỏ nhánh "null → bỏ qua" (D-phụ thuộc): caregiver chưa gán kỹ năng nào thì `[]` → `CAREGIVER_SERVICE_NOT_SUPPORTED`.
- **DB (Provider):** `caregiver_services`:
  - `caregiver_id` FK, `center_service_id` uuid, `care_service_id` uuid NN;
  - `assigned_by`, `assigned_at`;
  - PK `(caregiver_id, center_service_id)`, index `(care_service_id)`.
- **Lỗi:** `CAREGIVER_SERVICE_NOT_IN_CENTER (4207, 400)`, `CAREGIVER_SERVICE_NOT_ACTIVE (4208, 409)`, `TOO_MANY_CAREGIVER_SERVICES (4209, 400)`, `CATALOG_UNAVAILABLE (4701, 503)`.
- **AC:**
  - Given manager C1 gán offering của C2, Then 400.
  - Given caregiver có {Cho ăn, Vệ sinh}, When khách đặt {Cho ăn, Uống thuốc} với caregiver đó, Then 409 `CAREGIVER_SERVICE_NOT_SUPPORTED (5406)`.
  - Given Catalog tắt, Then 503 và không đổi dữ liệu.

#### BK-01 — Giá từng buổi + API báo giá tạm tính + `payment-context`
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S2 · **Phụ thuộc:** CAT-01, PAY-03 (API chính sách — có thể stub)

- **Role:** CUSTOMER (báo giá), SERVICE (`payment-context` — chi tiết thuộc PAY-08 nhưng dữ liệu giá do BK-01 cung cấp).
- **API:** `POST /api/v1/bookings/quotes` — CUSTOMER.
  - Request `BookingQuoteRequest`: giống `BookingCreateRequest` (`centerId`, `recipientId` tùy chọn, `scheduleType`, `startDate`, `endDate`, `startTime`, `endTime`, `daysOfWeek`, `services[{careServiceId, centerServiceId, pricePlanId}]`). Validation dùng lại message key hiện có (`SERVICES_REQUIRED`, `START_DATE_REQUIRED`…) và thêm `pricePlanId` `@NotNull(message="PRICE_PLAN_REQUIRED")`.
  - Response `BookingQuoteResponse`:
    - `occurrenceCount`;
    - `lines[]` (`{careServiceId, serviceName, pricingUnit, unitPrice, unitsPerOccurrence, occurrenceCount, lineTotal}`);
    - `occurrences[]` (`{occurrenceNo, startAt, endAt, priceAmount}`, tối đa 60);
    - `totalAmount`, `depositPercent`, `estimatedDepositAmount`, `policyId`.
- **Quy tắc nghiệp vụ:**
  1. Sinh buổi bằng `OccurrenceGenerator` hiện có (cùng kiểm biên 60 buổi / 90 ngày / báo trước 12h). Không ghi DB.
  2. Gọi Catalog quote (CAT-01) ngoài transaction.
  3. Số đơn vị **mỗi buổi** (D12):
     - HOUR: `units = ceil(duration_minutes_buổi / 60)`;
     - DAY: 1;
     - SHIFT: 1, nhưng buổi phải ≤ `durationMinutes` của plan, ngược lại `PRICE_PLAN_UNIT_MISMATCH`.
  4. `priceAmount_i = Σ_dịch vụ (unitPrice × units_i)`, `totalAmount = Σ priceAmount_i`. Làm tròn đồng.
  5. `estimatedDepositAmount = round(depositPercent% × total)` theo chính sách **hiệu lực hiện tại** (PAY-03), chỉ để hiển thị.
  6. Khi tạo booking thật (`POST /bookings`):
     - lưu `booking_occurrences.price_amount`;
     - `booking_details` thêm `units_per_occurrence`;
     - `bookings.subtotal / total_amount = Σ priceAmount`;
     - **bỏ** `deposit_amount` tính trong Booking: cột giữ lại, giá trị NULL tới khi PAY-08 ghi.
  7. Bỏ cờ `BOOKING_CATALOG_INTEGRATION_ENABLED`: luôn gọi Catalog. Catalog lỗi → 503 `CARE_CATALOG_UNAVAILABLE`, không tạo booking không giá.
- **DB (Booking):**
  - `booking_occurrences` + `price_amount numeric(14,2)`;
  - `booking_details` + `units_per_occurrence int`;
  - backfill dữ liệu dev: NULL được phép cho booking cũ; CHECK mới chỉ áp cho booking tạo sau (ràng buộc ở service).
- **Lỗi:** `PRICE_PLAN_REQUIRED (5224, 400)`, `PRICE_PLAN_UNIT_MISMATCH (5225, 400)`, `CARE_CATALOG_UNAVAILABLE (5300, 503)`, cùng các mã lịch 52xx hiện có.
- **AC:**
  - Given 2 dịch vụ HOUR 100.000 và DAY 300.000, lịch 3 buổi × 150 phút, Then `units` HOUR = 3, `price_i = 300.000 + 300.000 = 600.000`, `total = 1.800.000`, cọc dự kiến 540.000 (30%).
  - Given gọi quote, Then không tạo dòng nào trong DB.
  - Given SHIFT plan 240 phút và buổi 300 phút, Then 400.

#### OPS-01 — Caregiver xác nhận nhận ca + chi tiết ca
**Người:** [Người B] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S2 · **Phụ thuộc:** BK-07

- **Role:** CAREGIVER.
- **API:**
  - `POST /api/v1/booking-occurrences/{id}/acknowledge` → `CaregiverOccurrenceResponse`.
  - `GET /api/v1/booking-occurrences/{id}`: bổ sung `services[]` (tên, đơn vị), `specialNotes` (từ snapshot: dị ứng, bệnh nền, ăn uống, hành vi, yêu cầu chăm sóc, thuốc), `serviceAddress`, `customerPhone` (masked trừ khi buổi trong 24h tới), `sessionId`, `reportStatus`.
- **Quy tắc nghiệp vụ:**
  1. Chỉ caregiver được giao (`caregiver_user_id = sub`); booking ∈ {CONFIRMED, IN_PROGRESS}; buổi `SCHEDULED`; `start_at > now`.
  2. `SCHEDULED → ACKNOWLEDGED`, ghi `acknowledged_at`, `booking_events`. Thông báo manager (tùy chọn, P2).
  3. Khi buổi đổi caregiver (BK-05), trạng thái quay về `SCHEDULED`, `acknowledged_at = null`. [Người B] gọi hàm `resetAcknowledgement` do OPS-01 cung cấp.
  4. Mọi tập trạng thái "buổi đang hoạt động" (`OccurrenceStatus.ACTIVE`, kiểm trùng lịch, `existsCaregiverAssignment`) bổ sung `ACKNOWLEDGED`.
- **DB (Booking):**
  - `booking_occurrences.status` CHECK thêm `ACKNOWLEDGED`, `CUSTOMER_NO_SHOW`;
  - + `acknowledged_at timestamptz`.
- **Lỗi:** `INVALID_OCCURRENCE_TRANSITION (5221, 409)`, `BOOKING_NOT_CONFIRMED (5223, 409)`, `OCCURRENCE_NOT_FOUND (5217, 404)`.
- **AC:**
  - Given buổi SCHEDULED của tôi, When acknowledge, Then ACKNOWLEDGED.
  - Given buổi của caregiver khác, Then 404.
  - Given booking AWAITING_DEPOSIT, Then 409.
  - Given buổi ACKNOWLEDGED bị đổi người, Then về SCHEDULED.

#### OPS-02 — Bắt đầu ca (check-in), sinh checklist
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 8 · **Sprint:** S3 · **Phụ thuộc:** OPS-01, PAY-08 (booking CONFIRMED thật), NTF-01

- **Role:** CAREGIVER (được giao).
- **API** (`CareSessionController`, `/api/v1/care-sessions`):
  - `POST /api/v1/care-sessions` — request `{occurrenceId (@NotNull), latitude?, longitude?}` → `CareSessionResponse` (`sessionId`, `occurrenceId`, `status`, `checkInAt`, `tasks[]`).
  - `GET /api/v1/care-sessions/{id}` (caregiver của session, khách chủ booking, manager trung tâm, admin).
- **Quy tắc nghiệp vụ:**
  1. Khóa dòng booking rồi occurrence (`findForUpdate`). Kiểm:
     - người gọi là caregiver được gán **cho buổi** (`caregiver_user_id = sub`);
     - booking ∈ {CONFIRMED, IN_PROGRESS};
     - buổi ∈ {SCHEDULED, ACKNOWLEDGED};
     - chưa có session (UNIQUE `occurrence_id` chặn race);
     - `start_at − BOOKING_SESSION_START_WINDOW_MINUTES (30) ≤ now < end_at`.
  2. Tạo `care_sessions` `IN_PROGRESS` (`check_in_at = now`, tọa độ nếu có — chỉ lưu, không kiểm khoảng cách).
  3. Sinh `care_tasks`:
     - (a) mỗi dịch vụ trong `booking_details` → 1 task `SERVICE` (tiêu đề = `service_name`);
     - (b) mỗi thuốc trong `recipient_snapshot.medications` có `timeOfDay` rơi vào khung buổi → 1 task `MEDICATION` ("Cho uống <tên> <liều> lúc <giờ>").

     Thứ tự theo giờ / thứ tự dịch vụ.
  4. Buổi → `IN_PROGRESS`, `actual_start_at = now`. Nếu booking đang `CONFIRMED` → `BookingAction.START` → `IN_PROGRESS`.
  5. Sau commit: thông báo khách `SESSION_STARTED` + SSE `session-update`.
- **DB (Booking):**
  - `care_sessions`:
    - `session_id` PK, `occurrence_id` UNIQUE FK, `booking_id` FK, `caregiver_id`, `caregiver_user_id`;
    - `status` CHECK (`IN_PROGRESS`, `ENDED`);
    - `check_in_at` NN, `check_out_at`, `check_in_lat/lng numeric(9,6)`, `check_out_lat/lng`;
    - `ended_by` uuid, `end_reason`;
    - `created_at`, `updated_at`, `version`.
  - `care_tasks`:
    - `task_id` PK, `session_id` FK, `booking_detail_id` null, `care_service_id` null;
    - `task_type` CHECK (`SERVICE`, `MEDICATION`, `CUSTOM`), `title` NN, `scheduled_time` time null;
    - `status` CHECK (`PENDING`, `DONE`, `SKIPPED`), `skip_reason`, `note`, `completed_at`;
    - `sort_order`, `updated_by`, `updated_at`.
- **Lỗi:** `SESSION_ALREADY_EXISTS (5801, 409)`, `SESSION_WINDOW_NOT_OPEN (5222, 409)`, `BOOKING_NOT_CONFIRMED (5223, 409)`, `INVALID_OCCURRENCE_TRANSITION (5221, 409)`, `OCCURRENCE_NOT_FOUND (5217, 404)`.
- **AC:**
  - Given buổi 08:00 và now 07:35, When check-in, Then 200, có task cho mỗi dịch vụ và thuốc 08:30; buổi IN_PROGRESS, booking IN_PROGRESS.
  - Given now 07:00, Then 409 `SESSION_WINDOW_NOT_OPEN`.
  - Given 2 request đồng thời, Then 1 thành công, 1 nhận 409.
  - Given caregiver khác, Then 404.

#### OPS-04 — Kết thúc ca, hoàn tất booking (FINALIZE), tạo hóa đơn REMAINING
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S3 · **Phụ thuộc:** OPS-03, PAY-11 (API — stub được)

- **Role:** CAREGIVER (kết thúc); CENTER_MANAGER (buộc kết thúc).
- **API:**
  - `POST /api/v1/care-sessions/{id}/end` — request `{latitude?, longitude?}`.
  - `POST /api/v1/care-sessions/{id}/force-end` — MANAGER, request `{reason (@NotBlank)}`.
- **Quy tắc nghiệp vụ:**
  1. **End:** mọi task ≠ PENDING (không thì 409 `TASKS_PENDING`). Session → `ENDED`, `check_out_at = now`. Buổi → `COMPLETED`, `actual_end_at = now`.
  2. **Force-end:** cho caregiver quên kết thúc. Task PENDING → SKIPPED với lý do "Buộc kết thúc bởi trung tâm".
  3. **`BookingLifecycle.finalizeIfDone(booking)`** — dùng chung với BK-03, BK-04, OPS-06:
     - còn buổi ∈ {SCHEDULED, ACKNOWLEDGED, IN_PROGRESS} → không đổi;
     - có ít nhất một buổi ∈ {COMPLETED, CUSTOMER_NO_SHOW} → action `FINALIZE` → `COMPLETED` (`completed_at`);
     - ngược lại → `CANCELLED` (`cancelled_by_party = SYSTEM`).

     Thêm action `FINALIZE` vào `BookingAction`: nguồn CONFIRMED / IN_PROGRESS, đích COMPLETED / CANCELLED (action có 2 đích, quyết định trong lifecycle). Xóa action `COMPLETE` không dùng.
  4. Booking COMPLETED → sau commit:
     - gọi Payment `POST /remaining-invoices` (SERVICE) với danh sách buổi + `finalStatus`;
     - lỗi → ghi `booking_outbox` (OPS-04 **tạo** bảng `booking_outbox` + dispatcher dùng chung cho refund, hóa đơn REMAINING, rating; BK-03, BK-04, OPS-06, FB-01, FB-02 dùng lại) và retry;
     - thông báo khách `BOOKING_COMPLETED` (mời đánh giá + hóa đơn).
  5. Job nhắc: session `IN_PROGRESS` quá `end_at + 60 phút` → thông báo caregiver + manager (một lần).
- **DB (Booking):**
  - `bookings` + `started_at`, `completed_at`;
  - `booking_outbox`: `id` PK, `event_type`, `aggregate_id`, `payload` jsonb, `status` (`PENDING` / `SENT` / `FAILED`), `attempts`, `next_attempt_at`, `last_error`, `created_at`; index `(status, next_attempt_at)`.
- **Lỗi:** `TASKS_PENDING (5805, 409)`, `SESSION_NOT_IN_PROGRESS (5802, 409)`, `REASON_REQUIRED (5106, 400)`.
- **AC:**
  - Given 2 buổi, buổi 1 COMPLETED, When buổi 2 end, Then booking COMPLETED và Payment nhận đúng 1 request remaining (kể cả gọi end lặp do retry mạng).
  - Given còn task PENDING, Then 409.
  - Given booking có 1 buổi COMPLETED và buổi còn lại bị hủy, Then booking COMPLETED.

#### PRV-03 — Sửa lỗi ca làm việc (capacity, múi giờ, đăng ký lại)
**Người:** [Người B] · **Ưu tiên:** P1 · **SP:** 5 · **Sprint:** S3 · **Phụ thuộc:** —

- **Role:** CENTER_MANAGER, CAREGIVER.
- **API:** giữ `PV/controller/WorkShiftController.java`.
- **Quy tắc nghiệp vụ:**
  1. **Capacity:** đếm số đăng ký APPROVED **trước** khi set APPROVED (hoặc bỏ `+1`). Đóng ca khi `approved == capacity` (`ShiftRegistrationServiceImpl.java:183-194`). Test với repository thật (không mock count).
  2. **Múi giờ:** thay `toInstant(ZoneOffset.UTC)` bằng `atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant()` (`WorkShiftServiceImpl.java:160-166,188`, `CaregiverScheduleServiceImpl.java:104-105`). Inject `Clock`.
  3. **Đăng ký lại:** đổi `uk_shift_registrations_shift_caregiver` thành unique một phần `WHERE registration_status IN ('PENDING','APPROVED')`. `existsBy…` lọc theo status.
  4. Chặn đăng ký ca đã bắt đầu dù không có deadline; chặn đăng ký / duyệt khi trung tâm không ACTIVE.
  5. `GET /work-shifts`:
     - CUSTOMER không được gọi;
     - CAREGIVER chỉ trung tâm mình và không thấy DRAFT;
     - MANAGER chỉ trung tâm mình;
     - ADMIN tất cả (một phần SEC-08).
  6. Bỏ N+1 count (`WorkShiftServiceImpl.java:87,152-154`) bằng một query gom nhóm.
- **DB:** change script đổi unique constraint.
- **Lỗi:** `WORK_SHIFT_ALREADY_STARTED (4308, 409)`, `CENTER_NOT_ACTIVE (4116, 409)`.
- **AC:**
  - Given ca capacity 3, When duyệt người thứ 2, Then ca vẫn OPEN; duyệt người thứ 3 thì CLOSED.
  - Given ca 08:00 giờ VN hôm nay và now = 07:30 giờ VN, When tạo ca, Then được (không lệch 7h).
  - Given đăng ký đã CANCELLED, When đăng ký lại, Then 200.

#### PRV-02 — Lịch rảnh + ngày nghỉ caregiver, API kiểm tra lịch
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 8 · **Sprint:** S4 · **Phụ thuộc:** PRV-03 (múi giờ), IAM-01

- **Role:**
  - CAREGIVER: tạo / hủy ngày nghỉ của mình;
  - CENTER_MANAGER: xem / duyệt / tạo ngày nghỉ cho caregiver trung tâm;
  - CUSTOMER: xem lịch trống tổng hợp;
  - SERVICE: kiểm tra.
- **API:**
  - `GET /api/v1/caregivers/{caregiverId}/time-offs?from=&to=` (CAREGIVER chính mình, MANAGER, ADMIN).
  - `POST /api/v1/caregivers/{caregiverId}/time-offs` — CAREGIVER (chính mình) / MANAGER. Request `TimeOffRequest`: `startAt`, `endAt` (`@NotNull`), `reason` (≤ 500).
  - `DELETE /api/v1/caregivers/{caregiverId}/time-offs/{timeOffId}`.
  - `GET /api/v1/caregivers/{caregiverId}/availability?from=&to=` (authenticated, `to − from ≤ 31 ngày`) → `[{startAt, endAt}]` khung rảnh đã trừ ngày nghỉ. Khung đã có booking do Booking trừ ở SRCH-01.
  - `POST /api/v1/caregivers/availability-checks` — SERVICE. Request `{caregiverId, slots[{startAt, endAt}] (≤ 60)}`. Response `{available: bool, violations[{startAt, endAt, reason: NO_SCHEDULE|TIME_OFF}]}`.
- **Quy tắc nghiệp vụ:**
  1. **Lịch rảnh = `caregiver_schedules` ở trạng thái SCHEDULED** (từ đăng ký ca đã duyệt), quy đổi `work_date + start/end_time` theo `Asia/Ho_Chi_Minh` → Instant.
  2. Một slot hợp lệ khi **nằm trọn** trong hợp các khung rảnh (cho phép ghép hai ca liền kề) và **không giao** ngày nghỉ nào (`[start, end)` nửa mở).
  3. Ngày nghỉ:
     - `endAt > startAt`, tối đa 30 ngày;
     - không chồng ngày nghỉ khác của cùng caregiver;
     - caregiver tạo ngày nghỉ trùng buổi đã được giao → 409 `TIME_OFF_HAS_BOOKINGS` (Provider gọi Booking `POST /booking-occurrences/busy-caregivers` — C5.1). Manager được tạo, nhưng phải đổi người các buổi bị ảnh hưởng trước.
  4. `caregiver_schedules`: bỏ trạng thái IN_PROGRESS / COMPLETED (D11):
     - xóa nhánh chuyển trong `CaregiverScheduleServiceImpl` và endpoint `PATCH /caregivers/schedules/{id}/status` chỉ còn hủy (CANCELLED kèm lý do);
     - bỏ endpoint trùng `PATCH /care-centers/schedules/{id}/status`.
  5. Hủy lịch rảnh mà đã có buổi được giao trong khung đó → 409 (kiểm qua Booking).
- **DB (Provider):**
  - `caregiver_time_offs`: `time_off_id` PK, `caregiver_id` FK, `start_at`, `end_at` timestamptz NN, CHECK `end_at > start_at`, `reason`, `created_by`, BaseEntity, `version`; index `(caregiver_id, start_at)`.
  - Change script sửa CHECK `schedule_status IN ('SCHEDULED','CANCELLED')`; backfill IN_PROGRESS / COMPLETED → SCHEDULED.
- **Lỗi:** `TIME_OFF_NOT_FOUND (4405, 404)`, `TIME_OFF_OVERLAP (4406, 409)`, `TIME_OFF_RANGE_INVALID (4407, 400)`, `TIME_OFF_HAS_BOOKINGS (4408, 409)`, `AVAILABILITY_RANGE_TOO_LONG (4409, 400)`, `BOOKING_SERVICE_UNAVAILABLE (4700, 503)`.
- **AC:**
  - Given caregiver có ca 08:00–12:00 và 12:00–16:00 ngày D, When kiểm slot 10:00–14:00, Then `available=true`.
  - Given nghỉ 13:00–15:00, Then `available=false` với `reason TIME_OFF`.
  - Given caregiver tạo nghỉ trùng buổi đã giao, Then 409.
  - Given CUSTOMER, When `POST availability-checks`, Then 403.

#### BK-02 — Kiểm tra khi tạo / duyệt booking: lịch rảnh, kỹ năng, nợ, giới hạn PENDING
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S4 · **Phụ thuộc:** PRV-01, PRV-02, PAY-11 (`/payments/me/outstanding` — stub trước), CR-01 (CareRecipient INACTIVE)

- **Role:** CUSTOMER (`POST /bookings`), CENTER_MANAGER (accept, đổi caregiver).
- **API** (đều là path hiện có):
  - `POST /api/v1/bookings`;
  - `POST /api/v1/bookings/center/{id}/accept`;
  - `PATCH /api/v1/bookings/center/{id}/caregiver`;
  - `PATCH /api/v1/bookings/center/occurrences/{occurrenceId}/caregiver`.
- **Quy tắc nghiệp vụ** (theo thứ tự, mọi gọi mạng **trước** `TransactionTemplate`):
  1. **Nợ:** gọi Payment `GET /payments/me/outstanding` (token khách chuyển tiếp). `hasDebt` → 409 `CUSTOMER_HAS_OUTSTANDING_DEBT`. Payment lỗi → 503 (fail-closed).
  2. **Giới hạn PENDING** (SEC-12): khách có ≥ `BOOKING_MAX_PENDING_PER_CUSTOMER` (mặc định 3) booking ở PENDING / AWAITING_DEPOSIT → 409 `TOO_MANY_PENDING_BOOKINGS`.
  3. **Người bệnh:** CareRecipient access ≥ EDITOR (hiện có) + snapshot `status == ACTIVE` (hiện có, `RECIPIENT_NOT_ACTIVE 5602`).
  4. **Dịch vụ:** quote Catalog (BK-01).
  5. **Caregiver** (khi có lúc tạo, hoặc khi accept / đổi người):
     - thuộc trung tâm, VERIFIED, ACTIVE, đủ kỹ năng (PRV-01);
     - **lịch rảnh**: `POST /caregivers/availability-checks` với mọi buổi của booking (hoặc buổi bị đổi) → `available=false` → 409 `CAREGIVER_NOT_AVAILABLE` (kèm danh sách buổi vi phạm trong log, không trong message).
  6. Trong transaction: advisory lock caregiver → người bệnh (hiện có), kiểm trùng (hiện có).
  7. Sửa race SEC-11: `accept` khóa dòng booking **trước**, đọc `caregiverId` hiện tại sau khóa. Nếu khác giá trị đã resolve thì thoát và thử lại một lần (resolve lại ngoài transaction).
- **DB:** — (cấu hình `BOOKING_MAX_PENDING_PER_CUSTOMER`).
- **Lỗi:** `CUSTOMER_HAS_OUTSTANDING_DEBT (5157, 409)`, `TOO_MANY_PENDING_BOOKINGS (5228, 409)`, `CAREGIVER_NOT_AVAILABLE (5226, 409)`, `PAYMENT_SERVICE_UNAVAILABLE (5154, 503)`, `CAREGIVER_SERVICE_NOT_SUPPORTED (5406, 409)`.
- **AC:**
  - Given caregiver không có lịch rảnh ngày D, When đặt, Then 409 `CAREGIVER_NOT_AVAILABLE`.
  - Given khách có hóa đơn OVERDUE, Then 409 `CUSTOMER_HAS_OUTSTANDING_DEBT`.
  - Given 3 booking PENDING, When đặt booking thứ 4, Then 409.
  - Given Provider tắt, Then 503 và không có booking mới.

#### OPS-06 — Job MISSED + báo khách vắng mặt (CUSTOMER_NO_SHOW)
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S4 · **Phụ thuộc:** OPS-04, OPS-04 (`booking_outbox`), PAY-10

- **Role:** hệ thống; CAREGIVER (báo); CENTER_MANAGER (xác nhận).
- **API:**
  - `POST /api/v1/booking-occurrences/{id}/no-show-reports` — CAREGIVER, request `{note (@NotBlank, 10–1000)}`.
  - `POST /api/v1/booking-occurrences/{id}/no-show-reports/decision` — MANAGER, request `{approved (@NotNull), note?}`.
- **Quy tắc nghiệp vụ:**
  1. **Báo vắng:**
     - caregiver được gán;
     - buổi ∈ {SCHEDULED, ACKNOWLEDGED}, chưa có session;
     - `start_at ≤ now < end_at + 60 phút`.

     Ghi `no_show_reported_at/by/note`. Thông báo manager `NO_SHOW_REPORTED`. Buổi **chưa đổi trạng thái**.
  2. **Manager quyết định:**
     - `approved=true` → buổi `CUSTOMER_NO_SHOW` (phí 100% theo snapshot; tiền tính ở PAY-11 khi FINALIZE);
     - `approved=false` → buổi `MISSED` (`missed_reason = CAREGIVER`) → refund `CAREGIVER_MISSED` qua outbox.

     Sau đó `finalizeIfDone`. Thông báo khách.
  3. **Job MISSED** (`BOOKING_MISSED_SCAN_INTERVAL` 5 phút, `SKIP LOCKED` sau BK-09): buổi ∈ {SCHEDULED, ACKNOWLEDGED}, không session, **không** có báo vắng, booking ∈ {CONFIRMED, IN_PROGRESS}, `start_at + BOOKING_MISSED_GRACE_MINUTES (30) ≤ now` → `MISSED`:
     - refund 100% cọc buổi (`CAREGIVER_MISSED`);
     - thông báo manager + khách;
     - `finalizeIfDone`.

     Mỗi buổi một transaction. Lỗi một buổi không dừng job.
  4. Buổi có báo vắng mà manager chưa quyết định sau `end_at + 24h` → nhắc manager (không tự quyết).
  5. Manager muốn thay người cho buổi sắp trễ → dùng đổi caregiver (BK-05) **trước** khi job chạy. Đã MISSED thì không mở lại.
- **DB (Booking):** `booking_occurrences` + `missed_reason varchar(30)`, `no_show_reported_at`, `no_show_reported_by`, `no_show_note`, `no_show_decided_by`, `no_show_decided_at`.
- **Lỗi:** `NO_SHOW_NOTE_REQUIRED (5812, 400)`, `NO_SHOW_NOT_REPORTED (5813, 409)`, `INVALID_OCCURRENCE_TRANSITION (5221, 409)`, `NO_SHOW_WINDOW_INVALID (5817, 409)`.
- **AC:**
  - Given buổi 08:00 không ai check-in, When job chạy lúc 08:31, Then MISSED, Payment nhận refund 1 lần, manager nhận thông báo.
  - Given caregiver báo vắng lúc 08:10, Then job bỏ qua buổi đó; manager duyệt thì `CUSTOMER_NO_SHOW`.
  - Given buổi cuối cùng thành MISSED và mọi buổi khác bị hủy, Then booking CANCELLED.

#### SRCH-01 — Tìm caregiver (giao tập dịch vụ, lọc, sắp xếp, lịch trống) + chi tiết công khai
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 8 · **Sprint:** S5 · **Phụ thuộc:** PRV-01, PRV-02, FB-01 (rating; không bắt buộc — dùng `rating_average` hiện có), C5.1

- **Role:** mọi user đã đăng nhập (CUSTOMER là chính).
- **API:** `GET /api/v1/caregivers/search` (ProviderService).
  - Query:

    | Tham số | Ràng buộc |
    |---|---|
    | `centerId` | bắt buộc |
    | `careServiceIds` | ≥ 1, ≤ 10 |
    | `gender` | |
    | `minExperienceYears` | |
    | `minRating` | 0–5 |
    | `keyword` | |
    | `slotStart`, `slotEnd` | tùy chọn, ISO; hoặc `slots` lặp tối đa 60 — FE gửi danh sách buổi từ BK-01 |
    | `sort` | `RATING_DESC` (mặc định), `EXPERIENCE_DESC`, `NAME_ASC` |
    | `page`, `size` | `size ≤ 50` |

  - Response `Page<CaregiverCardResponse>`: `caregiverId`, `fullName`, `gender`, `experienceYears`, `ratingAverage`, `ratingCount`, `avatarUrl`, `careServiceIds`, `verifiedCertificateCount`.
- **API chi tiết công khai:** `GET /api/v1/caregivers/{caregiverId}/public` → hồ sơ (bio, kinh nghiệm, dịch vụ), `certificates[]` **chỉ ADMIN_VERIFIED**, `rating`. Review phân trang lấy từ Booking `GET /api/v1/feedbacks?caregiverId=` (FB-01) — FE gọi riêng.
- **Quy tắc nghiệp vụ:**
  1. Chỉ trung tâm ACTIVE; caregiver VERIFIED + ACTIVE, chưa xóa.
  2. **Giao tập dịch vụ:** `caregiver_services.care_service_id IN (:ids) GROUP BY caregiver_id HAVING COUNT(DISTINCT care_service_id) = :n` (Specification / native query có `@Param`).
  3. Lọc giới tính, kinh nghiệm, rating, keyword bằng JPA Specification.
  4. Khi có slot:
     - lọc thêm theo lịch rảnh / ngày nghỉ (PRV-02, tính trong Provider);
     - gọi Booking `POST /api/v1/booking-occurrences/busy-caregivers` (SERVICE; C5.1) với danh sách id còn lại (≤ 200 / lần) → loại caregiver bận.

     Vì lọc sau phân trang làm lệch tổng số, quy trình:
     - (a) lấy toàn bộ id thỏa điều kiện tĩnh (giới hạn 500);
     - (b) lọc lịch;
     - (c) sắp xếp và phân trang trong bộ nhớ.

     Vượt 500 → yêu cầu thu hẹp, trả 400 `SEARCH_TOO_BROAD`.
  5. Booking lỗi → 503 (không trả danh sách sai).
  6. `GET /caregivers/{id}` (cũ) giữ cho ADMIN / MANAGER / chính caregiver (SEC-08); khách dùng `/public`.
- **Lỗi:** `CARE_SERVICE_IDS_REQUIRED (4210, 400)`, `SEARCH_TOO_BROAD (4211, 400)`, `CENTER_NOT_ACTIVE (4116, 409)`, `BOOKING_SERVICE_UNAVAILABLE (4700, 503)`.
- **AC:**
  - Given CG1 {A, B}, CG2 {A}, When tìm {A, B}, Then chỉ CG1.
  - Given CG1 bận slot S, When tìm có slot S, Then không có CG1.
  - Given CG1 nghỉ ngày D, Then loại.
  - Given `minRating=4`, Then chỉ caregiver `ratingAverage ≥ 4`.
  - Given `/public`, Then không có chứng chỉ PENDING / CENTER_VERIFIED.

#### BK-03 — Khách hủy booking / từng buổi + preview-cancel
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 8 · **Sprint:** S5 · **Phụ thuộc:** PAY-09, PAY-10, NTF-01, OPS-04

- **Role:** CUSTOMER (chủ booking).
- **API:**
  - `POST /api/v1/bookings/{id}/cancellation-previews` — request `{occurrenceIds?: [uuid]}` (rỗng = toàn bộ buổi chưa diễn ra) → `CancellationPreviewResponse` (`items[{occurrenceId, startAt, priceAmount, paidAmount, feeAmount, refundAmount, rule}]`, `totalRefund`, `totalFee`, `previewToken`, `expiresAt`).
  - `POST /api/v1/bookings/{id}/cancel` (sửa endpoint hiện có) — request `{reason (@NotBlank, ≤ 1000), occurrenceIds?, previewToken?}` → `BookingDetailResponse` + `refund` (`totalRefund`, `refundIds[]`).
- **Quy tắc nghiệp vụ:**
  1. **Buổi hủy được:** thuộc booking; status ∈ {SCHEDULED, ACKNOWLEDGED}; không có care session; `startAt > now`. Có buổi không hợp lệ trong danh sách chỉ định → 409 `OCCURRENCE_NOT_CANCELLABLE` (không hủy phần nào).
  2. **Booking hủy được:** status ∈ {PENDING, AWAITING_DEPOSIT, CONFIRMED, IN_PROGRESS}. PENDING / AWAITING_DEPOSIT chỉ cho hủy **cả booking** (chưa trả tiền).
  3. **Preview:** gọi Payment `POST /refund-quotes` (SERVICE, `reason=CUSTOMER_CANCEL`). Trả kết quả kèm `previewToken` = HMAC(bookingId + danh sách buổi + `totalRefund`, khóa nội bộ), hạn 10 phút. **Không ghi DB.**
  4. **Cancel:** nếu có `previewToken` mà kết quả tính lại khác (vd đã sang mốc 24h) → 409 `CANCELLATION_PREVIEW_EXPIRED` để FE hỏi lại khách. Không có token thì vẫn hủy (mobile cũ) và trả số tiền thực tế.
  5. **Transaction** (khóa booking):
     - buổi được chọn → CANCELLED (`cancelled_at`, `cancelled_by_party=CUSTOMER`, `cancel_reason`);
     - ghi `booking_status_history`;
     - hủy **cả booking** khi booking chưa bắt đầu → `CANCEL_BY_CUSTOMER` → CANCELLED;
     - IN_PROGRESS / CONFIRMED mà vẫn còn buổi khác → booking giữ trạng thái;
     - không còn buổi nào chưa kết thúc → gọi `BookingLifecycle.finalize` (OPS-04): có buổi COMPLETED / CUSTOMER_NO_SHOW → COMPLETED, ngược lại CANCELLED.
  6. **Sau commit** (ngoài transaction):
     - gọi Payment `POST /refunds` (SERVICE, `idempotencyKey=CANCEL:<bookingId>:<occurrenceId>` cho từng buổi gộp trong một request);
     - Payment lỗi → ghi `booking_outbox` (tạo ở OPS-04) `PENDING`, dispatcher retry, **không** rollback việc hủy;
     - gửi thông báo cho khách, manager, caregiver của buổi (NTF-02).
  7. `BookingAction`: thêm cho phép CANCEL_BY_CUSTOMER từ IN_PROGRESS chỉ qua `finalize` (không cho trực tiếp). Cập nhật `BookingActionTests`.
- **DB (Booking):**
  - `booking_occurrences` + `cancelled_at`, `cancelled_by_party varchar(20)` CHECK (`CUSTOMER`, `CENTER`, `SYSTEM`, `ADMIN`), `cancel_reason` (đã có).
  - Dùng `booking_outbox` đã tạo ở OPS-04 (không tạo bảng mới).
- **Lỗi:** `OCCURRENCE_NOT_CANCELLABLE (5150, 409)`, `OCCURRENCE_NOT_IN_BOOKING (5153, 400)`, `CANCEL_SCOPE_NOT_ALLOWED (5151, 409)` (hủy lẻ khi PENDING / AWAITING_DEPOSIT), `CANCELLATION_PREVIEW_EXPIRED (5159, 409)`, `PAYMENT_SERVICE_UNAVAILABLE (5154, 503)` (chỉ ở preview), `REASON_REQUIRED (5106, 400)`.
- **AC:**
  - Given booking CONFIRMED 3 buổi, When preview hủy buổi 2 còn 48h, Then `rule TIER_24_72`.
  - When cancel với `previewToken`, Then buổi 2 CANCELLED, booking vẫn CONFIRMED, Payment nhận refund 1 lần.
  - Given buổi 1 đang IN_PROGRESS, When hủy buổi 1, Then 409.
  - Given Payment tắt lúc cancel, Then buổi vẫn CANCELLED và refund được gửi lại khi Payment bật.
  - Given hủy toàn bộ khi chưa buổi nào bắt đầu, Then booking CANCELLED.

#### SRCH-02 — Danh sách trung tâm ACTIVE cho khách
**Người:** [Người B] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S5 · **Phụ thuộc:** CAT-02

- **Role:** mọi user đã đăng nhập (đề xuất mở public — permit ở Gateway / Provider khi nhóm đồng ý; mặc định yêu cầu đăng nhập).
- **API:**
  - `GET /api/v1/care-centers/public?keyword=&careServiceId=&minPrice=&maxPrice=&page=&size=` → `CenterCardResponse`: `centerId`, `centerName`, `address`, `operatingHours`, `phoneNumber`, `imageUrls[≤ 3]`, `minPrice`, `serviceCount`. **Không** có email nội bộ, `managerUserId`, `createdBy`.
  - `GET /api/v1/care-centers/public/{centerId}`.
- **Quy tắc nghiệp vụ:**
  1. Chỉ `center_status = ACTIVE`, chưa xóa.
  2. Lọc theo dịch vụ / giá (O7): Provider gọi Catalog `GET /api/v1/center-services/price-summary?careServiceId=&minPrice=&maxPrice=` (endpoint mới trong CAT-02, trả `[{centerId, minPrice}]`, SERVICE). Provider giao kết quả với danh sách trung tâm ACTIVE.
  3. Ảnh: dùng `provider_media` owner CARE_CENTER; presigned URL lấy qua Media bằng token SERVICE.
- **Lỗi:** `CATALOG_UNAVAILABLE (4701, 503)`, `INVALID_PAGE (4003, 400)`.
- **AC:**
  - Given trung tâm SUSPENDED, Then không xuất hiện.
  - Given lọc `maxPrice=150.000`, Then chỉ trung tâm có plan ≤ 150.000 cho dịch vụ đã chọn.

#### BK-04 — Trung tâm từ chối / hủy, hủy hệ thống (khóa trung tâm) + hoàn tiền
**Người:** [Người B] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S6 · **Phụ thuộc:** BK-03 (cơ chế refund), PAY-10

- **Role:** CENTER_MANAGER (trung tâm mình), SERVICE (Provider khi khóa trung tâm / caregiver).
- **API:**
  - `POST /api/v1/bookings/center/{id}/reject` (hiện có);
  - `POST /api/v1/bookings/center/{id}/cancel` (hiện có) — MANAGER, request mở rộng `{reason, occurrenceIds?}` (rỗng = mọi buổi chưa diễn ra);
  - `POST /api/v1/bookings/system-cancellations` — SERVICE, request `{centerId?, caregiverId?, reason: CENTER_LOCKED \| CAREGIVER_DEACTIVATED, note}` → `{affectedBookings, affectedOccurrences}`.
- **Quy tắc nghiệp vụ:**
  1. **Từ chối** (PENDING): không có tiền → không refund; thông báo khách.
  2. **Trung tâm hủy:**
     - mọi buổi chưa diễn ra (hoặc buổi chỉ định) → CANCELLED (`cancelled_by_party=CENTER`);
     - refund `reason=CENTER_CANCEL` 100% số đã trả;
     - nếu còn buổi đã COMPLETED → `finalize`.
  3. **Hủy hệ thống, `CENTER_LOCKED`:**
     - mọi booking của trung tâm ở PENDING / AWAITING_DEPOSIT / CONFIRMED / IN_PROGRESS → hủy các buổi chưa diễn ra;
     - booking PENDING / AWAITING_DEPOSIT → CANCELLED (không tiền);
     - CONFIRMED / IN_PROGRESS → refund `CENTER_LOCKED` 100%.

     Xử lý theo lô 50 booking, mỗi booking một transaction; lỗi một booking không dừng cả lô; idempotent (chạy lại không hủy lần 2).
  4. **`CAREGIVER_DEACTIVATED`:** không hủy. Đánh dấu các buổi tương lai của caregiver `needs_reassignment=true`, thông báo manager phải đổi người (BK-05). Quá giờ chưa đổi thì job MISSED xử lý (OPS-06).
  5. Thông báo cho khách và caregiver liên quan.
- **DB:** `booking_occurrences` + `needs_reassignment boolean NN default false`.
- **Lỗi:** `INVALID_BOOKING_TRANSITION (5101, 409)`, `REASON_REQUIRED (5106, 400)`.
- **AC:**
  - Given booking CONFIRMED đã cọc 450.000, When manager hủy, Then refund 450.000 (mọi buổi chưa diễn ra).
  - Given trung tâm bị khóa với 5 booking, When Provider gọi `system-cancellations` 2 lần, Then chỉ một lượt refund.
  - Given manager C2 hủy booking của C1, Then 404.

#### BK-05 — Audit đổi caregiver + lịch sử trạng thái booking
**Người:** [Người B] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S6 · **Phụ thuộc:** BK-02

- **Role:** CUSTOMER (booking mình), CENTER_MANAGER, ADMIN.
- **API:** `GET /api/v1/bookings/{id}/history?page=&size=` → `[{eventType, fromStatus, toStatus, occurrenceId, oldCaregiverId, newCaregiverId, changedBy, reason, changedAt}]`.
- **Quy tắc nghiệp vụ:**
  1. Đổi caregiver cấp booking / từng buổi (`BSI:266-312`) ghi một dòng `booking_events` mỗi buổi bị đổi (`eventType=CAREGIVER_CHANGED`) + thông báo caregiver cũ ("lịch bị hủy giao"), caregiver mới ("lịch mới"), khách.
  2. Đổi caregiver từng buổi: nếu mọi buổi tương lai đều thuộc caregiver mới thì cập nhật luôn caregiver cấp booking (để danh sách / lọc đúng; sửa `BookingSpecifications.java:37-39` lọc theo cả cấp buổi).
  3. Ghi chuyển trạng thái buổi (CANCELLED, MISSED, CUSTOMER_NO_SHOW, COMPLETED) vào `booking_events`.
  4. Gộp `booking_status_history` cũ vào API (đọc hai bảng, sắp theo thời gian) — không migrate dữ liệu.
  5. Scope theo role như các API booking khác (ngoài phạm vi → 404).
- **DB:** `booking_events`:
  - `event_id` PK, `booking_id` FK, `occurrence_id` null, `event_type` varchar CHECK;
  - `old_caregiver_id`, `new_caregiver_id`, `from_status`, `to_status`;
  - `changed_by` uuid null, `reason`, `created_at`.
- **Lỗi:** `BOOKING_NOT_FOUND (5100, 404)`.
- **AC:**
  - Given manager đổi caregiver buổi 3, Then history có `CAREGIVER_CHANGED` với id cũ / mới, và caregiver mới nhận thông báo.
  - Given lọc booking theo caregiver mới, Then thấy booking.

#### BK-06 — Lịch sử booking: lọc, sắp xếp, phân trang
**Người:** [Người B] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S6 · **Phụ thuộc:** BK-08

- **Role:** CUSTOMER, CENTER_MANAGER, ADMIN, CAREGIVER (lịch sử ca).
- **API:**
  - `GET /api/v1/bookings?status=&recipientId=&centerId=&from=&to=&keyword=(bookingCode)&sort=createdAt,desc|startDate,asc&page=&size=`;
  - `GET /api/v1/bookings/{id}/occurrences?status=&page=&size=`;
  - `GET /api/v1/booking-occurrences?from=&to=&status=&page=&size=` (caregiver; bỏ giới hạn 62 ngày khi có phân trang).
- **Quy tắc nghiệp vụ:**
  1. Lọc ngày theo **khoảng giao** với `[start_date, end_date]`; quy đổi ngày theo `Asia/Ho_Chi_Minh`.
  2. Sort chỉ cho whitelist field; field khác → 400 `INVALID_SORT`.
  3. Response danh sách có `nextOccurrenceAt`, `completedCount`, `totalCount`.
- **Lỗi:** `INVALID_SORT (5112, 400)`, `INVALID_DATE_FILTER (5219, 400)`, `INVALID_PAGE (5003, 400)`.
- **AC:**
  - Given 30 booking, When `page=1&size=10&sort=startDate,asc`, Then đúng 10 dòng thứ 11–20 theo ngày bắt đầu.
  - Given `sort=customerId`, Then 400.

#### BK-09 — Chống trùng lịch tầng DB + scheduler nhiều instance
**Người:** [Người B] · **Ưu tiên:** P2 · **SP:** 3 · **Sprint:** S6 · **Phụ thuộc:** BK-03

- **Nội dung:**
  1. Thêm extension `btree_gist`. Thêm cột `active_slot boolean` (true khi buổi ∈ {SCHEDULED, ACKNOWLEDGED, IN_PROGRESS} và booking đang giữ chỗ — cập nhật ở mọi chuyển trạng thái). Exclusion constraint:

     ```sql
     EXCLUDE USING gist (caregiver_id WITH =, tstzrange(start_at, end_at, '[)') WITH &&) WHERE (active_slot)
     ```
  2. `GlobalExceptionHandler` map vi phạm constraint → `CAREGIVER_SCHEDULE_CONFLICT (5215)`.
  3. Mọi scheduler Booking (EXPIRE, MISSED, refund retry, notification): `SELECT … FOR UPDATE SKIP LOCKED` khi lấy lô.
  4. Test chỉ chạy trên PostgreSQL (`-Dbooking.testing.postgres=true`): 2 thread tạo booking trùng slot → đúng 1 thành công.
- **DB:** change script thêm extension, cột, constraint; backfill `active_slot`.
- **AC:**
  - Given hai request song song cùng caregiver cùng giờ (bỏ qua advisory lock trong test), Then DB chặn một request.
  - Given 2 instance scheduler, Then mỗi booking chỉ EXPIRE một lần.

#### INF-01 — Tài liệu chung, e2e, Docker cập nhật theo kiến trúc mới
**Người:** [Người B] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S6 (bắt đầu S1 với phần e2e chạy lại được) · **Phụ thuộc:** —

- **Nội dung:**
  1. `AGENTS.md` gốc:
     - bảng domain: Review / Complaint / Notification → BookingService; Payment → PaymentService;
     - bảng port / dải lỗi (Payment 8087 / 6xxx, Media 7xxx);
     - mục token SERVICE (thay đoạn "không còn `/internal`");
     - ngoại lệ rule #3 cho IPN VNPay.
  2. `DOCKER.md`, `compose.yaml`: thêm Payment. Sửa override URL trong container (`BOOKING_CARE_CATALOG_URL`, `PROVIDER_MEDIA_URL`, `BOOKING_PAYMENT_URL` …).
  3. `scripts/e2e`:
     - bỏ `*_INTERNAL_SECRET`, đặt `*_SERVICE_CLIENT_*`;
     - sửa path trong `e2e.py` (`/api/v1/bookings/center` …);
     - thêm Catalog, Media, Payment vào dựng hạ tầng;
     - phase a–f chạy lại được trước cuối S2.
  4. `docs/PLAN.md`: cập nhật các mục đã xong / mới.
  5. README / AGENTS từng service do **chủ module** cập nhật trong task của họ; INF-01 chỉ kiểm tra chéo trước khi kết thúc S6.
- **AC:**
  - Given repo sạch, When `Run-E2E.ps1`, Then `E2E PASSED`.
  - Given `grep -r "X-Internal-Secret\|/internal/" */README.md */AGENTS.md`, Then không còn kết quả (trừ mục lịch sử).

---

## Lịch sprint của [Người B]

| Sprint | Task (SP) | Mục tiêu demo chung cuối sprint |
|---|---|---|
| **S1** | 🔒 IAM-01 (5), 🔒 GW-01 (3), BK-07 (3), BK-08 (2), CAT-01 (3) — 16 SP | Hết toàn bộ lỗ hổng SEC-01…08; có token SERVICE; PaymentService chạy trong Docker; publish stub contract §7 |
| **S2** | CAT-02 (3), 🔒 PRV-01 (5), 🔒 BK-01 (5), OPS-01 (3) — 16 SP | Báo giá có giá từng buổi; khách nhận URL VNPay; caregiver acknowledge; thông báo in-app + SSE |
| **S3** | 🔒 OPS-02 (8), 🔒 OPS-04 (5), PRV-03 (5) — 18 SP | **Luồng chính end-to-end**: đặt → duyệt → cọc VNPay sandbox → CONFIRMED → check-in → task → check-out → COMPLETED; xác minh chứng chỉ 2 bước |
| **S4** | 🔒 PRV-02 (8), BK-02 (5), OPS-06 (5) — 18 SP | Kiểm lịch rảnh / kỹ năng / nợ khi đặt; MISSED + khách vắng tự động hoàn tiền; care report có ảnh; đánh giá caregiver; preview hoàn tiền đúng bảng 2.2 |
| **S5** | SRCH-01 (8), BK-03 (8), SRCH-02 (3) — 19 SP | Tìm caregiver đủ dịch vụ / lịch trống; hủy từng buổi / cả booking có hoàn tiền; REMAINING + chặn nợ; khiếu nại có escalate |
| **S6** | BK-04 (5), BK-05 (3), BK-06 (3), BK-09 (3), INF-01 (3) — 17 SP | Khóa trung tâm dây chuyền (hủy hệ thống + hoàn 100%); lịch sử booking; đối soát; dashboard doanh thu / vận hành; e2e toàn hệ thống PASSED |
| **Tổng** | **104** |  |

---

## Contract [Người B] cài đặt hoặc sử dụng (trích §7)

Ký hiệu `[A]`, `[B]`, `[C]` là người cài đặt / gọi. Chi tiết đầy đủ và các contract còn lại: `docs/WORK_PLAN.md` §7.

### C1 — Token service (Identity)

| # | Chữ ký | Cài đặt | Dùng bởi |
|---|---|---|---|
| C1.1 | `POST /api/v1/auth/service-token` `{clientId, clientSecret}` → `{accessToken, tokenType, expiresInSeconds}` | [Người B] IAM-01 | Mọi service |
| C1.2 | Java: `ServiceTokenProvider.getToken(): String` + `ServiceTokenInterceptor implements RequestInterceptor` (chép vào `client/` từng service) | [Người B] mẫu ở Booking; mỗi chủ service chép | Feign client cần quyền service |

### C2 — Booking → Payment

| # | Chữ ký | Cài đặt | Gọi bởi | Khi nào |
|---|---|---|---|---|
| C2.1 | `GET /api/v1/payment-policies/effective?centerId&at` → `PolicySnapshotResponse {policyId, depositPercent, commissionPercent, refundTiers[], coolingOffMinutes, coolingOffMinLeadHours, customerNoShowChargePercent, remainingDueHours, paymentSessionTtlMinutes, depositDueHours}` | [A] PAY-03 | [B] BK-01 (báo giá); [B] accept → `deposit_due_at` theo `depositDueHours` | Báo giá, accept |
| C2.2 | `POST /api/v1/refund-quotes` (SERVICE) `RefundQuoteRequest {bookingId, bookingStatus, cancelledBy, reason, cancelledAt, firstUpcomingStartAt, occurrences[{occurrenceId, startAt, status, hasSession}]}` → `RefundQuoteResponse {items[{occurrenceId, priceAmount, paidAmount, feeAmount, refundAmount, rule}], totalPaid, totalFee, totalRefund, policyId}` | [A] PAY-09 | [B] BK-03 | Preview hủy |
| C2.3 | `POST /api/v1/refunds` (SERVICE) `RefundCreateRequest {bookingId, occurrenceIds[], reason, cancelledBy, cancelledAt, firstUpcomingStartAt, requestedBy, idempotencyKey}` → `RefundBatchResponse {items[{refundId, occurrenceId, amount, feeAmount, status}], totalRefund}` | [A] PAY-10 | [B] BK-03, BK-04, OPS-06 (`CAREGIVER_MISSED`) | Sau commit hủy / MISSED |
| C2.5 | `POST /api/v1/remaining-invoices` (SERVICE) `{bookingId, occurrences[{occurrenceId, finalStatus}]}` → `RemainingInvoiceResponse {invoiceId?, amount, dueAt, status}` (amount 0 → không tạo hóa đơn, `invoiceId` null) | [A] PAY-11 | [B] OPS-04 | Booking COMPLETED |
| C2.6 | `GET /api/v1/payments/me/outstanding` (CUSTOMER, token khách chuyển tiếp) → `{hasDebt, overdueInvoices[], pendingInvoices[]}`; `GET /api/v1/payments?bookingId=` (thêm quyền SERVICE) → danh sách payment + `refundedAmount` | [A] PAY-11, PAY-16 | [B] BK-02; [C] FB-02 | Tạo booking; trước admin-resolve |

### C3 — Payment → Booking

| # | Chữ ký | Cài đặt | Gọi bởi | Khi nào |
|---|---|---|---|---|
| C3.1 | `POST /api/v1/bookings/{id}/deposit-confirmations` (SERVICE) `DepositConfirmationRequest {paymentId, amount, paidAt, policySnapshot, occurrenceDeposits[{occurrenceId, depositAmount}]}` → `BookingDetailResponse`; idempotent theo `paymentId` | [A] PAY-08 (file Booking, [B] review) | [A] PAY-06 (outbox) | IPN thành công — thay sự kiện `PaymentSucceeded(bookingId)` |
| C3.2 | `GET /api/v1/bookings/{id}/payment-context` (SERVICE) → `{bookingId, bookingCode, customerId, centerId, status, totalAmount, depositDueAt, firstStartAt, depositPaidAt, occurrences[{occurrenceId, occurrenceNo, startAt, endAt, status, priceAmount, depositAmount, caregiverId, hasSession}]}` | [A] PAY-08 (dữ liệu giá từ [B] BK-01) | [A] PAY-05, PAY-09 | Tạo phiên, tính hoàn |

### C4 — Thông báo (BookingService)

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C4.1 | Java trong Booking: `NotificationService.notify(List<UUID> recipientUserIds, NotificationType type, Map<String,Object> params, String refType, UUID refId)` — chỉ gọi sau commit | [C] NTF-01 | [B] (BK-03/04/05, OPS-01/02/04/06, PAY-08), [C] (OPS-03/05, FB, NTF-02) |

### C5 — Provider ↔ Booking

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C5.1 | `POST /api/v1/booking-occurrences/busy-caregivers` (SERVICE) `{caregiverIds[≤ 200], slots[{startAt, endAt}] (≤ 60)}` → `{busyCaregiverIds[]}` (buổi ∈ {SCHEDULED, ACKNOWLEDGED, IN_PROGRESS} của booking đang giữ chỗ) | [B] (Booking, làm trong SRCH-01) | [B] SRCH-01, PRV-02 |
| C5.2 | `POST /api/v1/caregivers/availability-checks` (SERVICE) `{caregiverId, slots[]}` → `{available, violations[{startAt, endAt, reason}]}` | [B] PRV-02 | [B] BK-02 |
| C5.4 | `POST /api/v1/bookings/system-cancellations` (SERVICE) `{centerId?, caregiverId?, reason: CENTER_LOCKED \| CAREGIVER_DEACTIVATED, note}` → `{affectedBookings, affectedOccurrences}` | [B] BK-04 | [C] PRV-04 |
| C5.5 | `GET /api/v1/caregivers/{id}` (SERVICE được phép) → thêm `careServiceIds[]`, `centerServiceIds[]` | [B] PRV-01 | [B] Booking `CaregiverPortImpl` |

### C7 — CareRecipient ↔ Booking

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C7.1 | `GET /api/v1/care-recipients/{id}/snapshot`, `GET /{id}/access?userId&minLevel` (SERVICE) | [C] CR-01 | [B] Booking `create` |
| C7.2 | `GET /api/v1/bookings/caregiver-access?caregiverUserId&recipientId` (SERVICE) → `{allowed}` theo buổi | [B] BK-07 | [C] CareRecipient `getForCaregiver` |

### C9 — Catalog

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C9.1 | `POST /api/v1/center-services/quote` — **bắt buộc** `pricePlanId` | [B] CAT-01 | [B] BK-01 |
| C9.2 | `GET /api/v1/center-services/price-summary?careServiceId&minPrice&maxPrice` (SERVICE) → `[{centerId, minPrice}]` | [B] CAT-02 | [B] SRCH-02 |

### Hàm nội bộ dùng chung trong BookingService (cần thống nhất giữa [B] và [C])

| Hàm | Chủ | Dùng bởi |
|---|---|---|
| `BookingLifecycle.apply(Booking, BookingAction, actor, reason)` | [B] (hiện có trong `BSI.applyAction`) | Mọi chuyển trạng thái booking |
| `BookingLifecycle.finalizeIfDone(Booking)` + action `FINALIZE` | [B] OPS-04 | [B] BK-03/04, OPS-06 |
| `OccurrenceTransitions.cancel / markMissed / markNoShow / start / complete / acknowledge / resetAcknowledgement` | [B] OPS-01/02/04/06, BK-03 | [B]; [C] chỉ đọc trạng thái (OPS-03/05, FB-01) |
| `BookingOutbox.enqueue(eventType, payload)` + dispatcher | [B] OPS-04 (tạo) | [B] BK-03/04, OPS-06; [C] FB-01/02 (rating, hoàn tiền khiếu nại) |
| `BookingEvents.record(...)` (`booking_events`) | [B] BK-05 | Cả hai |
