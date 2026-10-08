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
8. Caregiver xem lịch, chuyển `SCHEDULED → IN_PROGRESS → COMPLETED`; caregiver/trung tâm có thể hủy với lý do.

ProviderService không lưu mật khẩu hay phát JWT. IdentityService vẫn xác thực người dùng; ProviderService chỉ xác minh JWT và kiểm tra role/quyền sở hữu tài nguyên.
Khi gán manager hoặc tạo caregiver profile, ProviderService gọi API nội bộ IdentityService để kiểm tra tài khoản `ACTIVE` và đúng role trước khi mở transaction ghi dữ liệu.

## Khởi tạo local

```powershell
Copy-Item .env.example .env
# Điền password, JWT issuer/JWKS và internal secret trong .env; không đặt dấu nháy quanh JDBC URL.

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
- `CUSTOMER`: xem catalog caregiver đã xác minh và ca được phép đọc.

## API chính

### Trung tâm

- `POST /admin/centers`
- `GET /admin/centers`
- `GET /admin/centers/{id}`
- `PUT /admin/centers/{id}`
- `PATCH /admin/centers/{id}/status`
- `DELETE /admin/centers/{id}?version=`

### Hồ sơ và chứng chỉ

- `POST /centers/{centerId}/caregivers`
- `GET /centers/{centerId}/caregivers`
- `GET|PUT|DELETE /caregivers/{caregiverId}`
- `GET /caregiver/profile`
- `POST|GET /caregivers/{caregiverId}/certificates`
- `PUT|DELETE /certificates/{id}`
- `PATCH /admin/caregivers/{id}/verification`
- `PATCH /admin/certificates/{id}/verification`
- `GET /catalog/caregivers`
- `GET /catalog/caregivers/{caregiverId}/certificates`

### Ca, đăng ký và lịch

- `POST /centers/{centerId}/shifts`
- `GET /shifts` và `GET /shifts/{id}`
- `PUT|DELETE /shifts/{id}`
- `POST /caregiver/shifts/{shiftId}/registrations`
- `GET /caregiver/registrations`
- `DELETE /caregiver/registrations/{id}?version=`
- `GET /shifts/{id}/registrations`
- `PATCH /registrations/{id}/review`
- `GET /caregiver/schedules`
- `GET /centers/{centerId}/schedules`
- `PATCH /caregiver/schedules/{id}/status`
- `PATCH /schedules/{id}/status`

Các request cập nhật dùng `version` để chống ghi đè dữ liệu đồng thời.

## Test

```powershell
.\mvnw.cmd clean test
```

Test dùng H2 PostgreSQL compatibility mode và không cần chạy Docker. Nếu chạy app bằng nút Run của IntelliJ, cần khai báo toàn bộ biến trong `.env` vào Run Configuration; cách đơn giản nhất khi dev là dùng `.\scripts\Start-Dev.ps1`.
