# Tiến độ làm lại luồng Booking theo đặc tả v1.0 (cập nhật 2026-11-03)

> Nhiệm vụ: rà soát và sửa luồng booking theo `CareConnect_Booking_Business_Specification.docx` v1.0.
> Báo cáo chi tiết từng đợt: `2026-11-02-BOOKING_FLOW_REVIEW.md` (gọi tắt **REVIEW**) — §1–§7 rà soát và quyết định,
> §8 đợt 1–3, §9 đợt 4, §10 đợt 5.
>
> **Trạng thái (2026-11-03): đã làm xong X1 và đợt 1–6.** Tài liệu luồng sau khi sửa: `2026-11-03-BOOKING_FLOW_V1.md`.
> Phần còn lại cần chủ dự án (áp change script, commit, trả lời Q11 / Q12, duyệt G) hoặc cần stack chạy (E2E).
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
| 9 | Đợt 6 — tài liệu: luồng theo vai trò, Mermaid state / sequence, API contract, policy, mã lỗi, edge case, 14 kịch bản test thủ công, tồn đọng | Xong | `2026-11-03-BOOKING_FLOW_V1.md`, REVIEW §11 |
| 10 | Trung tâm ARCHIVED (X2): khôi phục về `INACTIVE` — làm ngoài phạm vi booking | Xong | D31 trong `IMPLEMENTATION_PROGRESS.md` |
| 11 | Q11 giờ lẻ (90 phút = 1,5 giờ) + Q12 cửa sổ check-in theo center policy (BE, FE, mobile) | Xong | REVIEW §12; change script `2026-11-03_booking_spec_v1_q11_q12.sql` |

### Lỗi phát hiện khi chủ dự án chạy thử (2026-11-03) — đã sửa, chưa commit

| Lỗi | Nguyên nhân | Sửa |
|---|---|---|
| Form đặt lịch báo "Không tải được người chăm sóc của trung tâm" ngay khi chọn dịch vụ | FE gọi `GET /caregivers/search` với `size=100`, Provider chỉ nhận tối đa 50 → 400 (log gateway). Test FE mock API nên không bắt được | `use-booking-providers.ts`: `size=50`, đọc đủ các trang (tối đa 10 = 500 ứng viên của BE); test mới cho nhiều trang |
| `GET /provider-media/CAREGIVER_PROFILE/{id}` trả 500 (`LazyInitializationException`), ảnh người chăm sóc không hiện | `ProviderMediaServiceImpl.list` / upload / delete chạy ngoài transaction (có gọi MediaService) nhưng đọc `caregiver.center` lazy khi kiểm quyền (khách và manager) | `CaregiverProfileRepository.findWithCenterById`, `CertificateRepository.findWithCaregiverById` (join fetch); test tích hợp mới. **Cần build lại provider-service** |

### Kiểm thử lần chạy gần nhất

| Phần | Kết quả |
|---|---|
| BookingService | H2: 118 test, 0 lỗi, 2 skip. PostgreSQL: 117 / 118 — lỗi có sẵn từ trước (G14) |
| PaymentService | 48 / 48 (H2 và PostgreSQL) |
| ProviderService | 71 / 71 (sau D31 và sửa media) |
| ApiGateway | 20 / 20 (đợt 1; sau đó không đổi) |
| FE | typecheck, lint, build pass; 84 file / 350 test pass |
| Mobile | 101 / 101 (sau Q12) |
| Change script | Schema cũ + change script **khớp** schema tạo mới (Booking, Payment); script Payment chạy lại được |

---

## 2. Chưa làm / đang chờ

| # | Việc | Ai làm | Ghi chú |
|---|---|---|---|
| 1 | Kiểm trên trình duyệt thật (end-to-end) theo 14 kịch bản ở `2026-11-03-BOOKING_FLOW_V1.md` §7 | Tôi, sau mục 2 | Stack chưa chạy trên máy |
| 2 | Áp change script cho DB dev (hoặc reset DB): Booking `2026-11-02_booking_spec_v1.sql` rồi `2026-11-02_booking_spec_v1_payment.sql`; Payment `2026-11-02-payment-spec-v1.sql`; Booking `2026-11-03_booking_spec_v1_q11_q12.sql` (Q11 / Q12). Build lại booking-service, payment-service, provider-service, api-gateway | Chủ dự án | REVIEW §8.3, §9.4, §12 |
| 3 | Commit từng repo — BE: issuer, đợt 1–3, đợt 4; FE: đợt 1–3, đợt 5, D31; mobile: đợt 1; BE: tài liệu đợt 6 + D31 | Chủ dự án | Message đề xuất ở REVIEW §8.6, §9.7, §10.5 |
| 4 | Duyệt / bỏ các đề xuất G1–G11, G13–G25 (G12 đã làm qua X1) | Chủ dự án | REVIEW §6, §7.7, §8.5, §9.6, §10.4 |
| 5 | Hỏi người viết spec: thứ tự chọn caregiver ↔ dịch vụ (Q10), admin hủy booking (Q9), mất cọc khi quá hạn thanh toán (M3) | Chủ dự án | REVIEW §7.8 |

### Tồn đọng đã biết (chưa xử lý, không chặn)

| # | Nội dung | Chi tiết |
|---|---|---|
| T6 | Lịch tháng chỉ vẽ booking CUSTOM ở ngày đầu (G15) | REVIEW §8.4 |
| T10 | Hóa đơn bị giảm trong lúc khách đang mở phiên trả với số tiền cũ → phần trả dư không tự hoàn (G19) | REVIEW §9.5 |
| T11 | Thứ tự khóa khác nhau giữa lệnh hoàn và IPN → có thể xảy ra deadlock hiếm gặp (G20) | REVIEW §9.5 |
| T12, T15 | Cột / API `payment-policies` không còn dùng để tính tiền (G21) | REVIEW §9.5, §10.3 |
| T13 | `/admin/revenue` vẫn là mock (G25) | REVIEW §10.3 |
| T14 | Nút trả phần còn lại tìm hóa đơn trong 100 hóa đơn đang chờ đầu tiên của khách (G24) | REVIEW §10.3 |
