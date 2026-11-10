# Aftercare — kết quả triển khai Người C (2026-10-24)

Phạm vi: 17 task / 74 SP trong `WORK_PLAN_C_AFTERCARE.md`. Code nằm trong các aggregate/service hiện hữu,
không tạo PaymentService, broker hoặc Flyway. Payment là contract bên ngoài, các test dùng stub. SQL đã áp dụng vào DB dev trong lần khởi động Docker ngày 2026-10-24, sau khi sao lưu.


Test thủ công từng task: [Swagger/Postman guide](2026-10-24-AFTERCARE_MANUAL_TEST.md), kèm collection và environment mẫu.

## Hợp đồng API và thay đổi FE

Mọi đường dẫn dưới đây bắt đầu bằng `/api/v1` và đi qua ApiGateway; response trong `APIResponse` trừ stream SSE.
Phân quyền tài nguyên ở service, ngoài phạm vi trả 404. SERVICE không dùng API người dùng.

| Task | Kết quả / API chính |
|---|---|
| CR-01 | Snapshot cho SERVICE/ADMIN hoặc CUSTOMER có VIEWER; access chỉ SERVICE; batch customer lọc membership. INACTIVE chỉ OWNER sửa hồ sơ, mọi ghi thuốc/liên hệ bị chặn tới khi ACTIVE. Các service con có interface. |
| IAM-02/03 | Refresh opaque hash + session authzVersion, Lua rotate nguyên tử; reuse thu hồi toàn bộ phiên/version. Access có sid bị vô hiệu tại Identity khi xóa phiên; đổi/reset mật khẩu, khóa, thu hồi role đều thu hồi phiên. Chỉ tin XFF từ IP proxy cấu hình. |
| IAM-04 | Lookup user/batch kiểm self hoặc manager cùng trung tâm; ADMIN/SERVICE được tra cứu toàn bộ/by-email. Không trả permissions khi xem người khác. Identity Feign ở Booking/Provider/CR dùng token SERVICE. |
| IAM-05 | `GET /users?role=&status=&centerId=&keyword=&page=&size=&sort=` trả **result.content** cùng page/size/totalElements/totalPages. Manager chỉ caregiver thuần cùng trung tâm. PATCH status nhận `{status,reason}`, giữ query status cũ thêm một sprint. |
| IAM-06 | `PATCH /users/centers/{id}/status` SERVICE/ADMIN; trả affectedUsers. Mở chỉ lock_source=CENTER, giữ khóa ADMIN riêng. |
| PRV-06 | Manager list trung tâm chỉ của mình; dữ liệu nhạy cảm bị bỏ khỏi response công khai. Caregiver ngoài phạm vi trả 404. PUT profile không nhận status; PATCH status riêng, ACTIVE yêu cầu VERIFIED; không vượt ARCHIVED/SUSPENDED bằng verify. |
| PRV-05 | `PATCH /certificates/{id}/center-verification` manager rồi `/verification` admin, body `{approved,reason,version}`. PENDING → CENTER_VERIFIED → ADMIN_VERIFIED; từ chối cần lý do, quá hạn không duyệt. Sửa nội dung reset xác minh. `/certificates/pending-audit`, `/caregivers/pending-verification` có phân trang. Public chỉ chứng chỉ ADMIN_VERIFIED chưa hết hạn. |
| PRV-04 | `PUT /care-centers/me` whitelist thông tin; state machine trung tâm, kiểm manager trước kích hoạt, chặn archive khi còn booking. provider_outbox gửi khóa/mở Identity + hủy hệ thống Booking sau commit. |
| MED-01 | Mọi `/media/**` chỉ SERVICE; source_service lấy từ client_id, cùng owner mới xem/download/delete. Lỗi chuyển sang 7xxx. Provider/Booking kiểm quyền user trước gọi Media bằng SERVICE; upload ngoài transaction, bù xóa file khi ghi DB lỗi. |
| NTF-01/02 | GET `/notifications` (unreadOnly/type/page/size), `/unread-count`, PATCH `/{id}/read`, `/read-all`; POST cho SERVICE. Các sự kiện booking/phân công/ca/report/review/complaint gửi sau commit. Nhắc ca sắp bắt đầu trong 2h mỗi 15 phút, reminder_sent_at khử trùng. |
| OPS-03 | `/care-sessions/{id}/tasks`, PATCH `/tasks/{taskId}`, POST/GET `/notes`: chỉ caregiver được giao ghi trong ca IN_PROGRESS; tối đa 20 task custom, SKIPPED cần lý do; notes/vitals JSON validate. |
| OPS-05 | `/care-reports`: nháp theo session, PUT, POST `/{id}/submit`, POST/DELETE ảnh. Một report/session; SUBMITTED bất biến và chỉ submit sau ENDED; tối đa 5 ảnh JPEG/PNG/WebP, 10MB/ảnh; URL tải có hạn. |
| FB-01 | `/feedbacks`, `/me`, `/eligible?bookingId=`; POST, POST `/{id}/reply` một lần, PATCH `/{id}/visibility` admin có lý do. Một đánh giá/booking/caregiver có buổi COMPLETED. Tên khách viết tắt, rating chỉ từ VISIBLE; booking_outbox cập nhật Provider `PUT /caregivers/{id}/rating` bằng SERVICE. |
| FB-02 | `/complaints` list/get/create + PATCH accept/resolve/admin-resolve, POST escalate. CENTER complaint lên ADMIN ngay; customer escalate sau RESOLVED. Audit mọi bước; kiểm payment đã trả−hoàn ngoài transaction, refund sau commit qua outbox idempotency complaintId. |
| STAT-01 | Booking `/statistics/centers`, `/statistics/system` (byCenter phân trang); Provider `/care-centers/statistics`; Identity `/users/statistics`. Chỉ aggregate query, manager bị ép center scope. |

## Định nghĩa STAT-01 đã được người dùng chấp thuận

- `from`/`to` tính theo ngày VN, bao gồm hai đầu, tối đa 366 ngày; mặc định đầu tháng tới hôm nay.
- Booking/review/complaint theo ngày tạo; occurrence theo ngày bắt đầu diễn ra. Rating chỉ review VISIBLE.
- `completionRate = 100 × COMPLETED / (COMPLETED + CANCELLED + MISSED + CUSTOMER_NO_SHOW)`, làm tròn 2 chữ số,
  mẫu số bằng 0 trả 0. RESOLVED trong complaint stats gồm cả RESOLVED_BY_ADMIN.
- Top 10 caregiver theo số session ENDED trong khoảng buổi, có avgRating. Doanh thu do Payment sở hữu.

## SSE và vận hành

`GET /notifications/stream`: dùng cookie access hoặc Bearer, event `notification`, `session-update`, `heartbeat`.
Heartbeat 25 giây, kết nối 30 phút; client reconnect và gọi danh sách để lấy các event bỏ lỡ. Trình duyệt EventSource
với cookie cần credentials; nếu dùng Bearer cần client hỗ trợ streaming có header. SSE registry nằm trong RAM của
một instance; chạy nhiều instance cần broker hoặc polling fallback. GET notifications/unread-count là fallback bền vững.

Provider outbox có lease, backoff, giới hạn retry, status FAILED; lỗi phải được vận hành kiểm tra trước requeue. HTTP 4xx vĩnh viễn (trừ 408/429) và sai credentials SERVICE dừng FAILED ngay; chỉ lỗi tạm thời được retry.
SUSPEND/RESUME theo thứ tự version từng trung tâm; dòng FAILED cũ chặn lệnh cascade sau để không mở nhầm tài khoản.
Thông báo ACTIVATE không chặn các lệnh khóa/mở quan trọng. Booking hủy hệ thống có lỗi một phần trả 503 để retry
phần còn lại; các booking đã xử lý không bị hoàn hai lần. Rating retry tính lại tổng mới nhất từ review VISIBLE.

Access ở service xác minh JWT offline vẫn còn độ trễ thu hồi tối đa bằng access TTL; thu hồi ngay được kiểm tại Identity.
`IDENTITY_TRUSTED_PROXIES` mặc định loopback, Docker cần cấu hình IP gateway cố định tin cậy. Gateway ghi đè XFF từ
peer thật, loại Forwarded do client đặt. Các danh tính SERVICE cần cấu hình clientId/secret riêng như IAM-01.

Env bổ sung: `BOOKING_MEDIA_URL` (gateway), `BOOKING_SHIFT_REMINDER_INTERVAL` (15m),
`PROVIDER_OUTBOX_SCAN_INTERVAL` (30s), `PROVIDER_OUTBOX_MAX_ATTEMPTS` (10), `PROVIDER_OUTBOX_LEASE` (60s),
`PROVIDER_OUTBOX_MAX_BACKOFF` (1h); các URL/timeout SERVICE token tiếp tục theo cấu hình service hiện có.

## SQL áp dụng tay

Sao lưu DB trước triển khai. Service dùng ddl-auto=validate. DB **mới** chỉ chạy bootstrap sql tương ứng;
DB **đang có**, đã chạy các migration của Người B, chạy đúng một lần script:

1. Identity: `IdentityService/schema/2026-10-24-user-status.sql`.
2. Provider: `ProviderService/schema/2026-10-24-aftercare.sql` (backfill certificate VERIFIED → ADMIN_VERIFIED).
3. Booking: `BookingService/schema/2026-10-24-aftercare.sql`.
4. Media: `MediaService/schema/2026-10-24-service-ownership.sql`: kiểm mapping source_service cũ tới đúng client_id;
   script mặc định PROVIDER_SERVICE→provider và BOOKING_SERVICE→booking. Dữ liệu giá trị khác cần mapping rõ trước chạy.

Không chạy lại bootstrap trên DB có dữ liệu. CR không cần đổi schema. Các số lỗi đề xuất trong plan bị trùng được
cấp số mới, giữ nguyên key: RECIPIENT_INACTIVE=2701, CAREGIVER_INVALID_TRANSITION=4216,
CENTER_INVALID_TRANSITION=4122, TASK_NOT_FOUND=5820, INVALID_VITALS=5821,
TOO_MANY_CUSTOM_TASKS=5822, REPORT_PHOTO_NOT_FOUND=5823.

## Kiểm chứng

| Kiểm tra | Kết quả |
|---|---|
| IdentityService | 85 test, không lỗi; đã chạy thêm suite trên PostgreSQL 17 + Redis 7 thật |
| CareRecipientService | 89 test, không lỗi |
| BookingService | 99 test; hai ca concurrency chỉ chạy PostgreSQL, đã pass cùng toàn bộ BookingApiTests trên PostgreSQL 17 |
| ProviderService | 61 test, không lỗi; bootstrap được Hibernate validate trên PostgreSQL trong E2E |
| MediaService | 6 test, không lỗi, R2 mock |
| ApiGateway | 20 test, không lỗi, gồm XFF giả và stream SSE |
| E2E 7 service + PostgreSQL/Redis thật | 102 kiểm tra pass: a=19, b=14, c=15, e=48, d=4, f=2 |
| Nâng cấp schema PostgreSQL từ bản HEAD cũ | Identity, Provider, Booking đều pass; không tác động DB dev |

E2E đã xác nhận khiếu nại accept → resolve → escalate → admin refund, Payment stub nhận đúng một lệnh hoàn;
khóa trung tâm thu hồi phiên Identity và hủy booking; mở lại không khôi phục booking. Runner hỗ trợ BasePort để
không dừng service dev. Các test security/transaction dùng identity/payment stub cục bộ; Media dùng R2 mock,
không gửi file lên bucket thật. Không kiểm VNPay/PaymentService thật. SQL đã áp dụng vào DB dev ngày 2026-10-24, sau khi sao lưu và kiểm tra số dòng dữ liệu cũ không đổi.

Lượt kiểm tra đồng thời có một response 500/NullPointerException không ổn định trong MockMvc gateway. Chưa xác định
được nguyên nhân; chưa tái hiện khi lặp route 20 lần và chạy lại toàn bộ suite bốn lượt (80 vòng route). Đã thêm log
stack frame an toàn, không ghi message/header, để chẩn đoán nếu gặp lại. E2E 102 kiểm tra không gặp lỗi này.

## Docker

Đã build thành công 7/7 image `careconnect/{identity-service,care-recipient-service,care-catalog-service,
provider-service,booking-service,media-service,api-gateway}:dev` bằng Dockerfile trong repo ngày 2026-10-24.
Image chạy user `careconnect` (không root), JRE 21. Build không khởi động hoặc thay dữ liệu của stack dev.
Đã sửa lỗi định dạng IDENTITY_SERVICE_CLIENTS trong .env local (các dấu phân cách BCrypt bị xuống dòng/mất định dạng),
giữ nguyên hash/secret và kiểm tra cả ba client booking/provider/care-recipient đều khớp. Có backup .env local;
không đưa secret vào Git. `docker compose config --quiet` đã thành công.

Ngày 2026-10-24 đã khởi động stack `careconnect`: 7 ứng dụng Spring + 6 PostgreSQL + Redis đều healthy.
Plastic SCM `Plastic Server 6` đã được dừng theo yêu cầu để giải phóng 8084; Provider giữ cổng mặc định.
Gateway health, Swagger UI và 6 OpenAPI spec đều HTTP 200; ba SERVICE client cấp token thành công.
PaymentService thật chưa có trong repo, không khởi động Payment giả trong stack dev này.

Trước khởi động đã sao lưu 6 DB và áp các script ngày 2026-10-22 còn thiếu rồi tới aftercare 2026-10-24.
Đã so sánh số dòng của các bảng cũ sau migration, không thay đổi. Backup và danh sách script đã áp tại:
`C:/Users/Juuga/Documents/MSS301/careconnect-backups/20261024-161745`.

Lệnh chạy lại: `docker compose up -d --no-build --wait --wait-timeout 240`.
Build image độc lập không đọc .env; trạng thái healthy ở đây đã được kiểm tra trên container thực tế.
