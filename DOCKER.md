# Chạy toàn bộ CareConnect bằng Docker

Một Compose project (`careconnect`, file [compose.yaml](compose.yaml)) chạy 6 service Spring Boot và hạ tầng riêng của
từng service:

| Service (tên trên mạng Docker) | Port host | Ghi chú |
|---|---|---|
| `api-gateway` | 8080 | Swagger tổng hợp `/swagger-ui.html`; route Identity, CareRecipient, Booking |
| `identity-service` | 8081 | JWKS `/.well-known/jwks.json` |
| `care-recipient-service` | 8082 | |
| `care-catalog-service` | 8083 | gateway chưa route — gọi trực tiếp |
| `provider-service` | 8084 | gateway chưa route — gọi trực tiếp |
| `booking-service` | 8085 | |
| `identity-postgres` / `identity-redis` | 5441 / 6381 | |
| `care-recipient-postgres` | 5442 | |
| `care-catalog-postgres` | 5443 | |
| `provider-postgres` | 5444 | |
| `booking-postgres` | 5445 | |

Port host lấy từ `.env` của service (`*_SERVER_PORT`, `*_DB_PORT`, `IDENTITY_REDIS_PORT`), mặc định như bảng, chỉ
bind `127.0.0.1`. Trong container, service gọi nhau bằng tên service (`http://identity-service:8081`,
`http://booking-service:8085`…) — các URL `localhost` trong `.env` được ghi đè trong `<Service>/compose.stack.yaml`.

## Cấu trúc

- `compose.yaml` — chỉ `include` 6 file `<Service>/compose.stack.yaml`. Compose đọc biến `${...}` của mỗi file từ
  `<Service>/.env` cạnh nó, nên **vẫn dùng `.env` riêng từng service như trước**, không có file secret chung.
- `<Service>/Dockerfile` — build JAR bằng Maven Wrapper của service rồi chạy trên JRE 21 (user không phải root).
  `.dockerignore` chỉ gửi `.mvn/`, `mvnw`, `pom.xml`, `src/` — `.env` và `keys/` không bao giờ vào image.
- `<Service>/docker-compose.yml` cũ **giữ nguyên** (chạy riêng hạ tầng một service khi dev bằng `Start-Dev.ps1`).

## 1. Cấu hình (một lần)

1. Mỗi service cần `.env`: `Copy-Item <Service>\.env.example <Service>\.env` rồi điền giá trị (xem
   `<Service>/README.md`). Bắt buộc với Docker:
   - `*_DB_USERNAME`, `*_DB_PASSWORD` của 5 service có DB (Compose báo lỗi nếu thiếu).
   - IdentityService: `IDENTITY_JWT_ISSUER/AUDIENCE/KEY_ID`, `IDENTITY_ACCESS_TTL/REFRESH_TTL`,
     `IDENTITY_OTP_TTL/OTP_COOLDOWN/OTP_MAX_ATTEMPTS`, `IDENTITY_ALLOWED_ORIGINS`, `BREVO_*` (gửi OTP),
     `IDENTITY_INTERNAL_SECRET`.
   - `*_JWT_ISSUER` của các service khác và `GATEWAY_JWT_ISSUER` phải **bằng đúng** `IDENTITY_JWT_ISSUER` (đây là
     chuỗi định danh, giữ `http://localhost:8081` cũng được khi chạy Docker).
   - Secret nội bộ phải khớp từng cặp: `*_IDENTITY_INTERNAL_SECRET` = `IDENTITY_INTERNAL_SECRET`;
     `BOOKING_CARE_RECIPIENT_INTERNAL_SECRET` = `CARE_RECIPIENT_INTERNAL_SECRET`;
     `BOOKING_PROVIDER_INTERNAL_SECRET` = `PROVIDER_INTERNAL_SECRET`;
     `CARE_RECIPIENT_BOOKING_INTERNAL_SECRET` = `BOOKING_INTERNAL_SECRET`.
   - Chạy qua HTTP localhost: `IDENTITY_COOKIE_SECURE=false` để cookie web hoạt động.
2. Khóa RSA của IdentityService: `.\IdentityService\scripts\New-DevKeys.ps1` (tạo `IdentityService/keys/*.pem`,
   được mount chỉ đọc vào container).

Các biến URL/host (`*_DB_URL`, `*_URL`, `*_JWKS_URI`, `IDENTITY_REDIS_HOST`, `IDENTITY_JWT_PRIVATE_KEY/PUBLIC_KEY`)
giữ giá trị chạy local trong `.env` — Compose tự ghi đè khi chạy container.

## 2. Khởi động / dừng

Nhấn đúp `start.bat` hoặc chạy trong terminal ở thư mục `BE/`:

```bash
start.bat --no-pause
```

`start.bat` kiểm Docker (tự mở Docker Desktop nếu chưa chạy), kiểm `.env` + khóa RSA, phát hiện container cũ đang
giữ volume/port (hỏi trước khi `docker stop`, không xóa), tạo volume còn thiếu, rồi chạy
`docker compose up --build -d --wait` — chỉ báo thành công khi **mọi service healthy**.

Dừng (container + network bị xóa, **volume dữ liệu giữ nguyên**):

```bash
stop.bat --no-pause
```

Tương đương lệnh tay:

```bash
docker compose up --build -d --wait
```

```bash
docker compose down
```

## 3. Trạng thái và log

```bash
docker compose ps
```

```bash
docker compose logs -f booking-service
```

```bash
docker compose logs --tail 200 identity-service
```

Khởi động lại một service sau khi sửa code: `docker compose up --build -d --wait booking-service`.
Mở psql: `docker compose exec identity-postgres psql -U <IDENTITY_DB_USERNAME> -d CARECONNECT_IDENTITY`.

## 4. Dữ liệu và volume

- Stack dùng lại đúng volume của các `docker-compose.yml` cũ (`identityservice_identity_postgres`,
  `identityservice_identity_redis`, `carerecipientservice_care_recipient_postgres`,
  `carecatalogservice_care_catalog_postgres`, `providerservice_provider_postgres`, `bookingservice_booking_postgres`)
  — dữ liệu dev hiện có được giữ.
- Volume khai báo `external: true`: `docker compose down -v` của stack **không** xóa được chúng. Máy mới chưa có
  volume thì `start.bat` tạo volume trống; khi chạy lệnh tay trên máy mới, tạo trước bằng
  `docker volume create <tên>`.
- Volume trống (DB mới): PostgreSQL tự chạy `schema/<service>.sql` lần đầu (`/docker-entrypoint-initdb.d`). Volume đã
  có dữ liệu thì bỏ qua — thay đổi schema cho DB cũ vẫn áp tay bằng change script (AGENTS.md §5), vd
  `ProviderService/schema/provider_upgrade_v2.sql`.
- **Không chạy song song** stack tổng với `docker-compose.yml` riêng của service (hai PostgreSQL cùng mở một volume
  sẽ hỏng dữ liệu; port cũng trùng). `start.bat` dừng container cũ trước khi chạy.
- Không dùng `docker compose down -v`, `docker volume prune`, `docker system prune --volumes`.

## 5. Lưu ý

- Chạy stack Docker thì không chạy cùng lúc các app bằng `Start-Dev.ps1` / IDE trên cùng port (8080–8085), và
  không chạy `scripts/e2e/Run-E2E.ps1` (cũng dùng 8080–8085).
- Port chỉ mở trên `127.0.0.1`. Thiết bị thật trong LAN (app mobile) không gọi được trực tiếp — dùng emulator
  (`10.0.2.2:8080`) hoặc đổi bind trong `compose.stack.yaml` khi cần.
- Build image bỏ qua test (`-DskipTests`); test vẫn chạy bằng `.\mvnw.cmd test` trong từng service.
