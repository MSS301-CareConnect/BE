# Tiến độ làm lại luồng Booking theo đặc tả v1.0 (cập nhật 2026-11-03)

> Nhiệm vụ: rà soát và sửa luồng booking theo `CareConnect_Booking_Business_Specification.docx` v1.0.
> Báo cáo chi tiết từng đợt: `2026-11-02-BOOKING_FLOW_REVIEW.md` (gọi tắt **REVIEW**) — §1–§7 rà soát và quyết định,
> §8 đợt 1–3, §9 đợt 4, §10 đợt 5.
>
> **Trạng thái: ĐANG DỪNG theo yêu cầu của chủ dự án (2026-11-03).** Đã làm xong X1 và đợt 1–5; đợt 6 (tài liệu) chưa làm.
> Mọi thay đổi ở 3 repo (BE, FE-careconnect, careconnect-mobile) **chưa commit / push**.

---

## 1. Đã làm

| # | Hạng mục | Kết quả | Chi tiết |
|---|---|---|---|
| 1 | Rà soát spec ↔ code, bảng vấn đề B1–B20, kế hoạch, câu hỏi Q0–Q10 | Xong | REVIEW §1–§6 |
| 2 | Chốt quyết định vòng 2–3 (Q1–Q10, M1–M7, X1–X5) | Xong | REVIEW §7 |
| 3 | X1 — sửa cấu hình issuer PaymentService (nguyên nhân lỗi thanh toán Q0.1) | Xong; `.env` do chủ dự án tự đặt | REVIEW §7.2, §8.1 |
| 4 | Đợt 1 — BookingService lõi: trạng thái theo spec, SHIFT / RECURRING / CUSTOM, center booking policy, migration; kèm lớp tương thích cho Payment / Provider / FE / mobile | Xong | REVIEW §8 |
| 5 | Đợt 2 — ProviderService: lịch ca cho SHIFT, kiểm trạng thái caregiver / trung tâm | Xong | REVIEW §8 |
| 6 | Đợt 3 — API `held-schedules` + form đặt lịch FE | Xong | REVIEW §8 |
| 7 | Đợt 4 — PaymentService: thanh toán đầu (SHIFT 100 %, RECURRING / CUSTOM theo % cọc), phần còn lại trước buổi đầu, quá hạn → EXPIRED + giữ cọc, hoàn 100 / 70, doanh thu cho manager; kèm phần BookingService (`fully_paid_at`, chặn check-in, nhắc trả) | Xong | REVIEW §9 |
| 8 | Đợt 5 — FE: thanh toán đầu / phần còn lại, khung hoàn tiền theo center policy, form chính sách cho manager / admin, hủy từng buổi, doanh thu trung tâm | Xong | REVIEW §10 |

### Kiểm thử lần chạy gần nhất

| Phần | Kết quả |
|---|---|
| BookingService | H2: 116 test, 0 lỗi, 2 skip. PostgreSQL: 115 / 116 — lỗi có sẵn từ trước (G14) |
| PaymentService | 48 / 48 (H2 và PostgreSQL) |
| ProviderService | 66 / 66 (đợt 2; đợt 4–5 không đổi) |
| ApiGateway | 20 / 20 (đợt 1; sau đó không đổi) |
| FE | typecheck, lint, build pass; 84 file / 350 test pass |
| Mobile | 101 / 101 (đợt 1; sau đó không đổi) |
| Change script | Schema cũ + change script **khớp** schema tạo mới (Booking, Payment); script Payment chạy lại được |

---

## 2. Chưa làm / đang chờ

| # | Việc | Ai làm | Ghi chú |
|---|---|---|---|
| 1 | **Đợt 6 — tài liệu Giai đoạn 5**: luồng chi tiết theo từng vai trò; Mermaid `stateDiagram-v2` + bảng chuyển trạng thái; sơ đồ sequence; API contract; edge case; hướng dẫn test thủ công; tồn đọng tổng hợp. Danh sách thay đổi đã có ở REVIEW §8.1, §9.2, §10.1 | Tôi, khi được duyệt | Đang dừng theo yêu cầu |
| 2 | Kiểm trên trình duyệt thật (end-to-end) | Tôi, sau mục 3 | Stack chưa chạy trên máy |
| 3 | Áp change script cho DB dev (hoặc reset DB): Booking `2026-11-02_booking_spec_v1.sql` rồi `2026-11-02_booking_spec_v1_payment.sql`; Payment `2026-11-02-payment-spec-v1.sql`. Build lại booking-service, payment-service, provider-service, api-gateway | Chủ dự án | REVIEW §8.3, §9.4 |
| 4 | Commit từng repo — BE: issuer, đợt 1–3, đợt 4; FE: đợt 1–3, đợt 5; mobile: đợt 1 | Chủ dự án | Message đề xuất ở REVIEW §8.6, §9.7, §10.5 |
| 5 | Trả lời **Q11** (làm tròn giờ: 90 phút = 1,5 giờ theo spec §7.2 hay 2 giờ như code hiện tại) và **Q12** (đợt nào áp cửa sổ check-in của policy) | Chủ dự án | REVIEW §8.5 |
| 6 | Duyệt / bỏ các đề xuất G1–G11, G13–G25 (G12 đã làm qua X1) | Chủ dự án | REVIEW §6, §7.7, §8.5, §9.6, §10.4 |
| 7 | Hỏi người viết spec: thứ tự chọn caregiver ↔ dịch vụ (Q10), admin hủy booking (Q9), mất cọc khi quá hạn thanh toán (M3) | Chủ dự án | REVIEW §7.8 |
| 8 | Trung tâm ARCHIVED (X2) | Backlog | Ngoài phạm vi booking |

### Tồn đọng đã biết (chưa xử lý, không chặn)

| # | Nội dung | Chi tiết |
|---|---|---|
| T3 | Cửa sổ check-in theo policy chưa được áp dụng (chờ Q12) | REVIEW §8.4 |
| T6 | Lịch tháng chỉ vẽ booking CUSTOM ở ngày đầu (G15) | REVIEW §8.4 |
| T10 | Hóa đơn bị giảm trong lúc khách đang mở phiên trả với số tiền cũ → phần trả dư không tự hoàn (G19) | REVIEW §9.5 |
| T11 | Thứ tự khóa khác nhau giữa lệnh hoàn và IPN → có thể xảy ra deadlock hiếm gặp (G20) | REVIEW §9.5 |
| T12, T15 | Cột / API `payment-policies` không còn dùng để tính tiền (G21) | REVIEW §9.5, §10.3 |
| T13 | `/admin/revenue` vẫn là mock (G25) | REVIEW §10.3 |
| T14 | Nút trả phần còn lại tìm hóa đơn trong 100 hóa đơn đang chờ đầu tiên của khách (G24) | REVIEW §10.3 |
