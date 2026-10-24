# AGENTS.md — ProviderService

> Chỉ ghi phần khác / bổ sung so với `../AGENTS.md`.

| Field | Value |
|---|---|
| Artifact | `mss301:ProviderService:0.0.1-SNAPSHOT` |
| Base package | `mss301.providerservice` |
| Port / env prefix | `8084` / `PROVIDER_` |
| Dải ErrorCode | `4000–4999` (+ 401/403/404/9999 dùng chung) |
| DB | PostgreSQL riêng `127.0.0.1:5444`, database `CARECONNECT_PROVIDER` |

## Phạm vi

- Trung tâm chăm sóc, hồ sơ chuyên môn caregiver, chứng chỉ, ca trung tâm công bố, đăng ký ca và lịch caregiver.
- Không sở hữu tài khoản/mật khẩu/role; `user_id`, `created_by`, `updated_by`, `verified_by` là UUID từ IdentityService,
  không có cross-service foreign key.
- Chỉ IdentityService phát token. ProviderService xác minh access JWT bằng JWKS và tự kiểm tra role/phạm vi tài nguyên.
- Admin CRUD trung tâm. Tạo mới luôn `PENDING`; `REJECTED`/`SUSPENDED` bắt buộc có lý do; xóa mềm thành `ARCHIVED`.
- `care_centers.manager_user_id` ánh xạ tài khoản `CENTER_MANAGER`; manager chỉ thao tác đúng center của mình.
- `client/` gọi service khác qua ApiGateway, **trước** transaction ghi (rule #24):
  - `IdentityClient` + `ServiceTokenInterceptor` (token SERVICE) — `IdentityAccountService.requireActiveRole(userId,
    RoleCode)` kiểm tài khoản `ACTIVE` + đúng role khi gán manager / tạo caregiver.
  - `CareCatalogClient` + `ServiceTokenInterceptor` (token SERVICE) — kiểm offering khi gán kỹ năng caregiver;
    `getPriceSummary` (C9.2) cho danh sách trung tâm công khai (SRCH-02).
  - `BookingClient` + `ServiceTokenInterceptor` (token SERVICE) — C5.1 busy-caregivers; luôn gọi qua
    `BookingAvailabilityService.findBusyCaregiverIds` (chia lô ≤ 200 id × ≤ 60 khung, lỗi → 503 4702, không coi là rảnh).
  - `MediaClient` (token SERVICE) — `ProviderMediaService.findImageUrls` lấy URL ảnh công khai best-effort.
  - `ServiceTokenProvider`: client-credentials `POST /api/v1/auth/service-token` (`provider.service-client.*`,
    env `PROVIDER_SERVICE_CLIENT_ID/SECRET`), cache tới `exp − 60s`, lỗi → 503 `SERVICE_TOKEN_UNAVAILABLE` 4700.
  - Không còn `/internal/**`, `X-Internal-Secret`, `Internal*Controller`, `InternalProviderMapper`. BookingService đọc
    `GET /api/v1/caregivers/{id}` (chỉ ADMIN / manager trung tâm / chính caregiver / SERVICE),
    `/caregivers/by-user/{userId}`, `/care-centers/{id}` bằng token SERVICE, và gọi `POST /caregivers/availability-checks`
    (C5.2, `hasRole('SERVICE')`).
- Mapper theo aggregate (`@Mapper(config = MapStructConfig.class)`): `CareCenterMapper`, `CaregiverMapper` (gồm kỹ năng
  `CaregiverSkill`), `CertificateMapper`, `WorkShiftMapper`, `ShiftRegistrationMapper`, `CaregiverScheduleMapper`,
  `ProviderMediaMapper`, `ServiceTokenMapper`, `BookingClientMapper` (request C5.1).
- **Kỹ năng caregiver (PRV-01):** bảng `caregiver_services` = entity `CaregiverSkill` (+ `CaregiverSkillId`; đặt tên khác
  `CaregiverService` để không trùng interface service). Entity con của `CaregiverProfile` (`skills`, chỉ đọc, `@BatchSize`):
  chỉ có `CaregiverSkillRepository`, logic ở `CaregiverServiceImpl.listServices/replaceServices`. Lưu `care_service_id`
  (dịch vụ chuẩn) + snapshot `service_name`. `CaregiverResponse.careServiceIds/centerServiceIds` luôn là list (chưa gán
  → `[]`, Booking coi là không làm được dịch vụ nào).
- **Thời gian:** giờ ca là giờ Việt Nam — quy đổi qua `util/ProviderTime` (`Asia/Ho_Chi_Minh`), "bây giờ" lấy từ bean
  `Clock` (`ProviderConfig`). Không dùng `ZoneOffset.UTC` / `Instant.now()` trong logic ca.
- Duyệt shift registration khóa ca + đăng ký, kiểm trung tâm ACTIVE, ca chưa bắt đầu, capacity (đếm trước khi duyệt;
  đóng ca khi approved == capacity), trùng lịch và tạo caregiver schedule trong cùng transaction. Unique (ca, caregiver)
  là index một phần (PENDING/APPROVED; lịch khác CANCELLED) — không khai báo `@UniqueConstraint` trong entity.
- `GET /work-shifts` phân phạm vi theo role trong `WorkShiftServiceImpl.resolveListCenterId` (`ProviderAuthorization.hasRole`).
- `GET /work-shifts/{id}` dùng cùng phạm vi (`WorkShiftServiceImpl.checkReadScope`): ngoài phạm vi → 404.
- Provider không đánh dấu slot đã đặt (Booking là nguồn sự thật về giờ đã đặt).
- **Lịch rảnh / ngày nghỉ (PRV-02):** `caregiver_schedules` chỉ còn `SCHEDULED`/`CANCELLED` (D11). Ngày nghỉ = entity
  `CaregiverTimeOff` (con của `CaregiverProfile`, chỉ có `CaregiverTimeOffRepository`, logic ở `CaregiverServiceImpl`).
  Phép toán khoảng `[start, end)` ở `util/TimeWindow` + `util/TimeWindows` (gộp ca liền kề, trừ ngày nghỉ).
  Tạo ngày nghỉ / hủy lịch: kiểm phạm vi trong transaction ngắn → hỏi Booking NGOÀI transaction → ghi (khóa hồ sơ khi
  kiểm chồng ngày nghỉ).
- **Tìm kiếm (SRCH-01):** `CaregiverProfileRepository.searchCandidates` (giao tập dịch vụ bằng subquery
  `having count(distinct)`), giới hạn 500 (+1) ứng viên, lọc lịch + Booking, rồi sắp xếp / phân trang trong bộ nhớ.
  DTO công khai (`CaregiverCardResponse`, `CaregiverPublicResponse`, `CenterCardResponse`) không chứa field nội bộ.
- Dải ErrorCode đã dùng thêm (2026-10-22): 4119, 4207–4215 (kỹ năng / tìm kiếm), 4308, 4405–4415 (ngày nghỉ / lịch
  rảnh), 4700–4702 (tích hợp). Lệch spec có chủ đích: `CARE_SERVICE_IDS_REQUIRED` = **4212** (spec ghi 4210, đã thuộc
  `CAREGIVER_SERVICES_REQUIRED`), `BOOKING_SERVICE_UNAVAILABLE` = **4702** (spec ghi 4700, đã thuộc
  `SERVICE_TOKEN_UNAVAILABLE`).

## Aftercare (2026-10-24)

- Certificate dùng CertificateVerificationStatus riêng: PENDING → CENTER_VERIFIED → ADMIN_VERIFIED; từ chối cần lý do,
  quá hạn không duyệt. Nội dung sửa reset xác minh; public chỉ ADMIN_VERIFIED chưa hết hạn.
- Caregiver PUT không nhận status; PATCH riêng, ACTIVE yêu cầu VERIFIED, không lách ARCHIVED/SUSPENDED bằng verify.
- Vòng đời trung tâm theo PRV-04; manager PUT /care-centers/me theo whitelist. Archive kiểm Booking bằng token ADMIN
  chuyển tiếp trước transaction. Cascade khóa/mở qua provider_outbox sau commit bằng SERVICE, thứ tự theo version.
- Feign dùng Apache HC5 để hỗ trợ PATCH. Không bỏ dependency feign-hc5 khi còn center cascade.
- SQL nâng cấp và giới hạn vận hành: ../docs/2026-10-24-AFTERCARE_IMPLEMENTATION.md.
