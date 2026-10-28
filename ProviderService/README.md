# CareConnect ProviderService

ProviderService sở hữu dữ liệu trung tâm chăm sóc, hồ sơ caregiver, chứng chỉ, ca làm việc, đăng ký ca và lịch caregiver.
Dịch vụ chạy Spring Boot 4.1.1 / Java 21 tại port mặc định `8084`; PostgreSQL riêng tại host port `5444`.

## Luồng nghiệp vụ đã triển khai

1. Admin CRUD trung tâm, gán `managerUserId` và chuyển trung tâm sang `ACTIVE`.
2. Admin hoặc `CENTER_MANAGER` của đúng trung tâm tạo hồ sơ caregiver. Hồ sơ mới là `PENDING/INACTIVE`.
3. Caregiver hoặc trung tâm thêm chứng chỉ. Không có bảng degree và certificate không có `mediaFileId`.
4. Admin duyệt/từ chối hồ sơ và từng chứng chỉ. Hồ sơ được duyệt chuyển sang `VERIFIED/ACTIVE`.
5. Trung tâm tạo ca `DRAFT` hoặc `OPEN`.
6. Caregiver đã `VERIFIED/ACTIVE` đăng ký ca `OPEN` thuộc trung tâm của mình.
7. Trung tâm duyệt đăng ký. Hệ thống khóa bản ghi để kiểm tra capacity, chặn trùng lịch và tự tạo `caregiver_schedules`.
8. Trung tâm gán kỹ năng (offering của Catalog) cho caregiver — Booking chỉ cho đặt dịch vụ caregiver có kỹ năng.
9. Lịch caregiver chỉ là **khung rảnh** `SCHEDULED`/`CANCELLED` (D11 — tiến độ ca thật ở BookingService); caregiver /
   trung tâm hủy lịch kèm lý do, bị chặn nếu đã có buổi được giao trong khung.
10. Ngày nghỉ caregiver + API kiểm lịch rảnh cho Booking (PRV-02); khách tìm caregiver / xem hồ sơ công khai (SRCH-01) và
    danh sách trung tâm đang hoạt động (SRCH-02).

ProviderService không lưu mật khẩu hay phát JWT. IdentityService vẫn xác thực người dùng; ProviderService chỉ xác minh JWT và kiểm tra role/quyền sở hữu tài nguyên.
Khi gán manager hoặc tạo caregiver profile, ProviderService gọi IdentityService (qua gateway) để kiểm tra tài khoản `ACTIVE` và đúng role trước khi mở transaction ghi dữ liệu.

## Khởi tạo local

```powershell
Copy-Item .env.example .env
# Điền password, JWT issuer/JWKS và PROVIDER_SERVICE_CLIENT_ID/SECRET trong .env; không đặt dấu nháy quanh JDBC URL.

docker compose --env-file .env up -d

Get-Content -Raw schema/provider.sql |
    docker compose --env-file .env exec -T postgres psql -U postgres -d CARECONNECT_PROVIDER -v ON_ERROR_STOP=1

.\scripts\Start-Dev.ps1
```

`provider.sql` chỉ dùng cho database mới. Nếu bạn đã chạy schema của ProviderService bản cũ, chạy đúng một lần:

```powershell
Get-Content -Raw schema/provider_upgrade_v2.sql |
    docker compose --env-file .env exec -T postgres psql -U postgres -d CARECONNECT_PROVIDER -v ON_ERROR_STOP=1
```

Kiểm tra container/database:

```powershell
docker compose --env-file .env ps
docker compose --env-file .env exec postgres psql -U postgres -d CARECONNECT_PROVIDER -c "\dt"
```

Swagger: `http://localhost:8084/swagger-ui.html`

## Role sử dụng

- `ADMIN`: CRUD center; quản lý toàn bộ dữ liệu; duyệt caregiver/certificate.
- `CENTER_MANAGER`: chỉ quản lý center có `manager_user_id` bằng `sub` trong JWT.
- `CAREGIVER`: quản lý chứng chỉ của mình, đăng ký ca, xem và cập nhật lịch của mình.
- `CUSTOMER`: tìm caregiver (`/caregivers/search`), xem hồ sơ công khai, lịch rảnh tổng hợp, trung tâm `ACTIVE`
  (`/care-centers/public`). Không đọc được hồ sơ đầy đủ `GET /caregivers/{id}` hay ca làm việc.
- `SERVICE` (token máy, vd BookingService): `GET /caregivers/{id}`, `/caregivers/by-user/{userId}`, `/care-centers/{id}`,
  `POST /caregivers/availability-checks`.

## API chính

Mọi path có prefix `/api/v1` (gateway không cắt prefix). Phân quyền từng method bằng `@PreAuthorize` + kiểm phạm vi
trung tâm trong service.

### Trung tâm — `/api/v1/care-centers`

- `GET /` (ADMIN, CENTER_MANAGER) · `GET /{centerId}` · `GET /{centerId}/schedules`
- `POST /` · `PUT /{centerId}` · `PATCH /{centerId}/status` · `DELETE /{centerId}` (ADMIN)
- Đã bỏ `PATCH /schedules/{id}/status` (trùng `PATCH /caregivers/schedules/{id}/status`).
- **Công khai cho khách (SRCH-02, 2026-10-22)** — mọi user đã đăng nhập (chưa mở anonymous):
  - `GET /public?keyword=&careServiceId=&minPrice=&maxPrice=&page=&size=` → `Page<CenterCardResponse>`
    `{centerId, centerName, address, operatingHours, phoneNumber, imageUrls[≤3], minPrice, serviceCount}` — **không**
    có email, `managerUserId`, `createdBy`. Chỉ `ACTIVE`, chưa xóa; keyword theo tên / địa chỉ; sắp xếp theo tên.
  - Có `careServiceId` / `minPrice` / `maxPrice` → gọi Catalog `GET /api/v1/center-services/price-summary` (C9.2, token
    SERVICE) rồi giao với trung tâm ACTIVE; Catalog lỗi → 503 `CATALOG_UNAVAILABLE` 4701. Không lọc → giá chỉ để hiển
    thị (Catalog lỗi → `minPrice` vắng). `minPrice > maxPrice` → 400 `PRICE_RANGE_INVALID` 4119.
  - `GET /public/{centerId}` — cùng DTO, tối đa 10 ảnh; trung tâm không ACTIVE → 404 4100.
  - `serviceCount` đọc từ Catalog nếu có (không nằm trong C9.2 → hiện là `null`). Ảnh: `provider_media` owner
    `CARE_CENTER`, URL presigned qua Media (`MediaClient`, token người dùng), best-effort.

### Caregiver — `/api/v1/caregivers`

- `GET /` (catalog caregiver VERIFIED/ACTIVE) · `GET /by-user/{userId}` — mọi token hợp lệ, kể cả token SERVICE.
- `GET /{caregiverId}` (hồ sơ đầy đủ) — chỉ ADMIN / CENTER_MANAGER của trung tâm / chính caregiver / **SERVICE**
  (BookingService, C5.5); người khác 403 (SRCH-01 quy tắc 6).
- `GET /centers/{centerId}` · `POST /centers/{centerId}` · `PUT /{caregiverId}` · `DELETE /{caregiverId}?version=`
- `GET /me` · `GET /me/schedules` (CAREGIVER) · `PATCH /{id}/verification` (ADMIN) · `PATCH /schedules/{id}/status`
- **Kỹ năng (PRV-01, 2026-10-22):**
  - `GET /{caregiverId}/services` — chính caregiver, manager của trung tâm, ADMIN →
    `[{centerServiceId, careServiceId, serviceName, assignedAt}]`.
  - `PUT /{caregiverId}/services` — CENTER_MANAGER của trung tâm caregiver. Body `{centerServiceIds: [uuid]}`
    (`@NotNull` → 4210, tối đa 50 → 4209; `[]` = bỏ hết; id trùng được gộp). **Thay toàn bộ** trong một transaction.
    Mỗi offering được kiểm qua Catalog `GET /api/v1/center-services/{id}` bằng **token SERVICE** (gọi trước transaction):
    khác trung tâm → 400 `CAREGIVER_SERVICE_NOT_IN_CENTER` 4207; không ACTIVE / Catalog 404 → 409
    `CAREGIVER_SERVICE_NOT_ACTIVE` 4208; Catalog lỗi → 503 `CATALOG_UNAVAILABLE` 4701 (không đổi dữ liệu); không xin
    được token SERVICE → 503 `SERVICE_TOKEN_UNAVAILABLE` 4700.
  - `CaregiverResponse` (chi tiết, danh sách, catalog) có thêm `careServiceIds` (id dịch vụ chuẩn) và
    `centerServiceIds`; caregiver chưa gán kỹ năng → `[]` (không null).
- **Ngày nghỉ + lịch rảnh (PRV-02, 2026-10-22):**
  - `GET /{caregiverId}/time-offs?from=&to=` (chính caregiver / manager / ADMIN) · `POST /{caregiverId}/time-offs`
    body `{startAt, endAt, reason?}` · `DELETE /{caregiverId}/time-offs/{timeOffId}` (xóa mềm).
  - Ngày nghỉ `[startAt, endAt)`: `endAt > startAt`, tối đa 30 ngày (400 4407); trùng ngày nghỉ khác → 409 4406;
    **caregiver tự khai báo** trùng buổi đã được giao → 409 `TIME_OFF_HAS_BOOKINGS` 4408 (hỏi Booking C5.1); manager
    được tạo (phải tự đổi người cho buổi bị ảnh hưởng). Booking lỗi → 503 `BOOKING_SERVICE_UNAVAILABLE` 4702.
  - `GET /{caregiverId}/availability?from=&to=` (mọi user đã đăng nhập, `to − from ≤ 31 ngày` → 4409) →
    `[{startAt, endAt}]` = hợp lịch `SCHEDULED` (giờ VN, ca liền kề được ghép) trừ ngày nghỉ. Chưa trừ buổi đã đặt.
  - `POST /availability-checks` (**SERVICE**, C5.2) body `{caregiverId, slots[{startAt, endAt}] (1..60)}` →
    `{available, violations[{startAt, endAt, reason: TIME_OFF | NO_SCHEDULE}]}` — slot hợp lệ khi nằm trọn trong lịch
    rảnh và không giao ngày nghỉ (ưu tiên báo `TIME_OFF`). Không xét buổi đã đặt (Booking tự kiểm).
  - `PATCH /schedules/{id}/status` chỉ còn **hủy** (`status=CANCELLED` + `reason` + `version`); lịch không còn
    SCHEDULED → 409 4402; khung lịch đã có buổi được giao → 409 `SCHEDULE_HAS_BOOKINGS` 4415 (hỏi Booking C5.1).
- **Tìm kiếm + hồ sơ công khai (SRCH-01, 2026-10-22)** — mọi user đã đăng nhập:
  - `GET /search?centerId=&careServiceIds=&gender=&minExperienceYears=&minRating=&keyword=&slotStart=&slotEnd=&slots=&sort=&page=&size=`
    → `Page<CaregiverCardResponse>` `{caregiverId, fullName, gender, experienceYears, ratingAverage, ratingCount,
    avatarUrl, careServiceIds, verifiedCertificateCount}`.
    - `centerId` bắt buộc (4213); `careServiceIds` 1..10 (4212); `minRating` 0–5 (4214); `minExperienceYears ≥ 0`
      (4215); `size ≤ 50` (4003); `sort` = `RATING_DESC` (mặc định) | `EXPERIENCE_DESC` | `NAME_ASC`. Khung giờ: cặp
      `slotStart`/`slotEnd` và/hoặc `slots` lặp lại dạng `<startISO>/<endISO>`, tổng ≤ 60 (4412 / 4413).
    - Trung tâm phải `ACTIVE` (409 4116); caregiver `VERIFIED` + `ACTIVE`, làm được **tất cả** dịch vụ (giao tập qua
      `caregiver_services`).
    - Quy trình: (a) lấy mọi ứng viên thỏa điều kiện tĩnh, > 500 → 400 `SEARCH_TOO_BROAD` 4211; (b) có khung giờ → lọc
      lịch rảnh / ngày nghỉ trong Provider rồi hỏi Booking (C5.1, ≤ 200 id / lần) loại caregiver bận — Booking lỗi →
      503 4702; (c) sắp xếp + phân trang trong bộ nhớ.
  - `GET /{caregiverId}/public` → `{caregiverId, centerId, centerName, fullName, gender, experienceYears, bio,
    ratingAverage, ratingCount, avatarUrl, services[{careServiceId, serviceName}], certificates[{certificateName,
    issuedBy, issuedDate, expiryDate}]}` — chỉ chứng chỉ `VERIFIED` (= ADMIN đã duyệt; PENDING/REJECTED ẩn), không có
    userId / ngày sinh / trạng thái / version. Hồ sơ chưa công khai → 404.

### Chứng chỉ — `/api/v1/certificates`, media — `/api/v1/provider-media`

- `GET|POST /certificates/caregivers/{caregiverId}`, `GET /certificates/caregivers/{caregiverId}/verified`,
  `PUT|DELETE /certificates/{id}`, `PATCH /certificates/{id}/verification` (ADMIN).
- `GET|POST /provider-media/{ownerType}/{ownerId}`, `DELETE /provider-media/{ownerType}/{ownerId}/{mediaFileId}`.

### Ca, đăng ký — `/api/v1/work-shifts`

- `GET /` — ADMIN mọi trung tâm (lọc `centerId` tùy chọn); CENTER_MANAGER chỉ trung tâm mình; CAREGIVER chỉ trung tâm
  của mình và **không thấy DRAFT**; `centerId` ngoài phạm vi → 403 4117; CUSTOMER → 403. `approvedCount` đếm bằng một
  query gom nhóm (không N+1).
- `GET /{id}` — cùng phạm vi với danh sách: ngoài phạm vi (trung tâm khác, hoặc DRAFT với caregiver) → 404 4300;
  CUSTOMER → 403.
- `POST /centers/{centerId}` · `PUT /{id}` · `DELETE /{id}?version=`
- `POST /{shiftId}/registrations` · `GET /registrations/me` · `DELETE /registrations/{id}?version=` (CAREGIVER)
- `GET /{id}/registrations` · `PATCH /registrations/{id}/review` (ADMIN, CENTER_MANAGER)

Quy tắc ca (PRV-03, 2026-10-22):
- Giờ ca (`workDate` + `startTime`/`endTime`) là **giờ Việt Nam** (`Asia/Ho_Chi_Minh`, `util/ProviderTime`); "bây giờ"
  lấy từ bean `Clock`.
- Đăng ký / duyệt: trung tâm phải `ACTIVE` (409 `CENTER_NOT_ACTIVE` 4116); ca đã tới giờ bắt đầu → 409
  `WORK_SHIFT_ALREADY_STARTED` 4308 (kể cả khi không đặt hạn đăng ký).
- Capacity: ca chuyển `CLOSED` khi số đăng ký APPROVED **bằng** capacity (đếm trước khi duyệt).
- Đăng ký lại được sau khi đăng ký cũ `CANCELLED`/`REJECTED` (unique một phần chỉ trên PENDING/APPROVED).

Các request cập nhật dùng `version` để chống ghi đè dữ liệu đồng thời.

### Gọi service khác

- IdentityService (`PROVIDER_IDENTITY_URL` = gateway): kiểm tài khoản khi gán manager / tạo caregiver — chuyển tiếp token
  người dùng (`FeignAuthForwardingInterceptor`); xin token SERVICE (`POST /api/v1/auth/service-token`,
  `PROVIDER_SERVICE_CLIENT_ID/SECRET`, cache tới `exp − 60s`).
- CareCatalogService (`PROVIDER_CARE_CATALOG_URL` = gateway): đọc offering + `price-summary` (C9.2) bằng token SERVICE
  (`ServiceTokenInterceptor`).
- BookingService (`PROVIDER_BOOKING_URL` = gateway): `POST /api/v1/booking-occurrences/busy-caregivers` (C5.1) bằng
  token SERVICE — `BookingAvailabilityService` chia lô ≤ 200 caregiver × ≤ 60 khung; lỗi → 503 4702.
- MediaService (`PROVIDER_MEDIA_URL`): URL ảnh công khai (thẻ trung tâm / caregiver) qua `GET /media/{id}/download-url`
  với token người dùng (endpoint `isAuthenticated()`), best-effort — Media lỗi thì ảnh vắng, danh sách vẫn trả.
- Không còn API `/internal/**` / `X-Internal-Secret`: BookingService đọc caregiver/trung tâm qua endpoint public bằng
  token SERVICE.

Booking là nguồn sự thật về giờ đã đặt: Provider chỉ giữ khung giờ làm việc (`caregiver_schedules`).

## Schema

DB mới: `schema/provider.sql`. DB đã có dữ liệu, áp tay theo thứ tự:
- `schema/changes/2026-10-22_caregiver_services.sql` — bảng `caregiver_services` (PRV-01).
- `schema/changes/2026-10-22_shift_registration_partial_unique.sql` — unique một phần cho `shift_registrations`
  (PENDING/APPROVED) và `caregiver_schedules` (khác CANCELLED) (PRV-03).
- `schema/changes/2026-10-22_caregiver_time_offs.sql` — bảng `caregiver_time_offs` (PRV-02).
- `schema/changes/2026-10-22_caregiver_schedule_status_d11.sql` — backfill `IN_PROGRESS`/`COMPLETED` → `SCHEDULED`,
  CHECK `schedule_status IN ('SCHEDULED','CANCELLED')` (PRV-02 / D11).

## Test

```powershell
.\mvnw.cmd clean test
```

Test dùng H2 PostgreSQL compatibility mode và không cần chạy Docker. 2026-10-22: **57/57**, trong đó
`ProviderIntegrationTests` (23) chạy MockMvc + repository thật, Catalog / Identity (token SERVICE) / Booking
(busy-caregivers) giả trên JDK HttpServer. Nếu chạy app bằng nút Run của IntelliJ, cần khai báo toàn bộ biến trong `.env` vào Run Configuration; cách
đơn giản nhất khi dev là dùng `.\scripts\Start-Dev.ps1`.
