# CareConnect MediaService

MediaService sở hữu metadata file và duy nhất service này giữ credentials Cloudflare R2. Trong phạm vi hiện tại,
service nghiệp vụ nhận multipart từ client rồi gọi MediaService qua OpenFeign. MediaService stream file lên R2,
lưu metadata PostgreSQL và trả `mediaFileId`.

## 1. Thông số

- API: `http://localhost:8085`
- PostgreSQL: `localhost:5445`
- Database: `CARECONNECT_MEDIA`
- Swagger: `http://localhost:8085/swagger-ui.html`
- Health: `http://localhost:8085/actuator/health`
- API nghiệp vụ: `/internal/media/**`, yêu cầu `X-Internal-Secret`
- Không route `/internal/**` qua ApiGateway.

## 2. Chuẩn bị `.env`

```powershell
Copy-Item .env.example .env
```

Điền endpoint, bucket, Access Key ID và Secret Access Key đã tạo trong Cloudflare R2. Endpoint phải có dạng:

```text
https://<ACCOUNT_ID>.r2.cloudflarestorage.com
```

Tự sinh internal secret bằng PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Dán kết quả vào:

```dotenv
MEDIA_INTERNAL_SECRET=<chuỗi-vừa-sinh>
```

Không commit `.env`.

## 3. Tạo database

```powershell
docker compose --env-file .env up -d
docker compose ps
```

Chỉ với database mới, áp dụng schema:

```powershell
Get-Content -Raw schema/media.sql |
    docker compose --env-file .env exec -T postgres psql `
        -U postgres `
        -d CARECONNECT_MEDIA `
        -v ON_ERROR_STOP=1
```

Lệnh `docker compose up -d` tạo/chạy container. Lệnh `Get-Content ... | psql` chỉ tạo bảng trong database.

## 4. Chạy service

```powershell
.\scripts\Start-Dev.ps1
```

Kiểm tra:

```text
GET http://localhost:8085/actuator/health
```

Kết quả mong đợi:

```json
{"status":"UP"}
```

## 5. Test Postman thủ công

Import `postman/CareConnect-MediaService.postman_collection.json`, sau đó đặt collection variables:

- `baseUrl`: `http://localhost:8085`
- `internalSecret`: giá trị `MEDIA_INTERNAL_SECRET`
- `uploadedBy`: UUID user bất kỳ lấy từ IdentityService

### Upload

```http
POST {{baseUrl}}/internal/media
X-Internal-Secret: {{internalSecret}}
Content-Type: multipart/form-data
```

Body `form-data`:

| Key | Type | Ví dụ |
|---|---|---|
| `file` | File | Chọn PNG/JPEG/PDF thật |
| `purpose` | Text | `CAREGIVER_CERTIFICATE` |
| `uploadedBy` | Text | UUID user |
| `sourceService` | Text | `PROVIDER_SERVICE` |
| `referenceType` | Text | `CAREGIVER_CERTIFICATE` |
| `referenceId` | Text | UUID certificate; có thể bỏ nếu chưa tạo |

Postman không cần tự đặt `Content-Type`; Postman sẽ thêm multipart boundary.

Response thành công trả `result.mediaFileId`. Collection tự lưu nó vào biến `mediaFileId`.

### Xem metadata

```http
GET {{baseUrl}}/internal/media/{{mediaFileId}}
X-Internal-Secret: {{internalSecret}}
```

### Lấy URL tải private

```http
GET {{baseUrl}}/internal/media/{{mediaFileId}}/download-url
X-Internal-Secret: {{internalSecret}}
```

Mở `result.downloadUrl` trong trình duyệt trước khi hết hạn.

### Xóa

```http
DELETE {{baseUrl}}/internal/media/{{mediaFileId}}
X-Internal-Secret: {{internalSecret}}
```

Sau đó gọi lại endpoint metadata phải nhận `404 MEDIA_NOT_FOUND`.

### Các case lỗi cần test

1. Bỏ header internal secret → `401`.
2. Gửi secret sai → `401`.
3. Chọn file TXT nhưng khai `image/png` → `5103 FILE_CONTENT_MISMATCH`.
4. Upload PDF với purpose `CAREGIVER_AVATAR` → `5102 INVALID_FILE_TYPE`.
5. Upload ảnh trên 5 MB với purpose avatar → `5101 FILE_TOO_LARGE`.
6. Gọi UUID không tồn tại → `5200 MEDIA_NOT_FOUND`.

## 6. Kiểm tra database và R2

```powershell
docker compose --env-file .env exec -T postgres psql `
    -U postgres `
    -d CARECONNECT_MEDIA `
    -c "SELECT media_file_id, object_key, media_purpose, media_status, file_size FROM media_files ORDER BY created_at DESC;"
```

Trong Cloudflare Dashboard → R2 → bucket, object key có dạng:

```text
caregiver-certificate/2026/10/<uuid>.pdf
```

## 7. Chạy test tự động

Test tự động mock R2, không sử dụng credentials và không upload file thật:

```powershell
.\mvnw.cmd test
```

## 8. Tích hợp ProviderService

ProviderService cần hai biến riêng:

```dotenv
PROVIDER_MEDIA_URL=http://localhost:8085
PROVIDER_MEDIA_INTERNAL_SECRET=<phải bằng MEDIA_INTERNAL_SECRET>
```

Feign client:

```java
@FeignClient(
        name = "media",
        url = "${provider.media.url}",
        configuration = MediaClientConfig.class)
public interface MediaClient {

    @PostMapping(value = "/internal/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    MediaApiResponse<MediaResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("purpose") String purpose,
            @RequestParam("uploadedBy") UUID uploadedBy,
            @RequestParam("sourceService") String sourceService,
            @RequestParam(value = "referenceType", required = false) String referenceType,
            @RequestParam(value = "referenceId", required = false) UUID referenceId);

    @DeleteMapping("/internal/media/{mediaFileId}")
    MediaApiResponse<Void> delete(@PathVariable("mediaFileId") UUID mediaFileId);
}
```

`MediaClientConfig` gắn secret tự động:

```java
public class MediaClientConfig {

    @Bean
    public RequestInterceptor mediaRequestInterceptor(ProviderProperties providerProperties) {
        String secret = providerProperties.getMedia().getInternalSecret();
        return template -> template.header("X-Internal-Secret", secret);
    }
}
```

Code nghiệp vụ ProviderService vẫn dùng giống service method cũ:

```java
MediaResponse media = mediaClient.upload(
        file,
        MediaPurpose.CAREGIVER_CERTIFICATE.name(),
        actorId,
        "PROVIDER_SERVICE",
        "CAREGIVER_CERTIFICATE",
        certificateId
).getResult();

certificate.setMediaFileId(media.getMediaFileId());
```

`media_file_id` trong Provider DB chỉ là UUID tham chiếu chéo; không tạo foreign key sang Media DB.
