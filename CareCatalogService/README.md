# CareConnect CareCatalogService

Java 21 / Spring Boot 4.1.1 / Spring Cloud 2025.1.3. Port mặc định **8083**. Service sở hữu danh mục dịch vụ chuẩn, dịch vụ do trung tâm chăm sóc cung cấp và bảng giá (theo giờ / ngày / ca). Không sở hữu tài khoản (IdentityService), hồ sơ người bệnh (CareRecipientService), caregiver hay booking.

Quy ước chung: `../AGENTS.md`; phần riêng của service: `AGENTS.md`.

## Chức năng

- **Danh mục dịch vụ chuẩn (CareService):**
  - Quản trị viên (`ADMIN`) tạo, sửa, xóa mềm các dịch vụ chuẩn toàn hệ thống (ví dụ: Chăm sóc người cao tuổi, Vật lý trị liệu tại nhà...).
  - Quản lý trạng thái (`ACTIVE`, `INACTIVE`), phân loại nhóm (`category`).
- **Dịch vụ trung tâm (CenterService):**
  - Quản lý trung tâm (`CENTER_MANAGER`) đăng ký cung cấp dịch vụ từ danh mục chuẩn.
  - Mỗi trung tâm chỉ liên kết một dịch vụ chuẩn một lần.
  - Phân quyền nghiêm ngặt theo phạm vi: Manager chỉ được thêm/sửa/xóa dịch vụ thuộc đúng trung tâm của mình (`center_id` từ JWT token).
- **Bảng giá dịch vụ (PricePlan):**
  - Trung tâm cấu hình các gói giá linh hoạt theo đơn vị tính (`pricingUnit`: `HOUR`, `DAY`, `SHIFT`).
  - Định nghĩa thời lượng (`durationMinutes`) và giá gốc (`basePrice`).
  - Tự động kiểm tra quyền sở hữu trung tâm trước khi thao tác bảng giá.
- **Duyệt danh mục & giá:**
  - Cung cấp API xem chi tiết và danh sách dịch vụ theo trung tâm phục vụ đặt lịch và tìm kiếm.

## Chạy local

Trong thư mục `CareConnect/CareCatalogService`, yêu cầu Java 21, Docker:

```powershell
# 1. Tạo file cấu hình từ template
Copy-Item .env.example .env
# Sửa .env nếu cần (DB password, issuer / JWKS của IdentityService)

# 2. Khởi động PostgreSQL riêng của service (Port 5443)
docker compose up -d

# 3. Áp dụng schema SQL cho DB mới (chỉ chạy một lần đầu)
Get-Content -Raw schema/catalog.sql | docker compose exec -T care-catalog-db psql -U postgres -d CARECONNECT_CATALOG -v ON_ERROR_STOP=1

# 4. Khởi chạy service
.\mvnw.cmd spring-boot:run
```

- PostgreSQL riêng `127.0.0.1:5443` (DB `CARECONNECT_CATALOG`). Không Redis, không Flyway; `ddl-auto: validate`.
- Schema khởi tạo tại `schema/catalog.sql`; DB đã có dữ liệu áp change script trong `schema/changes/` (vd
  `2026-10-22_price_plan_hour_duration.sql` — CAT-01).
- JVM chạy múi giờ UTC, thời gian lưu kiểu `TIMESTAMPTZ`.

## API Endpoints

### 1. Danh mục dịch vụ chuẩn (`/care-services`)
- `POST /care-services`: Tạo dịch vụ chuẩn mới (*Role: ADMIN*)
- `PUT /care-services/{id}`: Cập nhật dịch vụ chuẩn (*Role: ADMIN*)
- `GET /care-services`: Lấy danh sách dịch vụ chuẩn đang hoạt động
- `GET /care-services/{id}`: Lấy chi tiết dịch vụ chuẩn
- `DELETE /care-services/{id}`: Xóa mềm dịch vụ chuẩn (*Role: ADMIN*)

### 2. Dịch vụ trung tâm (`/center-services`)
- `POST /center-services`: Đăng ký cung cấp dịch vụ cho trung tâm (*Role: CENTER_MANAGER*)
- `PUT /center-services/{id}`: Cập nhật thông tin dịch vụ trung tâm (*Role: CENTER_MANAGER*)
- `GET /center-services/center/{centerId}`: Lấy danh sách dịch vụ của một trung tâm
- `GET /center-services/{id}`: Lấy chi tiết dịch vụ trung tâm
- `DELETE /center-services/{id}`: Xóa mềm dịch vụ trung tâm (*Role: CENTER_MANAGER*)

- `POST /api/v1/center-services/quote` (authenticated): báo giá **xác định** — body `{centerId, items[{careServiceId,
  centerServiceId?, pricePlanId}]}`, **`pricePlanId` bắt buộc** (thiếu → 400 `3305`, Catalog không tự chọn gói). Gói phải
  active (`3306`), thuộc đúng dịch vụ trung tâm; dịch vụ trung tâm và danh mục chuẩn phải ACTIVE (`3105`). Trả
  `[{careServiceId, centerServiceId, pricePlanId, serviceName, pricingUnit, durationMinutes, basePrice, active}]`.
- Tạo / sửa dịch vụ trung tâm (ACTIVE) yêu cầu danh mục chuẩn ACTIVE (409 `3105`); vẫn tắt được dịch vụ có danh mục đã bị
  vô hiệu hóa. Hai request song song cùng đăng ký một dịch vụ → 409 `3205` (unique index `uk_center_services_center_care`).

- CAT-02 (2026-10-22): `GET /api/v1/catalog-items?status=&category=&keyword=&page=&size=` và
  `GET /api/v1/center-services/center/{centerId}?status=&page=&size=` trả **trang** (`PageResponse`, `size ≤ 100`).
  Khách / caregiver chỉ thấy dịch vụ chuẩn ACTIVE và dịch vụ trung tâm đang bán được (offering + danh mục ACTIVE, có gói
  active, kèm `pricePlans[]` active); ADMIN và manager của chính trung tâm thấy mọi trạng thái. `PATCH /catalog-items/{id}/status`
  (ADMIN), `PATCH /center-services/{id}/status` (manager). Xóa chỉ khi không còn dùng (409 `3110` / `3206`, dùng PATCH
  status); xóa dịch vụ trung tâm xóa mềm luôn gói con. `GET /api/v1/center-services/price-summary?careServiceId=&minPrice=&maxPrice=`
  (SERVICE, C9.2): `[{centerId, minPrice}]` của gói đang bán trong khoảng giá.

### 3. Bảng giá (`/price-plans`)
- `POST /price-plans`: Tạo bảng giá mới cho dịch vụ trung tâm (*Role: CENTER_MANAGER*)
- `PUT /price-plans/{id}`: Cập nhật bảng giá (*Role: CENTER_MANAGER*)
- `GET /price-plans/center-service/{centerServiceId}`: Lấy các bảng giá của một dịch vụ trung tâm
- `GET /price-plans/{id}`: Lấy chi tiết bảng giá
- `DELETE /price-plans/{id}`: Xóa mềm bảng giá (*Role: CENTER_MANAGER*)
- `isActive` không gửi → `true` khi tạo, giữ nguyên khi sửa. `pricingUnit = HOUR` bắt buộc `durationMinutes = 60`
  (đơn giá theo giờ; 400 `3303`, CHECK `ck_price_plans_hour_duration`); DAY / SHIFT là thời lượng chuẩn của đơn vị.

## Bảng mã lỗi (ErrorCode: 3000–3999)

| Code | Key | Ý nghĩa |
|---|---|---|
| `3000` | `INVALID_REQUEST` | Yêu cầu không hợp lệ / JSON sai định dạng |
| `3002` | `CSRF_INVALID` | CSRF token thiếu hoặc sai |
| `3100` | `CARE_SERVICE_NOT_FOUND` | Không tìm thấy dịch vụ chuẩn |
| `3101` | `CARE_SERVICE_NAME_REQUIRED` | Tên dịch vụ chuẩn trống |
| `3102` | `CARE_SERVICE_CATEGORY_REQUIRED` | Danh mục trống |
| `3103` | `CARE_SERVICE_STATUS_REQUIRED` | Trạng thái trống |
| `3200` | `CENTER_SERVICE_NOT_FOUND` | Không tìm thấy dịch vụ trung tâm |
| `3204` | `CENTER_ACCESS_DENIED` | Không có quyền thao tác trên dịch vụ của trung tâm khác |
| `3205` | `CENTER_SERVICE_ALREADY_EXISTS` | Dịch vụ chuẩn đã được trung tâm đăng ký trước đó |
| `3300` | `PRICE_PLAN_NOT_FOUND` | Không tìm thấy bảng giá |
| `3301` | `PRICE_PLAN_NAME_REQUIRED` | Tên bảng giá trống |
| `3302` | `PRICING_UNIT_REQUIRED` | Đơn vị tính giá trống (phải là `HOUR`, `DAY`, `SHIFT`) |
| `3303` | `INVALID_DURATION` | Thời lượng phút phải > 0 |
| `3304` | `INVALID_BASE_PRICE` | Giá cơ bản phải >= 0 |
