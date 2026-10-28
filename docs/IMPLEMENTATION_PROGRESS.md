# CareConnect — Tiến độ triển khai sau khảo sát 2026-10-28

Nguồn: `docs/2026-10-28-FULLSTACK_GAP_ANALYSIS.md` (§7 mã vấn đề, §8.1 quyết định D15…D30, §9 kế hoạch).
Thứ tự đã duyệt: 0 → 1a → 1b → 1c → 2 → 3 → 4 → 5 → 6. Mỗi đợt: kế hoạch → BE → FE → test → commit (không push) → cập nhật file này.
Code FE nằm ở repo `FE-careconnect` (commit riêng repo đó).

---

## Đợt 0 — Gỡ rủi ro nhanh

### Kế hoạch

| Hạng mục | File chính | Ghi chú |
|---|---|---|
| M-01 Lọc bản nháp care report khỏi khách / manager | `BookingService/.../CareReportServiceImpl.java` (+ test) | Không đổi schema |
| M-02 `GET /caregivers` trả DTO công khai | `ProviderService/.../CaregiverController`, `CaregiverServiceImpl` (+ test, snapshot FE) | Đổi kiểu response của một endpoint đọc; FE chỉ dùng field công khai |
| H-14 Payment: handler `AccessDeniedException`, lỗi parse / thiếu tham số | `PaymentService/.../GlobalExceptionHandler.java` | |
| C-03 (hạ tầng) Bật PaymentService trong `compose.yaml`, volume trong `start.bat` | `compose.yaml`, `start.bat`, `DOCKER.md` | Không mở path Stripe (D16) |
| H-01 Xóa trang cọc giả `/customer/booking/deposit` | FE `features/customer/booking-deposit/*`, route, i18n | |
| D25 / H-03 Gỡ trang mock Caregiver (dashboard, history, shift-detail, active-session, care-report mock) + code chết `caregiver/schedule`, `caregiver/profile` | FE `features/caregiver/*`, `entities/care-session/*`, routes, i18n | Trang chủ caregiver → `/caregiver/visits` |
| H-02 Bỏ nút Bắt đầu / Hoàn thành ở lịch caregiver | FE `entities/provider/provider-schedule-page.tsx` | Lịch = lịch rảnh (D11), chỉ hủy |
| H-03 Nhãn mẫu dashboard admin | FE `features/admin/dashboard/*` | |
| D26 Ẩn menu Doanh thu, Chính sách, Chất lượng | FE `admin-navigation.constants.ts` | Route giữ nguyên |
| L-01 Link chết | FE `patient-sidebar.tsx`, customer bell, caregiver bell | Thông báo thật làm ở Đợt 3 |
| L-02 Code chết customer caregivers cũ, `AdminCentersPage`, `ManagerCaregiversPage` | FE | |

Rủi ro: xóa i18n đang được trang khác dùng (typecheck bắt được vì key có kiểu); test cũ khóa hành vi mock → xóa cùng feature.
Không có migration.

### Kết quả (2026-10-28)

> Theo yêu cầu của chủ dự án (2026-10-28): **không commit / push** — mọi thay đổi để ở working tree; commit message đề
> xuất ở cuối mục. Các file FE bị xóa đã được `git rm` (nằm trong index, chưa commit).

**Đã đóng:** M-01, M-02, H-14, H-01, H-02, H-03, L-01 (phần link chết), L-02 (phần customer caregivers cũ), C-03 (phần hạ
tầng Docker), D25, D26.

BE (`BE/`):
- `BookingService/.../CareReportServiceImpl.java` — `canReadReport`: bản nháp chỉ caregiver viết nó đọc được; danh sách
  của khách / manager / admin chỉ chứa report `SUBMITTED` (+ bản nháp của chính caregiver). Test bổ sung trong
  `BookingApiTests` (khách nhận 404 / danh sách rỗng khi report còn nháp, thấy sau khi nộp).
- `ProviderService` — `GET /api/v1/caregivers` trả `PageResponse<CaregiverCardResponse>` (thẻ công khai, thêm `centerId`,
  `centerName`, `bio`; ảnh lấy ngoài transaction). Test `publicProfileShows…` kiểm không lộ `userId`, ngày sinh, lý do
  xác minh, `createdBy`. README cập nhật.
- `PaymentService` — `GlobalExceptionHandler` theo mẫu Booking: `AccessDeniedException` → 403, body/tham số/header sai → 400,
  khóa lạc quan / vi phạm unique → 409 `CONCURRENT_UPDATE` (6014), lỗi DB → 503 (6015), key validation lạ → `INVALID_KEY`
  (6016); bỏ `APIResponse.error(...)` (rule #5 error handling). Test API của Payment chưa có hạ tầng (H2) → bổ sung ở Đợt 1a.
- `compose.yaml` bật `PaymentService/compose.stack.yaml`; `start.bat` tạo volume `paymentservice_payment_postgres`, kiểm
  `.env` của 8 service, in URL Payment; `DOCKER.md` cập nhật. Không mở path Stripe ở gateway (D16).

FE (`FE-careconnect/`):
- Xóa trang cọc giả `/customer/booking/deposit` (feature, route, i18n, test).
- Portal Caregiver chỉ còn trang thật (D25): xóa dashboard, history, shift-detail, active-session, care-report mẫu,
  `entities/care-session`, feature chết `caregiver/schedule`, `caregiver/profile` + i18n; trang chủ `/caregiver` →
  `/caregiver/visits`; menu: Buổi được giao · Lịch rảnh · Ca làm việc · Hồ sơ; bỏ chuông giả có chấm đỏ.
- Lịch rảnh: bỏ nút Bắt đầu / Hoàn thành (BE chỉ cho hủy) — test mới kiểm chỉ còn hủy kèm version + lý do.
- Dashboard admin: biểu đồ mẫu nằm dưới ghi chú `role='note'` "Bản xem thử · Kỳ báo cáo…". Menu admin ẩn Doanh thu,
  Chất lượng, Chính sách (route giữ nguyên).
- Customer: bỏ chuông no-op; link "Bảo mật" → `/account/security`; bỏ link `/customer/notifications` (404). Xóa trang
  tìm caregiver mock cũ (`caregivers-page.tsx`, `mock-caregivers.ts`, component/hook đi kèm).
- Contract: `GET /v1/caregivers` → `ProviderCaregiverCardPageResponse`; `ProviderCaregiverCardRecord` thêm `centerId`,
  `centerName`, `bio`; `pnpm api:generate`; danh mục khách và form đặt lịch dùng `caregiverId`.
- `ARCHITECTURE.md` cập nhật mục Caregiver portal, Admin menu, dashboard.

**Kiểm thử:** `mvnw test` BookingService ✅, ProviderService ✅, PaymentService ✅ · FE `pnpm check` ✅ (prettier, lint,
typecheck, vitest 66 file / 244 test, `node --test` 11/11, build). Số test FE giảm từ 290 do xóa test của các trang mock.

**Kiểm tay:**
1. Đăng nhập caregiver → vào `/caregiver` thấy "Buổi được giao"; menu 4 mục; `/caregiver/dashboard` → trang 404.
2. Caregiver tạo report nháp cho một phiên → khách của booking gọi `GET /api/v1/care-reports/{id}` nhận 404; nộp xong → 200.
3. Khách `GET /api/v1/caregivers?centerId=…` → item không có `userId`, `dateOfBirth`.
4. Khách gọi `GET /api/v1/payments/revenue` (ADMIN-only) → 403 thay vì 500.
5. `start.bat` → `payment-service` healthy (cần `PaymentService/.env` + client `payment` trong `IDENTITY_SERVICE_CLIENTS`).
6. Admin dashboard: ghi chú "Bản xem thử" hiện trên biểu đồ; menu không còn Doanh thu / Chất lượng / Chính sách.

**Còn treo / ghi chú:**
- `AdminCentersPage`, `ManagerCaregiversPage` (code chết trong `*-live-page.tsx`) đan với trang đang dùng → dọn ở Đợt 6.
- Trang care report mẫu của khách `/customer/bookings/:code/report` giữ lại, thay bằng luồng thật ở Đợt 3.
- **Cần quyết định:** sidebar hồ sơ người bệnh hiển thị "Đường dây nóng y tế 24/7" với số `1900 6868` (`tel:19006868`) — số
  minh họa nhưng trông như thật; người dùng có thể gọi nhầm khi khẩn cấp. Đề xuất gỡ hoặc thay bằng số thật của dự án.

**Commit đề xuất:**
- BE: `fix(aftercare): hide draft care reports and expose public caregiver cards` · `fix(payment): map access denied and bad requests to 4xx; add payment-service to docker stack`
- FE: `refactor(caregiver): remove sample-only caregiver screens and fake deposit page` · `fix: label admin dashboard samples, hide preview-only admin menus, remove dead links`

---

## Đợt 1a — Payment: hợp đồng Booking đang gọi (CHƯA BẮT ĐẦU — điểm tiếp tục cho phiên sau)

Đọc trước khi code (phía Booking là hợp đồng, chỉ đổi khi bắt buộc):
- `BookingService/.../client/PaymentServiceClient.java`, `PaymentClient.java`, `PaymentServiceClientConfig.java`
  (404/4xx → `REMOTE_COMMAND_REJECTED` → outbox FAILED ngay), `client/dto/*` (RefundQuoteRequest/Response,
  RefundCreateRequest, RemainingInvoiceRequest, ComplaintRefundRequest, PaymentPageResponse, PaymentSummaryResponse,
  PaymentPolicyResponse, OutstandingDebtResponse).
- Nơi Booking tạo payload: `BookingMapper.toRefundQuoteRequest`, `BookingLifecycleServiceImpl` (REFUND, REMAINING_INVOICE),
  `ComplaintServiceImpl` (COMPLAINT_REFUND, `findRefundableAmount`), `BookingOutboxServiceImpl.send` (header `Idempotency-Key`).
- Bảng phí: `docs/GAP_ANALYSIS.md` §1.2; quy tắc: `docs/WORK_PLAN_A_PAYMENT.md` PAY-02…PAY-12, PAY-16; D15–D17 và yêu cầu
  hoàn tiền ở `docs/2026-10-28-FULLSTACK_GAP_ANALYSIS.md` §8.1.

Kế hoạch dự kiến:
1. Hạ tầng test Payment (H2 + security test, stub Booking bằng `HttpServer` như BookingService) + test 403/400 cho H-14.
2. Chuẩn hóa quy ước (L-09): package `service/Impl`, import tường minh, bỏ `@Operation`, `@Tag` trùng, controller mỏng
   (IP client xuống service), MapStruct cho entity/response, `@Repository`, `findForUpdate`, `IgnoreServerManagedFields`.
3. Schema additive `schema/changes/2026-10-xx-payment-ledger.sql` + cập nhật `schema/payment.sql`: `platform_policies`
   (seed GLOBAL 30 %), `booking_financials`, `occurrence_financials`, `refunds`, `remaining_invoices`, `payment_transactions`
   (append-only), cột `payment_type` / `refunded_amount` trên `payments` (bỏ unique `booking_id` → unique theo
   (booking, type) — **cần kiểm là thay đổi additive được hay phải hỏi chủ dự án vì gỡ ràng buộc cũ**).
4. API: `GET /payment-policies/effective` (từ bảng), `POST /refund-quotes`, `POST /refunds` + dispatcher
   (`PAYMENT_REFUND_MODE=SIMULATED|VNPAY`), `POST /refunds/complaints`, `POST /remaining-invoices` + job OVERDUE,
   `GET /payments/me/outstanding`, `POST /remaining-invoices/{id}/payments`, `GET /payments` (khách / manager / admin),
   `deposit-confirmations` (sửa cả Booking, ghi lý do). Stripe sau cờ, mặc định tắt (D16). Mặc định cọc 30 % (D17).
5. Test hợp đồng hai đầu: JSON mẫu dùng chung cho test Booking (client) và Payment (controller).
