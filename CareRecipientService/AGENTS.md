# AGENTS.md — CareRecipientService

> Chỉ ghi phần **khác / bổ sung** so với `../AGENTS.md`. Đọc file gốc trước.

## Thông số

| Field | Value |
|---|---|
| Thư mục / artifact | `CareConnect/CareRecipientService/` · `mss301:CareRecipientService:0.0.1-SNAPSHOT` |
| Base package | `mss301.carerecipientservice` |
| Port / env prefix | `8082` / `CARE_RECIPIENT_` |
| Dải `ErrorCode` | **2000–2999** (+ 401/403/404/9999 dùng chung) — 20xx chung, 21xx hồ sơ, 22xx thuốc, 23xx liên hệ khẩn cấp, 24xx người quản lý, 25xx tích hợp Identity, 26xx API nội bộ |
| DB | PostgreSQL riêng `127.0.0.1:5442`, `schema/care_recipient.sql` (áp tay, không Flyway). Không Redis |

## Khác với IdentityService

- **Không phát token.** Xác minh JWT bằng `NimbusJwtDecoder.withJwkSetUri` (JWKS của Identity), audience bắt buộc
  `careconnect-api` (`security/TokenClaims.API_AUDIENCE`). Quyền là snapshot trong token — không kiểm trạng thái tài khoản.
- **CSRF chỉ đọc**: `security/ReadOnlyCookieCsrfTokenRepository` so header `X-XSRF-TOKEN` với cookie `XSRF-TOKEN` của
  Identity, **không bao giờ ghi cookie**. CSRF chỉ bắt buộc khi request ghi mang cookie `access_token`.
- CORS tắt (gateway xử lý).
- `config/RequestIdFilter`: MDC `requestId` từ header `X-Request-Id` (gateway) → log pattern `logging.pattern.correlation`
  và Feign interceptor chuyển tiếp.
- Enum quyền trên hồ sơ tên là **`RecipientAccessLevel`** (không đặt `AccessLevel` để khỏi trùng `lombok.AccessLevel`
  dùng trong `@FieldDefaults`). Giá trị DB/JSON: `OWNER`, `EDITOR`, `VIEWER`.
- `BloodType` lưu/trả ký hiệu `"A+"`… qua `entity/BloodTypeConverter` + `@JsonValue`.
- `RecipientManager` là bảng liên kết, **xóa dòng** khi gỡ người thân (không kế thừa `BaseEntity`, như `user_roles`).

## Quy tắc nghiệp vụ (bắt buộc giữ)

1. Mọi API customer kiểm quyền qua `RecipientAccessService.checkAccess(recipientId, actorId, level)`: không quan hệ →
   404 `RECIPIENT_NOT_FOUND`, thiếu cấp → 403 `RECIPIENT_ACCESS_DENIED`. Không tự viết kiểm quyền rời rạc.
2. Thao tác ghi đụng tới người quản lý / liên hệ chính / trạng thái **khóa dòng hồ sơ** (`CareRecipientRepository.findForUpdate`)
   sau khi kiểm quyền.
3. Đúng một quản lý chính / hồ sơ, luôn OWNER (CHECK + partial unique index trong SQL); khi chuyển: bỏ cờ cũ → `flush`
   → đặt cờ mới, cùng transaction. Tương tự cho liên hệ khẩn cấp chính.
4. Gọi IdentityService (Feign) **ngoài** transaction DB: method cần Identity không có `@Transactional`, phần ghi dùng
   `TransactionTemplate`. Feign chỉ được dùng qua `service/IdentityUserService`.
5. Danh sách người quản lý phải chịu được Identity lỗi (fallback batch rỗng); thao tác cần xác minh (thêm người thân)
   không được đoán khi Identity lỗi → 503.
6. `PageResponse.of(page, items)` và `APIResponse.success(...)` là wrapper chung (không qua MapStruct); mọi entity ↔ DTO
   qua mapper.
7. API nội bộ `/internal/**` có chain bảo mật riêng (`InternalSecurityConfig`, header `X-Internal-Secret`), không nhận JWT.

## TODO đã biết

Đồng bộ người quản lý khi user bị khóa/xóa (event); quyền caregiver (Booking); audit; Flyway (đợt chung).
