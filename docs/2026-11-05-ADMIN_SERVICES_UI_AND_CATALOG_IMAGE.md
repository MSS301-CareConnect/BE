# 2026-11-05 — Giao diện tab "Dịch vụ" (admin) và tải ảnh minh họa dịch vụ chuẩn

Hai phần việc trong cùng một phiên: chỉnh giao diện `/admin/services`, rồi thêm tải ảnh minh họa từ máy (trước đó chỉ dán
được link). Code đã viết xong, test đã xanh, SQL đã áp vào DB dev, container đã build lại và đã thử thật trên trình duyệt.
**Chưa commit.**

## 1. Giao diện `/admin/services` (FE)

Sửa theo ảnh chụp của chủ dự án:

| Lỗi | Nguyên nhân / cách sửa | File |
|---|---|---|
| Mũi tên ▼ của select (Danh mục, Trạng thái) rớt xuống dòng dưới, ô cao bất thường | Class `block` của Tailwind đè `display: flex` của `appearance: base-select` trong `app.css`. Bỏ `block`, mọi ô cao `h-11`, `whitespace-nowrap`. **Không đặt `block` lên `<select>`.** | `catalog-item-dialog.tsx`, `catalog-filters.tsx` |
| Ô "Tất cả (Trạng thái)" xuống dòng; nút "Đặt lại bộ lọc" lệch hàng | Thanh lọc chuyển sang lưới **container query** (`@container`, `@md` / `@xl` / `@4xl`): breakpoint theo viewport sai vì sidebar admin chiếm chỗ. Nhãn mặc định đổi thành "Tất cả danh mục" / "Mọi trạng thái" | `catalog-filters.tsx` |
| Badge danh mục xuống 2 dòng, tên dài làm hàng cao không đều | Bảng `table-fixed`; badge `whitespace-nowrap`; tên và mô tả cắt một dòng, `title` hiện đầy đủ | `catalog-table.tsx` |
| Cột "Mô tả" chiếm chỗ, chữ nhỏ | Gộp mô tả thành dòng phụ dưới tên dịch vụ; cột Danh mục chỉ hiện khi khung ≥ `@3xl` | `catalog-table.tsx` |
| Nút xem / sửa / xóa không rõ nghĩa | Tooltip hiện ngay ("Xem", "Sửa", "Xóa"); icon sửa đổi từ `tune` (giống chỉnh giá) sang `edit` | `catalog-table.tsx` |
| Hộp thoại 2 cột lệch | Cột trái: tên, rồi danh mục + trạng thái cạnh nhau, rồi mô tả giãn bằng chiều cao cột phải. Cột phải: xem trước thẻ, ảnh, ảnh gợi ý | `catalog-item-dialog.tsx`, `catalog-image-field.tsx` |
| Mobile | Hàng bảng thành thẻ chỉ bằng CSS (`@max-xl:`), không render trùng DOM (test đếm `row`); 4 thẻ thống kê 2×2; hộp thoại trượt từ đáy | `catalog-table.tsx`, `admin-services-page.tsx`, `catalog-item-dialog.tsx` |

Sửa thêm ngoài phạm vi:

- Hộp thoại "Xem chi tiết dịch vụ" in UUID kèm nhãn viết cứng `Mã dịch vụ:` → bỏ (quy tắc FE: admin không hiển thị UUID; lint
  `no-hardcoded-ui-text` cũng báo lỗi).
- `aria-label` viết cứng (`Xóa tìm kiếm`, `Số lượng mỗi trang`, `Xóa ảnh`) → key i18n mới.
- Ảnh gợi ý: nhãn lấy từ `common.serviceCategories.*`; ảnh "Điều dưỡng" trước đây điền danh mục `Điều dưỡng` (không có trong
  6 danh mục) → nay điền đúng `Điều dưỡng y tế`.
- `table-pagination.tsx`: lỗi lint `prefer-const`.

Key i18n mới (chỉ thêm, không đổi key cũ) trong `app/shared/i18n/admin/services-resources.ts`: `allCategories`,
`allStatuses`, `clearSearch`, `actionLabels.*`, `pageSizeLabel`, `form.imageClear`, `form.imagePresetsShort`,
`form.detailsSection`, `form.mediaSection`, `form.imageUpload`, `form.imageReplace`, `form.imageUploading`,
`form.imageDrop`, `form.imageUploadHint`, `form.imageUrlOr`, `form.imageUploadErrors.*`.

## 2. Tải ảnh minh họa dịch vụ chuẩn

### Vì sao cần backend

MediaService `/api/v1/media` chỉ nhận `hasRole('SERVICE')`, URL tải là URL R2 có chữ ký và hết hạn, còn
`care_services.image_url` tối đa 1000 ký tự. Trình duyệt không tự tải lên được, và không được lưu URL có chữ ký.

### Luồng

```text
Admin chọn / kéo-thả ảnh (JPG / PNG / WebP ≤ 5 MB, kiểm ở trình duyệt trước)
  → POST /api/v1/catalog-items/images (ADMIN, multipart "file", CSRF)
      → CareCatalogService kiểm lại loại / dung lượng
      → MediaService POST /api/v1/media (token SERVICE client "catalog", purpose CATALOG_ITEM_IMAGE)
  ← { mediaFileId, imageUrl: "/api/v1/catalog-items/images/{mediaFileId}", contentType, fileSize }
FE đặt imageUrl vào form → lưu bằng POST / PUT /catalog-items như cũ

<img src="/api/v1/catalog-items/images/{id}"> (public, không đọc token)
  → CareCatalogService hỏi MediaService download-url → 302 Location = URL R2 có hạn
     Cache-Control: public, max-age = (hạn URL − 60 s), tối đa 1 giờ
```

MediaService chỉ trả file do chính client `catalog` tải lên (`source_service`), nên endpoint public không đọc được file của
service khác (403 → 404 `3114`).

### Backend

| Service | Thay đổi | File chính |
|---|---|---|
| MediaService | Purpose mới `CATALOG_ITEM_IMAGE` (5 MB, jpeg / png / webp); CHECK `ck_media_files_purpose` | `enums/MediaPurpose.java`, `schema/media.sql`, `schema/2026-11-05-catalog-item-image.sql` |
| CareCatalogService | Feign client đầu tiên của service: `client/MediaClient` (+ `MediaClientConfig`, `MediaClientFallbackFactory`, `ServiceTokenProvider`, `ServiceTokenInterceptor`, `dto/*`), chép mẫu BookingService; `@EnableFeignClients`; bean `Clock`, `serviceTokenRestClient` | `client/**`, `CareCatalogServiceApplication`, `config/CareCatalogConfig`, `config/CareCatalogProperties` |
| CareCatalogService | `POST /catalog-items/images` (ADMIN) và `GET /catalog-items/images/{mediaFileId}` (public, 302) trong `CatalogItemController` / `CatalogItemService` (cùng aggregate); response qua `CatalogItemMapper.toImageResponse`; `ServiceTokenMapper` | `controller/CatalogItemController`, `service/Impl/CatalogItemServiceImpl`, `mapper/*`, `dto/response/CatalogImageResponse` |
| CareCatalogService | Path ảnh vào `PUBLIC_GET_ENDPOINTS` của `SecurityConfig` (không đọc token); multipart `max-file-size: 6MB`; Feign `media` timeout 2 s / 10 s | `security/SecurityConfig`, `application.yaml` |
| CareCatalogService | Mã lỗi: `3007 MEDIA_SERVICE_UNAVAILABLE`, `3008 SERVICE_TOKEN_UNAVAILABLE` (503), `3111 CATALOG_IMAGE_REQUIRED`, `3112 CATALOG_IMAGE_INVALID`, `3113 CATALOG_IMAGE_TOO_LARGE` (400), `3114 CATALOG_IMAGE_NOT_FOUND` (404). `GlobalExceptionHandler` map `MaxUploadSizeExceededException` → 3113, `MissingServletRequestPartException` → 3111 | `exception/*` |
| ApiGateway | `/api/v1/catalog-items/images/{mediaFileId}` (và alias `care-services`) vào `PUBLIC_GET_ENDPOINTS` — chỉ GET | `config/GatewayPaths.java` |
| Tài liệu | Ngoại lệ thứ ba của rule #3 (endpoint trả 302, không `APIResponse`) ghi ở `BE/AGENTS.md`; `CareCatalogService/AGENTS.md` bỏ câu "không gọi service nào"; API ở `CareCatalogService/README.md` | |

### Frontend

- `openapi/openapi.yaml`: thao tác `uploadCatalogItemImage`, schema `CatalogImageUploadRequest` / `CatalogImageRecord` /
  `CatalogImageResponse`; **thêm `imageUrl` vào `CatalogItemRecord` và `CatalogItemRequest`** — trước đó trường này chỉ có
  trong code generated (đã bị sửa tay), chạy lại `pnpm api:generate` sẽ làm mất nó. `GET` ảnh không có trong snapshot (302,
  không có body JSON).
- `orval.config.ts`: override Faker `CatalogImageUploadRequest.file` → `Blob` (nếu không, faker sinh `ArrayBuffer` và tsc lỗi).
- `entities/care-catalog/care-catalog-api.ts`: `uploadCatalogImage(file)`.
- `features/admin/services/hooks/use-catalog-image-upload.ts`: kiểm loại / dung lượng trước, map 3111–3113 / 401 / 403 sang
  thông báo dưới ô ảnh.
- `features/admin/services/components/catalog-image-field.tsx`: nút "Tải ảnh từ máy" / "Đổi ảnh khác", kéo-thả lên khung xem
  trước, ô dán link, ảnh gợi ý.
- `docs/ARCHITECTURE.md`: đoạn `/admin/services` ghi `imageUrl` và luồng tải ảnh.

## 3. Cấu hình và thao tác trên môi trường dev (đã làm)

| Việc | Chi tiết |
|---|---|
| Service client `catalog` | Sinh bằng `IdentityService/scripts/New-ServiceClient.ps1 catalog`. Hash (dạng Compose, `$$`) nối vào `IDENTITY_SERVICE_CLIENTS` trong `IdentityService/.env`; secret thô ở `CARE_CATALOG_SERVICE_CLIENT_SECRET` trong `CareCatalogService/.env` (cả hai file bị gitignore, secret không in ra log / chat) |
| Biến môi trường mới của Catalog | `CARE_CATALOG_IDENTITY_URL`, `CARE_CATALOG_MEDIA_URL` (dev `http://localhost:8080`; Compose ghi đè `http://api-gateway:8080` trong `compose.stack.yaml`), `CARE_CATALOG_SERVICE_CLIENT_ID=catalog`, `CARE_CATALOG_SERVICE_CLIENT_SECRET`, tùy chọn `CARE_CATALOG_MEDIA_*_TIMEOUT_MS`, `CARE_CATALOG_SERVICE_TOKEN_*_TIMEOUT`. Đã thêm vào `.env.example` |
| SQL MediaService | Đã áp `MediaService/schema/2026-11-05-catalog-item-image.sql` vào `careconnect-media-postgres-1` |
| SQL CareCatalogService | Đã áp `CareCatalogService/schema/changes/2026-11-05_catalog_item_image_url.sql` (cột `image_url`, của một phiên khác, trước đó chưa áp) — xem mục 5 |
| Build lại container | `docker compose up -d --build media-service care-catalog-service api-gateway identity-service`; cả 4 healthy |

Máy khác muốn chạy được tính năng này: sinh client `catalog` như trên (hoặc chép secret / hash), áp 2 file SQL, build lại 4
service.

## 4. Kiểm thử

- CareCatalogService `mvnw test`: 28 chạy, 0 lỗi, 1 skip — có `CatalogImageApiTests` mới (5 test: ADMIN tải lên được và nhận
  đường dẫn cố định; 401 / 403 với người khác; thiếu file / sai loại / > 5 MB bị chặn trước khi gọi Media; redirect public có
  `Cache-Control` ngắn hơn hạn URL; id lạ → 404 `3114`).
- ApiGateway: thêm `catalogImageIsAnonymousOnlyForGet` (GET public kể cả cookie hỏng; POST và các path khác vẫn 401). Pass.
- MediaService `mvnw test`: pass.
- FE: `pnpm typecheck`, `pnpm lint` xanh; `pnpm vitest run app/features/admin app/entities/care-catalog app/features/customer`
  40 file / 226 test pass (2 test mới: tải ảnh rồi lưu `imageUrl`; file PDF bị chặn ở trình duyệt, lỗi 3113 hiện thông báo).
- Trình duyệt (admin `localhost:5173`, backend thật): giao diện ở 1024 / 1600 / 375 px; tải một ảnh PNG thật → ô link nhận
  `/api/v1/catalog-items/images/…`, khung xem trước hiện ảnh. Qua gateway: khách chưa đăng nhập → 302 tới R2,
  `Cache-Control: max-age=239, public`; id không tồn tại → 404 `3114`; `POST` không đăng nhập → 401.

## 5. Còn lại / lưu ý

- **Chưa commit.** Chủ dự án tự commit.
- **Build lại container kéo theo code chưa commit của phiên khác.** Entity `CatalogItem.imageUrl` (phiên khác) làm Catalog
  không khởi động vì DB thiếu cột → đã áp script `2026-11-05_catalog_item_image_url.sql` (chỉ thêm cột nullable). Lần sau
  build lại service nào thì kiểm `schema/changes/` của service đó trước.
- **Ảnh mồ côi:** ảnh tải lên rồi bỏ (đóng hộp thoại, đổi ảnh khác) vẫn nằm trên R2 / MediaService. Lúc thử đã để lại một ảnh
  PNG test như vậy (không gắn dịch vụ nào). Chưa có dọn dẹp — nếu cần, làm sau (vd Catalog xóa media cũ khi `imageUrl` đổi).
- Link ngoài (Unsplash, dán tay) vẫn được chấp nhận như trước; chỉ ảnh tải lên mới đi qua Catalog.
- Hai file trong `app/features/admin/services` lệch Prettier từ trước, không thuộc thay đổi này nên chưa format:
  `components/delete-catalog-item-dialog.tsx`, `hooks/use-catalog-list.ts`.
- `pnpm typecheck` in cảnh báo Node 22.19 < 22.22 nhưng vẫn thoát 0.
