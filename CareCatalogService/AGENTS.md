# AGENTS.md — CareCatalogService

> Chỉ ghi phần **khác / bổ sung** so với `../AGENTS.md`. Đọc file gốc trước.

## Thông số

| Field | Value |
|---|---|
| Thư mục / artifact | `CareConnect/CareCatalogService/` · `mss301:CareCatalogService:0.0.1-SNAPSHOT` |
| Base package | `mss301.carecatalogservice` |
| Port / env prefix | `8083` / `CARE_CATALOG_` |
| Dải `ErrorCode` | **3000–3999** (+ 401/403/404/9999) — 30xx chung, 31xx danh mục chuẩn, 32xx dịch vụ trung tâm, 33xx bảng giá. Mã mới theo kế hoạch: 3110–3149, 3206–3249, 3305–3349 |
| DB | PostgreSQL riêng `127.0.0.1:5443`, DB `CARECONNECT_CATALOG`, `schema/catalog.sql` + `schema/changes/` (áp tay, không Flyway). Không Redis |

## Aggregate

| Entity (bảng) | Service / Controller | Path |
|---|---|---|
| `CatalogItem` (`care_services`) — danh mục chuẩn, ADMIN quản lý | `CatalogItemService` / `CatalogItemController` | `/api/v1/catalog-items`, alias `/api/v1/care-services` |
| `CenterOffering` (`center_services`) — dịch vụ trung tâm, CENTER_MANAGER quản lý | `CenterOfferingService` / `CenterOfferingController` | `/api/v1/center-services` (kể cả `POST /quote`) |
| `PricePlan` (`price_plans`) — gói giá HOUR / DAY / SHIFT | `PricePlanService` / `PricePlanController` | `/api/v1/price-plans` |

`CatalogQuoteService` là logic báo giá dùng chung, được `CenterOfferingService.quote` gọi (không có controller riêng).
Service hiện **không gọi service nào** (không Feign client); Booking / Provider gọi vào qua gateway.

## Quy tắc nghiệp vụ (bắt buộc giữ)

1. **Báo giá xác định** (CAT-01, D12): mỗi item phải có `pricePlanId`; không bao giờ tự chọn gói "đầu tiên". Gói phải
   active và thuộc đúng dịch vụ trung tâm; dịch vụ trung tâm + danh mục chuẩn phải ACTIVE. Cùng input → cùng kết quả.
2. **HOUR = 60 phút**: đơn giá theo giờ luôn trên 60 phút (Booking nhân với số giờ của buổi). Kiểm ở service
   (`INVALID_DURATION`) **và** CHECK `ck_price_plans_hour_duration` trong SQL.
3. `isActive` của gói giá do service quyết định (mapper ignore): tạo không gửi → `true`; sửa không gửi → giữ giá trị cũ.
4. Dịch vụ trung tâm ACTIVE chỉ dùng danh mục chuẩn ACTIVE (`SERVICE_NOT_ACTIVE`); tắt dịch vụ thì luôn được.
5. Một trung tâm đăng ký một dịch vụ chuẩn một lần: kiểm ở service + unique index `uk_center_services_center_care`
   (race → `GlobalExceptionHandler` map thành `CENTER_SERVICE_ALREADY_EXISTS`).
6. Phạm vi manager: `center_id` lấy từ token (`@CurrentCenterId`), thao tác dịch vụ / gói giá của trung tâm khác → 403
   `CENTER_ACCESS_DENIED`.
7. **Vô hiệu hóa an toàn** (CAT-02): tắt dịch vụ chuẩn không sửa offering trong DB — mọi truy vấn cho khách và báo giá
   lọc khi đọc; booking đã tạo giữ giá snapshot. Người xem không quản lý trung tâm chỉ thấy dịch vụ đang bán được
   (`CenterOfferingRepository.findSellableByCenter`). Role người gọi qua `@CurrentRoles` (copy mẫu BookingService).

## Kiểm thử

`.\mvnw.cmd test` — unit test service + mapper thật (`CatalogQuoteServiceTests`, `CenterOfferingAndPricePlanTests`).
`CatalogApiTests` (CAT-02): Spring context trên H2, token giả bằng `jwt()` của spring-security-test — 401 / 403 / phạm vi
đọc theo role / xóa có điều kiện / price-summary.
