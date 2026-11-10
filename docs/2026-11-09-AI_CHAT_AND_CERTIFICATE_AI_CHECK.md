# AI chat + kiểm tra chứng chỉ bằng AI — 2026-11-09

Trạng thái: **code xong, chưa commit**; test BE/FE xanh (xem cuối file). Chưa kiểm tra trên trình duyệt với Groq thật.

## Quyết định của chủ dự án (2026-11-09)

| # | Câu hỏi | Chốt |
|---|---|---|
| 1 | Ai được chat? | Khách: 3 lượt/ngày, không ảnh, không lưu lịch sử. **Mọi role đã đăng nhập** dùng như customer (lưu hội thoại, tối đa 3 ảnh/câu hỏi). |
| 2 | AI chạy lúc nào? | **Sau khi Center Manager xác nhận** (bước 3 → 4), chạy nền; manager không chờ AI. |
| 3 | Ai xem kết quả AI? | **Chỉ ADMIN**. Caregiver / manager chỉ thấy trạng thái kiểm tra. |
| 4 | Lưu kết quả? | **Chỉ kết quả mới nhất**, cột trên `certificates`. |
| 5 | Mobile caregiver? | Không đổi (trạng thái chứng chỉ giữ nguyên enum). |

## Phần A — AI chat (AIService)

CRUD đầy đủ (`/api/v1/ai`, tag `AI Chat`):

| Thao tác | Endpoint | Ghi chú |
|---|---|---|
| Hạn mức | `GET /chat/quota` (public) | Khách: `remainingPrompts`/`dailyPromptLimit`; đã đăng nhập: giới hạn ảnh. Đọc không khóa. |
| Tạo | `POST /chat` (public, multipart: part JSON `request` + `images[]`) | Khách: không `conversationId`, không ảnh (8103), hết lượt 8108. |
| Đọc | `GET /conversations`, `GET /conversations/{id}` | Có `version`. |
| **Sửa (mới)** | `PATCH /conversations/{id}` `{title, version}` | Lệch version → 409 8203; 8204/8205 tên trống / > 120. |
| Xóa | `DELETE /conversations/{id}` | Xóa mềm + xóa ảnh trên MediaService. |

- Giới hạn khách mặc định 5 → **3** (`AI_GUEST_DAILY_PROMPT_LIMIT`, yaml, `AIProperties`).
- Method controller/service đổi tên theo quy ước (`list`/`get`/`update`/`delete`, `getQuota`); response quota dựng qua
  `ChatMapper.toQuotaResponse` (không builder trong service).

## Phần B — Kiểm tra chứng chỉ 5 bước

1. Caregiver khai báo (`POST/PUT /certificates…`). 2. Caregiver tải ảnh (`/provider-media/CERTIFICATE/{id}`).
3. Center Manager xác nhận (`PATCH /{id}/center-verification`) — **mới: phải có ≥ 1 ảnh/file** (409 4255); xác nhận →
   `ai_check_status = QUEUED`. 4. `CertificateAiCheckScheduler` (mỗi `PROVIDER_AI_CHECK_SCAN_INTERVAL`, mặc định 15s) gửi
   sang AIService `POST /api/v1/ai/certificate-checks` (token SERVICE, qua gateway). 5. ADMIN xem kết quả và duyệt
   (`PATCH /{id}/verification`) — **mới: duyệt khi AI chưa trả LOW/MEDIUM thì bắt buộc lý do** (400 4256).

### AIService — đọc ảnh, đối chiếu bằng code

- Mô hình chỉ **chép nội dung ảnh ra JSON** (`CertificatePrompts`, không được biết giá trị khai báo); bóc `<think>`,
  khối ```json; không đọc được / không phải JSON → `UNDETERMINED` (không phải lỗi). Groq lỗi → 503 để Provider thử lại.
- `CertificateRiskServiceImpl` so sánh (bỏ dấu, hoa thường, ký tự phân cách; họ tên so tập từ; số chứng chỉ bỏ nhãn
  "Số/Số hiệu/No."; tên chứng chỉ / đơn vị cấp: chứa nhau hoặc ≥ 60 % từ chung):

| Mức | Điều kiện |
|---|---|
| UNDETERMINED | Không có ảnh (`NO_IMAGE`), chỉ có PDF (`UNSUPPORTED_FILE`), AI không đọc được (`UNREADABLE`) |
| HIGH | `MISMATCH` họ tên hoặc số chứng chỉ, hoặc `EXPIRED` (hạn trên ảnh, không có thì hạn khai báo, so với hôm nay giờ VN) |
| MEDIUM | `MISMATCH` tên chứng chỉ / đơn vị cấp / ngày, hoặc `NOT_FOUND` một trường đã khai |
| LOW | Không cảnh báo |

- Cấu hình: `AI_GROQ_VISION_MODEL` (trống = `AI_GROQ_MODEL`; **phải là mô hình nhận ảnh**), `AI_CERTIFICATE_MAX_IMAGES`
  (≤ 5, giới hạn Groq), `AI_CERTIFICATE_TEMPERATURE`, `AI_CERTIFICATE_TIMEZONE`. Mã lỗi 8600–8603.

### ProviderService — hàng chờ trên chính bảng `certificates`

- Cột mới (`schema/changes/2026-11-09_certificate_ai_check.sql`, cũng thêm cuối `provider.sql`): `ai_check_id`,
  `ai_check_status` (QUEUED/PROCESSING/COMPLETED/FAILED), `ai_check_attempts`, `ai_next_attempt_at`, `ai_risk_level`,
  `ai_warnings` (jsonb), `ai_extracted` (jsonb), `ai_model`, `ai_checked_at`, `ai_last_error` + index hàng chờ. File SQL
  xếp hàng luôn các chứng chỉ đang `CENTER_VERIFIED`.
- Không dùng `provider_outbox`: outbox gửi ngay trong request (manager sẽ chờ AI tới 60s) và gắn với sự kiện trung tâm.
- Scheduler nhận việc bằng `UPDATE … WHERE ai_check_id = ? AND status IN (QUEUED, PROCESSING quá lease)` (không tăng
  `version` → không làm 409 người đang mở chứng chỉ), gọi MediaService (URL tải có hạn + content type) và AIService
  **ngoài transaction**, rồi ghi kết quả có điều kiện `ai_check_id` (caregiver sửa / admin chạy lại giữa chừng → bỏ kết
  quả cũ). Lỗi: backoff 30s·2ⁿ (≤ `PROVIDER_AI_CHECK_MAX_BACKOFF`), hết `PROVIDER_AI_CHECK_MAX_ATTEMPTS` hoặc AIService trả
  400 → `FAILED`.
- Sửa thông tin **hoặc thêm/xóa ảnh** chứng chỉ → quay về `PENDING`, xóa kết quả xác minh + AI (`Certificate.restartReview()`).
- Caregiver **chỉ xóa** chứng chỉ `PENDING` hoặc `REJECTED` (chốt 2026-11-10); `CENTER_VERIFIED` / `ADMIN_VERIFIED` → 409
  `4253` (`DELETABLE_STATUSES` trong `CertificateServiceImpl`).
- API mới (ADMIN): `GET /certificates/pending-audit?riskLevel&aiCheckStatus` (HIGH → UNDETERMINED → MEDIUM → LOW → chưa có,
  rồi `centerVerifiedAt`; `aiCheckStatus=QUEUED` gồm cả PROCESSING), `GET /certificates/pending-audit/statistics`,
  `POST /certificates/{id}/ai-check` (chạy lại, chỉ khi `CENTER_VERIFIED`, 409 4257).
- `CertificateResponse` thêm `caregiverName`, `centerId/Name`, `centerVerifiedAt/Reason`, `aiCheckStatus` (mọi role) và
  `aiRiskLevel`, `aiWarnings`, `aiExtracted`, `aiCheckedAt` (**chỉ ADMIN** — `CertificateMapper.toAdminResponse`).
- Feign `AiClient` (`PROVIDER_AI_URL` = gateway, read timeout 95s), mã lỗi 4255–4257, 4704.

## Hạ tầng

- **ApiGateway**: route `/api/v1/ai/**` qua `LongRequestProxyHandler` (read timeout riêng `GATEWAY_AI_READ_TIMEOUT`, mặc
  định 90s; route thường vẫn 10s). Public: `POST /api/v1/ai/chat` (vẫn đọc token nếu có), `GET /api/v1/ai/chat/quota`.
  Swagger `/api-docs/ai`. Biến mới bắt buộc `GATEWAY_AI_URL`.
- **Docker**: `AIService/Dockerfile`, `.dockerignore`, `compose.stack.yaml` (ai-postgres 5448, ai-service 8088), include
  trong `compose.yaml`, `start.bat` (volume `aiservice_ai_postgres`, đã tạo sẵn trên máy dev).

## Việc chủ dự án cần làm

1. Đăng ký client SERVICE `ai` trong `IDENTITY_SERVICE_CLIENTS` (`IdentityService/scripts/New-ServiceClient.ps1 ai`, secret
   vào `AI_SERVICE_CLIENT_SECRET`) — thiếu thì upload ảnh chat lỗi (giống 6011 của PaymentService).
2. `AI_GROQ_API_KEY` và `AI_GROQ_VISION_MODEL` là mô hình **nhận ảnh** trên Groq.
3. Áp `ProviderService/schema/changes/2026-11-09_certificate_ai_check.sql` vào DB `CARECONNECT_PROVIDER`.
4. Build lại `api-gateway`, `provider-service`, `ai-service` (`docker compose up --build -d api-gateway provider-service
   ai-service`). `ProviderService/.env` đang có `PROVIDER_AI_URL=http://localhost:8088` (gọi thẳng AIService khi chạy
   local); trong Docker `compose.stack.yaml` ghi đè sang gateway.

## Kiểm thử

- ApiGateway `mvnw test`: 24/24 (thêm route AI public/không public, timeout dài).
- AIService: `CertificateCheckServiceImplTests` 6/6 (LOW / HIGH tên / HIGH hết hạn / MEDIUM đơn vị cấp / không đọc được
  / không ảnh – chỉ PDF). `AiServiceApplicationTests.contextLoads` cần DB thật — không chạy trong lần này.
- ProviderService `mvnw test`: 91/91 (thêm `CertificateAiCheckTests` 8, cập nhật `ProviderIntegrationTests`).
- FE: xem `FE-careconnect/docs/ARCHITECTURE.md` (widget chat, `/admin/certificates`).
