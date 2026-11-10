# AGENTS.md — MediaService

Áp dụng quy ước `../AGENTS.md`.

| Field | Value |
|---|---|
| Artifact / package | `mss301:MediaService:0.0.1-SNAPSHOT` / `mss301.mediaservice` |
| Port / prefix | 8086 / MEDIA_ |
| PostgreSQL | 127.0.0.1:5446 / CARECONNECT_MEDIA |
| ErrorCode | 7000–7999 (+ 401/403/404/9999) |

Media giữ metadata và credentials Cloudflare R2. Mọi `/api/v1/media/**` chỉ nhận token SERVICE qua gateway.
`source_service` lấy từ JWT `client_id`; tuyệt đối không lấy từ multipart. Get/download/delete phải cùng owner
service, khác owner trả 404. Service nghiệp vụ kiểm quyền người dùng và sở hữu liên kết file UUID trong DB riêng.
Upload backend multipart, download presigned GET ngắn hạn; không dùng presigned PUT trong phạm vi này.
