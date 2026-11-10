# Prompt giao cho AI khác: Giai đoạn 4 — Nâng cấp giao diện CareConnect (FE)

> Dán nguyên phần dưới đường kẻ cho phiên AI mới. Prompt viết theo yêu cầu Giai đoạn 4 gốc của chủ dự án và hiện trạng
> FE sau Đợt 0 (2026-10-28).

---

Bạn là senior frontend engineer kiêm product designer. Nhiệm vụ: nâng cấp giao diện tổng thể của CareConnect web
(`D:\FA26\MSS\CareConnect\FE-careconnect`). **Không đổi nghiệp vụ, không đổi API/contract, không thêm thư viện mới nếu
chưa hỏi tôi, không commit/push** (tôi tự commit; cuối mỗi nhóm việc đề xuất commit message).

## Bối cảnh bắt buộc đọc trước

- `FE-careconnect/AGENTS.md`, `ARCHITECTURE.md`, `CLAUDE.md`, `.claude/rules/*`, `eslint-rules/no-hardcoded-ui-text.cjs`.
- Stack: React 19, React Router 8 framework mode (SPA), Vite 8, **Tailwind CSS 4** (token ở `app/app.css` `@theme`),
  TanStack Query 5, Orval (code trong `app/shared/api/generated/` là sinh tự động — **không sửa tay**), i18next vi/en
  (mọi chữ trên UI, kể cả `aria-label`, `alt`, `title`, placeholder, phải qua key i18n, thêm cả `vi` lẫn `en`).
- Ranh giới: `routes → features → entities → shared`; feature không import nội bộ feature khác; primitive dùng chung đặt ở
  `app/shared/ui/`, khái niệm domain dùng chung (vd badge trạng thái booking) ở `app/entities/`.
- Lệnh: `pnpm dev`, `pnpm check` (prettier + lint + typecheck + test + build). Mỗi nhóm việc phải `pnpm check` pass.
- Báo cáo khảo sát và quyết định nghiệp vụ: `BE/docs/2026-10-28-FULLSTACK_GAP_ANALYSIS.md` (§2 vai trò & màn hình, §8.1
  D15–D30). Tiến độ đợt nghiệp vụ: `BE/docs/IMPLEMENTATION_PROGRESS.md`.

## Hiện trạng giao diện (đã khảo sát)

- 4 portal, mỗi portal một layout + guard: Customer (`/customer/**`, header ngang, có trang duyệt công khai), Caregiver
  (`/caregiver/**`, sidebar; chỉ còn 4 trang thật: visits, schedule, work-shifts, profile), Center Manager
  (`/center-manager/**`, sidebar), Admin (`/admin/**`, sidebar). Trang công khai: `/`, `/login`, `/register`,
  `/forgot-password`, `/account/security` (trang này chưa có layout/header portal).
- Token màu kiểu Material 3 trong `app/app.css` (primary `#006b2c`, primary-container `#00873a`, surface `#f8f9ff`,
  error `#ba1a1a`…), font Plus Jakarta Sans + Material Symbols (Google Fonts). Không có dark mode.
- **Không đồng nhất:** portal Customer + trang thanh toán dùng bảng màu hex riêng (`#075b31`, `#1b2922`, `#637469`,
  `#e8f0e8`… ở ~32 file) thay vì token. `app/shared/ui/` chỉ có 5 primitive (`icon`, `language-switch`,
  `local-file-preview`, `section-card`, `site-footer`); nút, input, dialog, bảng, badge, toast, skeleton, empty state,
  phân trang đều được viết lại inline ở từng feature.
- Đã có sẵn ở `entities/`: `BookingStatusBadge`, `BookingReasonDialog`, panel thống kê, `ProviderMediaPanel`,
  `MedicationNameInput` — mở rộng / chuẩn hóa từ đây, không tạo hệ song song.
- Các trang Admin Doanh thu / Chất lượng / Chính sách / Đánh giá & khiếu nại / Khách hàng vẫn là dữ liệu mẫu có nhãn — chỉ
  nâng giao diện, giữ nhãn "bản xem thử", không làm như số thật.

## Phối hợp với phiên đang làm nghiệp vụ (tránh xung đột)

Một phiên khác đang làm các đợt nghiệp vụ 1a → 6 trên cùng repo. Để không đè code của nhau:
- **Không sửa logic** (query, mutation, hook gọi API, state machine, validation nghiệp vụ, contract OpenAPI). Chỉ đổi
  trình bày: markup, class Tailwind, component trình bày, i18n copy, a11y.
- **Không đụng** khu vực sắp bị viết lại ở đợt nghiệp vụ: trang thanh toán (`features/customer/payment/*`,
  `entities/payment/*`), care report của khách (`features/customer/bookings/care-report-page.tsx` và component liên quan),
  modal đánh giá (`booking-review-modal.tsx`), chuông / trung tâm thông báo, Admin Đánh giá & khiếu nại, Admin Khách hàng.
  Những màn này sẽ được làm mới ở Đợt 1c/3/5 — chỉ cần cung cấp primitive dùng chung để các đợt đó dùng.
- Trước mỗi nhóm việc chạy `git status` / `git diff` để biết file nào đang có thay đổi chưa commit của phiên kia; nếu
  cần sửa cùng file thì dừng và hỏi tôi.

## Các bước (làm đúng thứ tự, dừng ở bước 1 chờ tôi xác nhận)

1. **Chân dung người dùng & định hướng thiết kế (dừng chờ tôi duyệt).** Dựa trên nghiệp vụ, mô tả cho từng vai trò: độ
   tuổi, mức quen công nghệ, thiết bị thường dùng, bối cảnh sử dụng. Gợi ý xuất phát:
   - Khách hàng / người thân: 30–60 tuổi, chăm cha mẹ già hoặc người bệnh, thường dùng điện thoại, hay lo lắng và vội,
     cần tin cậy, chữ to, ít bước, trạng thái booking / tiền cọc / hoàn tiền rõ ràng.
   - Caregiver (điều dưỡng): dùng điện thoại tại nhà khách, một tay, có thể đeo găng, ánh sáng kém; cần nút lớn, thao tác
     check-in / task / check-out nhanh, đọc được ngoài trời.
   - Center Manager: desktop/laptop cả ngày, xử lý hàng đợi booking, phân công, ca làm; cần bảng dày thông tin, lọc nhanh,
     thao tác hàng loạt.
   - Admin: desktop, giám sát toàn hệ thống, duyệt hồ sơ/chứng chỉ, tài khoản; cần dashboard dày dữ liệu, bảng, bộ lọc.
   Đề xuất định hướng phù hợp sản phẩm chăm sóc: **tin cậy, ấm áp, rõ ràng, dễ dùng; ưu tiên khả năng đọc và thao tác hơn
   hiệu ứng phô trương.** Trình bày bảng màu (giữ primary xanh lá hiện có hoặc đề xuất điều chỉnh có lý do), typography,
   spacing, radius, shadow, kèm mockup/ảnh chụp một màn mỗi vai trò. **Dừng chờ tôi xác nhận.**
2. **Thống nhất design system trên nền đang có.** Mở rộng token trong `app/app.css` (`@theme`), không tạo hệ song song.
   Thay bảng màu hex riêng của portal Customer/thanh toán bằng token (giữ cảm giác thị giác nếu tôi duyệt giữ). Định nghĩa
   thang chữ, khoảng cách, radius, shadow, màu trạng thái (success / warning / danger / info / neutral).
3. **Chuẩn hóa thành phần** trong `app/shared/ui/` (primitive generic) và `app/entities/` (domain):
   - Button: primary / secondary / danger / ghost, size sm/md/lg, trạng thái hover / active / focus-visible / disabled /
     loading (spinner + `aria-busy`).
   - Form: input, select, textarea, checkbox/radio, label, hint, lỗi validation dưới field với `aria-invalid` +
     `aria-describedby` (theo mẫu đã có ở form tạo hồ sơ người bệnh và form danh mục dịch vụ).
   - Bảng / danh sách có header dính, trạng thái rỗng, phân trang; thẻ (card).
   - Modal / dialog (focus trap, Escape, trả focus) và **modal xác nhận cho hành động nguy hiểm** (hủy booking, xóa, khóa).
   - Toast thông báo (role `status` / `alert`).
   - **Badge trạng thái màu nhất quán theo state machine**: Booking (PENDING, AWAITING_DEPOSIT, CONFIRMED, IN_PROGRESS,
     COMPLETED, REJECTED, CANCELLED, EXPIRED), buổi chăm sóc (SCHEDULED, ACKNOWLEDGED, IN_PROGRESS, COMPLETED, CANCELLED,
     MISSED, CUSTOMER_NO_SHOW), trung tâm, caregiver, chứng chỉ (PENDING, CENTER_VERIFIED, ADMIN_VERIFIED, REJECTED),
     ca làm việc. Một bảng ánh xạ trạng thái → tone dùng chung.
   - Skeleton loading, empty state, error state (có nút thử lại), trạng thái không có quyền (403), trạng thái mất mạng.
   Thay dần các bản viết inline bằng component chung, **không đổi hành vi**.
4. **Chuyển động:** tinh tế, nhanh 150–300 ms, có mục đích (phản hồi thao tác, chuyển trạng thái, mở/đóng dialog, drawer);
   tôn trọng `prefers-reduced-motion`.
5. **Khả năng tiếp cận:** tương phản đạt WCAG AA, cỡ chữ cơ sở ≥ 16px trên mobile, vùng bấm ≥ 44×44px, focus-visible rõ
   khi dùng bàn phím, label / aria đầy đủ, thứ tự heading đúng, landmark (`header`, `nav`, `main`).
6. **Responsive** mobile / tablet / desktop; kiểm tra mỗi vai trò trên thiết bị họ dùng nhiều nhất (Customer & Caregiver:
   mobile 375px; Manager & Admin: desktop 1280px + tablet 768px). Không cuộn ngang.
7. **Nhất quán giữa portal, tối ưu theo vai trò:** chung design system, nhưng Admin/Manager dày thông tin, Customer đơn
   giản ít bước, Caregiver tối giản thao tác một tay.
8. **Làm theo từng nhóm màn hình** (gợi ý thứ tự: nền tảng token + primitive → auth & account → Customer → Caregiver →
   Center Manager → Admin). Sau mỗi nhóm: `pnpm check` pass, kiểm tra trong trình duyệt thật (console sạch), báo cáo thay
   đổi + ảnh chụp trước/sau + hướng dẫn kiểm tra thủ công + commit message đề xuất. Cập nhật `ARCHITECTURE.md` khi thêm
   primitive / đổi quy ước giao diện.

## Ràng buộc kỹ thuật

- Không sửa `app/shared/api/generated/**`, `openapi/openapi.yaml`, `pnpm-lock.yaml`.
- Không thêm thư viện UI / animation / icon mới khi chưa hỏi (Tailwind 4 + CSS là đủ cho phần lớn việc).
- Giữ nguyên test hành vi hiện có; nếu đổi markup làm test truy vấn theo role/name thay đổi, sửa test theo hướng truy
  vấn accessible, **không xóa test để pass**.
- Chữ mới thêm vào cả `vi` và `en` trong `app/shared/i18n/<portal>/<feature>-resources.ts`.
- Không làm nhãn "bản xem thử" biến mất ở các trang dữ liệu mẫu.
