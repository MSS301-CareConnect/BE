# AGENTS.md — AIService

> Chỉ ghi phần khác với `../AGENTS.md`. Nghiệp vụ: `../docs/2026-11-09-AI_CHAT_AND_CERTIFICATE_AI_CHECK.md`.

| Field | Value |
|---|---|
| **Base package** | `mss301.aiservice` |
| **Port** | `AI_SERVER_PORT` (mặc định 8088) |
| **PostgreSQL (dev)** | 5448 (`AI_DB_*`), schema `schema/ai.sql` |
| **Dải mã lỗi `ErrorCode`** | `8000–8999` (+ `401`, `403`, `404`, `9999` dùng chung ý nghĩa) |
| **Route gateway** | `/api/v1/ai/**` (proxy có read timeout riêng) |

- Gọi Groq (API ngoài, không qua gateway) bằng `client/GroqClient` + `client/impl/GroqClientImpl`; cấu hình `AI_GROQ_*`.
- Gọi MediaService qua gateway bằng `client/MediaClient` với token SERVICE (`client/ServiceTokenProvider`).
- `POST /api/v1/ai/certificate-checks`, `/certificate-verifications` chỉ cho token SERVICE (`hasRole('SERVICE')`).
