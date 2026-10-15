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
- `client/` gọi API nội bộ IdentityService trước transaction ghi để xác minh tài khoản `ACTIVE` và đúng role.
- Duyệt shift registration phải khóa dữ liệu, kiểm tra capacity/trùng lịch và tạo caregiver schedule trong cùng transaction.
- API nội bộ chỉ đọc cho BookingService (`/internal/caregivers/**`, `/internal/centers/**`, `X-Internal-Secret`):
  `InternalCaregiverController`, `InternalCenterController`, `InternalProviderService` + `Impl`, `InternalProviderMapper`.
  `careServiceIds = null` nghĩa là chưa quản lý kỹ năng — không đổi thành `[]` khi chưa có bảng `caregiver_services`.
  Provider không đánh dấu slot đã đặt (Booking là nguồn sự thật về giờ đã đặt).
