# AGENTS.md — MediaService

> Áp dụng cùng quy ước của `../AGENTS.md` trong CareConnect.

| Field | Value |
|---|---|
| Artifact | `mss301:MediaService:0.0.1-SNAPSHOT` |
| Base package | `mss301.mediaservice` |
| Port / env prefix | `8085` / `MEDIA_` |
| Dải ErrorCode | `5000–5999` (+ `401`, `403`, `404`, `9999`) |
| DB | PostgreSQL riêng `127.0.0.1:5445`, database `CARECONNECT_MEDIA` |

## Phạm vi

- Sở hữu metadata file và credentials Cloudflare R2.
- Chỉ cung cấp API `/internal/media/**` cho service-to-service; không đi qua ApiGateway.
- Service nghiệp vụ sở hữu quan hệ tới file bằng `media_file_id` UUID, không FK xuyên database.
- Upload sử dụng multipart backend-to-backend theo phạm vi hiện tại; không dùng presigned PUT.
- Download private bằng presigned GET ngắn hạn.
