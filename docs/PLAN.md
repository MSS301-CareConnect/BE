# CareConnect — Implementation checklist

Tracker riêng của CareConnect. Chỉ đánh hoàn thành khi hạng mục đã triển khai và kiểm chứng; cập nhật ngày trong cùng thay đổi. Các scope nghiệp vụ tiếp theo bổ sung khi được chốt.

- [x] Tạo hướng dẫn dùng chung `AGENTS.md`, điều chỉnh quy ước từ backend nguồn cho CareConnect/microservice, ghi schema IdentityService và quyết định còn thiếu; đối chiếu source guide, ERD và skeleton hiện có — hoàn thành 2026-09-30.
- [x] Chốt contract IdentityService: ACTIVE/LOCKED, permission catalog, một trung tâm cho manager/caregiver, ADMIN tạo manager và manager tạo caregiver, kiểm trạng thái/quyền hiện hành, schema SQL không Flyway — hoàn thành 2026-09-30.
- [x] Triển khai IdentityService port 8081 và bổ sung dependencies Spring Security Resource Server, Redis, Actuator, MapStruct cùng test dependencies phù hợp — hoàn thành 2026-09-30.
- [x] Đăng ký CUSTOMER, xác thực email OTP Brevo, gửi lại OTP, quên/đặt lại/đổi mật khẩu; OTP giới hạn lượt thử và consume một lần — hoàn thành 2026-09-30.
- [x] JWT RS256/JWKS, web HttpOnly cookies + CSRF, mobile bearer, refresh rotation, logout/logout-all và thu hồi phiên khi khóa/đổi mật khẩu — hoàn thành 2026-09-30.
- [x] RBAC và quyền hiện hành, quản lý tài khoản theo phạm vi trung tâm, soft delete, audit và bảo vệ ADMIN hoạt động cuối cùng — hoàn thành 2026-09-30.
- [x] Schema SQL thủ công, seed role/permission, bootstrap ADMIN qua env, Docker dev và hướng dẫn cấu hình/chạy — hoàn thành 2026-09-30.
- [x] Kiểm chứng 44 tests với PostgreSQL/Redis thật, Maven package và JAR tại 8081; kiểm web CSRF/login/refresh/logout, JWKS và Redis outage trả 503; dọn môi trường test riêng — hoàn thành 2026-09-30.
- [x] Gộp cấu hình Spring vào một application.yaml, chuyển Swagger/Secure cookie sang env, chia .env.example theo nhóm; bổ sung constructors/Builder/FieldDefaults cho entity, giữ builder defaults và MapStruct mapping field kế thừa; clean package với 44 tests mặc định thành công, JAR chỉ có một application.yaml — hoàn thành 2026-10-01.
- [x] Ghi chú từng biến JWT issuer/audience/key ID/private key/public key trong .env.example, đối chiếu luồng issue/validate/JWKS và script tạo khóa; giữ nguyên giá trị cấu hình — hoàn thành 2026-10-01.
- [x] Hỗ trợ Windows PowerShell 5.1 cho scripts dev; tạo RSA PEM bằng Java 21, kiểm cặp khóa/chữ ký RS256, từ chối ghi đè và xác nhận gitignore; kiểm syntax/env split của Start-Dev trên 5.1 — hoàn thành 2026-10-01.
- [x] Đồng bộ Compose và env mẫu với tên DB CARECONNECT_IDENTITY, cấu hình IDENTITY_DB_NAME và hướng dẫn schema/volume; kiểm docker compose config — hoàn thành 2026-10-01.
- [x] Viết lại `AGENTS.md` theo bố cục và quy ước `backend/CLAUDE.md` (§3 Code Conventions, §4 Authentication Flow, §7 Rules) cho microservice, bỏ Flyway, ghi ngữ cảnh role/chức năng → domain sở hữu và danh sách code IdentityService lệch quy ước (§8); đối chiếu với code hiện có — hoàn thành 2026-10-01.
- [x] Refactor IdentityService theo `AGENTS.md` (style, controller mỏng + `@CurrentUserId`, tầng service, MapStruct cho entity liên kết/response, magic string, `SecurityErrorHandler`, `@Repository` + `@Param`, xóa ErrorCode kế thừa AIMA), giữ nguyên API contract; 45/45 tests trên H2 và PostgreSQL/Redis thật, package thành công; ghi nhận API gateway sẽ có (chưa tạo) — hoàn thành 2026-10-01.
- [x] Tách lỗi CSRF thành `CSRF_INVALID` 1111 (403) khỏi `UNAUTHORIZED`; Swagger có scheme `csrf` (header `X-XSRF-TOKEN`) cho mọi request ghi trừ `/auth/mobile/*` để test luồng web/đăng ký trên Swagger; 47/47 tests H2 — hoàn thành 2026-10-01.
- [ ] Tạo API gateway (đã chốt sẽ có): chọn công nghệ, định tuyến, chuyển tiếp cookie/CSRF/Authorization, CORS và cookie domain theo gateway.
- [ ] Kiểm chứng gửi OTP với Brevo account/sender thật sau khi có cấu hình triển khai; chốt TTL/chính sách production.
- [ ] Tích hợp contract CenterService để xác minh UUID trung tâm và đồng bộ vòng đời membership/trung tâm.
- [ ] Chốt audience và cơ chế kiểm trạng thái/version hiện hành tại các domain service trước khi cam kết thu hồi ngay toàn hệ thống.
- [ ] Bổ sung key ring/overlap nếu cần rotation RSA không gián đoạn production.
