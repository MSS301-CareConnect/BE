# WORK PLAN — CareConnect Backend (3 người) — phần chung

> Ngày lập: **2026-10-21** · Căn cứ: `docs/GAP_ANALYSIS.md` (khảo sát code nhánh `dev` @ `1609b4f`) và các quyết định đã chốt ở GAP_ANALYSIS §1.
> Task chi tiết tách theo người: `docs/WORK_PLAN_A_PAYMENT.md`, `docs/WORK_PLAN_B_BOOKING.md`, `docs/WORK_PLAN_C_AFTERCARE.md`.
> Mọi task phải tuân thủ `AGENTS.md` (aggregate, `/api/v1/<resource>`, `APIResponse<T>`, MapStruct, `AppException` + `ErrorCode`, SQL áp tay + `schema/changes/`, không I/O mạng trong transaction, test theo rule #30) và cập nhật tài liệu cùng thay đổi (rule #31).

---

## 1. Nguyên tắc chia việc

1. **Chia theo module dọc.** Mỗi người sở hữu trọn module, từ schema SQL, entity, service, API, test đến tài liệu, để giảm đụng code. Một file chỉ có **một chủ**. Chỉ được sửa file của module khác khi task ghi rõ, và phải nhờ chủ module review.
2. **Hợp đồng trước, cài đặt sau.** Mọi lời gọi chéo module đi qua contract ở §7. Tuần đầu, mỗi bên publish DTO và path (có thể là stub trả dữ liệu giả) để hai bên làm song song.
3. **Cân bằng khối lượng.** Story point dùng thang Fibonacci (1, 2, 3, 5, 8). Mặc định ba người lệch nhau tối đa khoảng 10%. **Ngoại lệ đã chốt (2026-10-21):** [Người B] là người xử lý chính luồng booking nên nặng nhất, [Người C] nhẹ hơn — xem §2.
   - Giả định vận tốc ≈ **15 SP / người / sprint**, 1 sprint = 1 tuần (5 ngày làm việc), tức ≈ 3 SP mỗi ngày.
4. **Lỗi bảo mật là P0** và do chủ module tương ứng sửa trong sprint 1. Riêng SEC-01 (`deposit-paid`) thuộc gói Payment.
5. **Ưu tiên:**
   - P0: chặn luồng chính hoặc lỗ hổng bảo mật.
   - P1: nghiệp vụ cốt lõi của đề tài.
   - P2: hoàn thiện, thống kê, tối ưu.
6. **Định nghĩa hoàn thành (DoD) cho mọi task:**
   - `mvnw clean verify` xanh (test trên H2; test đụng lock hoặc SQL đặc thù thì chạy thêm với `-D<service>.testing.postgres=true`).
   - Đã cập nhật `schema/<service>.sql` và có change script `schema/changes/2026-MM-DD_<tên>.sql` khi đổi schema.
   - Đã cập nhật README / AGENTS của service; đánh dấu `[x]` kèm ngày trong `docs/PLAN.md`.
   - Có test phân quyền cho mọi endpoint mới, tối thiểu 3 ca: 401, 403 hoặc 404 khi ngoài phạm vi, và ca thành công.

---

## 2. Bảng tổng

| Người | Module phụ trách | Mã task | Tổng SP | Ước lượng |
|---|---|---|---|---|
| **[Người A]** | **Gói Payment & tài chính**: PaymentService mới; PlatformPolicy; cọc VNPay; IPN; hết hạn phiên; thay `deposit-paid`; tính phí hủy / hoàn tiền; REMAINING và nợ; hoa hồng / ledger; đối soát; doanh thu; lịch sử giao dịch | PAY-01 → PAY-17 | **91** | ≈ 30 ngày (6 sprint) |
| **[Người B]** | **Luồng booking end-to-end (người xử lý chính)**: danh mục / giá; kỹ năng và lịch rảnh caregiver (Provider); tìm caregiver và trung tâm; báo giá; tạo booking; duyệt / từ chối / phân công / đổi người; caregiver nhận ca, check-in, check-out, hoàn tất booking (FINALIZE), MISSED / khách vắng; hủy booking hoặc từng buổi + yêu cầu hoàn tiền; lịch sử booking; token service (Identity); Gateway; tài liệu chung và e2e | IAM-01, GW-01, CAT-01, CAT-02, PRV-01, PRV-02, PRV-03, SRCH-01, SRCH-02, BK-01 → BK-09, OPS-01, OPS-02, OPS-04, OPS-06, INF-01 | **104** | ≈ 35 ngày (6 sprint, ≈ 17 SP/sprint) |
| **[Người C]** | **Hậu mãi, chất lượng & bảo mật**: checklist / ghi chú trong ca, care report; thông báo + SSE; review; khiếu nại; vòng đời trung tâm và xác minh caregiver (Provider); CareRecipient; Media; Identity (bảo mật phiên, tra cứu user, quản lý user, khóa theo trung tâm); thống kê vận hành | IAM-02 → IAM-06, CR-01, MED-01, PRV-04, PRV-05, PRV-06, OPS-03, OPS-05, NTF-01, NTF-02, FB-01, FB-02, STAT-01 | **74** | ≈ 25 ngày (6 sprint, ≈ 12 SP/sprint) |
| **Tổng** | | 57 task | **269** | 6 sprint |

**Phân bổ có chủ đích** (yêu cầu 2026-10-21):
- [Người B] xử lý chính luồng booking nên nặng nhất: 104 SP ≈ 17 SP/sprint, vượt vận tốc giả định khoảng 15%.
- [Người C] nhẹ nhất: 74 SP ≈ 12 SP/sprint. C cũng là người **dự phòng cho B**: nếu cuối S4 B còn > 40 SP, C nhận BK-09 (P2) và INF-01 (tổng 6 SP, đều không nằm trên đường găng).
- [Người A] ở giữa: 91 SP.

Mỗi file cá nhân mở đầu bằng mục **"Tổng quan"**: người đó làm tính năng gì, cho role nào (CUSTOMER / CAREGIVER / CENTER_MANAGER / ADMIN / hệ thống).

**Quyền sở hữu file dùng chung:**

| Phần dùng chung | Chủ sở hữu |
|---|---|
| `BookingController`, `BookingOccurrenceController` (tách ở BK-08), `CareSessionController` (OPS-02/04) | [Người B] |
| Controller mới `CareReportController`, `FeedbackController`, `ComplaintController`, `NotificationController`, `StatisticsController` | [Người C]. C thêm endpoint task / ghi chú (OPS-03) vào `CareSessionController` qua review của B |
| `BookingAction` / `BookingServiceImpl` (state machine Booking), `BookingLifecycle.finalizeIfDone`, `booking_outbox` + dispatcher | [Người B] |
| `schema/booking.sql` | [Người B] gộp; [Người C] gửi change script riêng cho bảng của mình |
| `IdentityService` | [Người B]: IAM-01 (token service); [Người C]: IAM-02 → IAM-06 |
| `ProviderService` | [Người B]: kỹ năng, lịch rảnh / ngày nghỉ, ca làm việc, tìm kiếm, trang trung tâm công khai (PRV-01/02/03, SRCH-01/02); [Người C]: vòng đời trung tâm, xác minh, phạm vi / quy ước, rating, media (PRV-04/05/06, FB-01, MED-01) |
| `CareCatalogService`, `ApiGateway` | [Người B] |
| `CareRecipientService`, `MediaService` | [Người C] |
| `PaymentService` | [Người A] |

---

## 3. Quy ước dùng chung cho kế hoạch

### 3.1 Dải ErrorCode mới (kiểm trùng trước khi thêm)

| Service | Dải mới cấp cho kế hoạch |
|---|---|
| Identity | 1114–1149 (service client, khóa theo trung tâm) |
| CareRecipient | 2700–2749 (truy cập cho service / manager) |
| Catalog | 3110–3149, 3206–3249, 3305–3349 |
| Provider | 4119–4149 (center), 4207–4249 (caregiver, kỹ năng), 4253–4299 (chứng chỉ), 4405–4449 (lịch rảnh, ngày nghỉ), 4700–4749 (tích hợp Booking/Catalog) |
| Booking | 5112–5149 (booking), 5150–5199 (hủy / tích hợp Payment), 5220–5299 (buổi), 5409–5449 (phân công), 5800–5899 (session / task / report / no-show), 5900–5999 (review, complaint, notification) |
| **Payment (mới)** | 6000–6099 chung · 6100 policy · 6200 payment · 6300 VNPay · 6400 refund · 6500 remaining / nợ · 6600 settlement · 6700 báo cáo |
| **Media** | Chuyển toàn bộ sang **7000–7999** (MED-01) |

### 3.2 Token service (dùng ở mọi contract `hasRole('SERVICE')`)

- Mỗi service gọi `POST /api/v1/auth/service-token` (IAM-01), cache token tới `exp − 60s`.
- Feign client cần quyền service dùng `ServiceTokenInterceptor`; client chạy dưới quyền người dùng vẫn dùng `FeignAuthForwardingInterceptor`.
- Token service: `sub` = UUID cố định của client, `roles=["SERVICE"]`, claim `client_id`, `aud` = `careconnect-api`, `token_type=access`.

### 3.3 Tiền tệ

- VND, cột `numeric(14,2)` nhưng giá trị luôn làm tròn đến **đồng** (`setScale(0, HALF_UP)`).
- Gửi VNPay `vnp_Amount = amount × 100`.

### 3.4 Thời gian

- Lưu `timestamptz` (UTC); quy đổi theo `Asia/Ho_Chi_Minh` khi tính ngày.
- Mọi "bây giờ" lấy từ bean `Clock` để test cố định được.

### 3.5 Mẫu một task

Mỗi task ghi đủ các mục sau:

- người phụ trách, ưu tiên, SP, sprint, phụ thuộc;
- role;
- API;
- request / response và validation;
- quy tắc nghiệp vụ;
- trạng thái và side effect;
- DB;
- lỗi / edge case (dạng `KEY (mã, HTTP)`);
- tiêu chí nghiệm thu (Given/When/Then).

---

## 4. GÓI PAYMENT — [Người A]

Chi tiết 17 task PAY-01 → PAY-17 (91 SP): **`docs/WORK_PLAN_A_PAYMENT.md`**.

## 5. GÓI CHỌN THÊM CHO [Người A]

Không kích hoạt ở phân bổ mặc định: Payment (91 SP) không nhẹ hơn cả hai người còn lại, vì C = 74. Ba nhóm ứng viên và bảng cân bằng lại: **`docs/WORK_PLAN_A_PAYMENT.md`** §5.

---

## 6. Task chi tiết của [Người B] và [Người C]

| Người | File | Số task | SP |
|---|---|---|---|
| [Người B] — Luồng booking end-to-end (xử lý chính), catalog & tìm kiếm | **`docs/WORK_PLAN_B_BOOKING.md`** | 23 | 104 |
| [Người C] — Hậu mãi, chất lượng & bảo mật | **`docs/WORK_PLAN_C_AFTERCARE.md`** | 17 | 74 |


---

## 7. Hợp đồng giao tiếp giữa các module

Không có message broker. Mọi "sự kiện" là **REST đồng bộ qua Gateway**:
- bên gửi dùng token SERVICE (§3.2);
- bên nhận idempotent;
- bên gửi ghi outbox để retry khi bên nhận lỗi (`outbox_events` ở Payment, `booking_outbox` ở Booking, `provider_outbox` ở Provider).

DTO client là **bản sao riêng** trong service gọi, có `@JsonIgnoreProperties(ignoreUnknown = true)`.

**Mốc publish:**
- Tuần 1 (S1): mỗi chủ module publish path + DTO (stub trả dữ liệu giả cũng được) cho mọi contract mình cài đặt.
- Từ S2: các bên viết code dựa trên stub.

### C1 — Token service (Identity)

| # | Chữ ký | Cài đặt | Dùng bởi |
|---|---|---|---|
| C1.1 | `POST /api/v1/auth/service-token` `{clientId, clientSecret}` → `{accessToken, tokenType, expiresInSeconds}` | [Người B] IAM-01 | Mọi service |
| C1.2 | Java: `ServiceTokenProvider.getToken(): String` + `ServiceTokenInterceptor implements RequestInterceptor` (chép vào `client/` từng service) | [Người B] mẫu ở Booking; mỗi chủ service chép | Feign client cần quyền service |

### C2 — Booking → Payment

| # | Chữ ký | Cài đặt | Gọi bởi | Khi nào |
|---|---|---|---|---|
| C2.1 | `GET /api/v1/payment-policies/effective?centerId&at` → `PolicySnapshotResponse {policyId, depositPercent, commissionPercent, refundTiers[], coolingOffMinutes, coolingOffMinLeadHours, customerNoShowChargePercent, remainingDueHours, paymentSessionTtlMinutes, depositDueHours}` | [A] PAY-03 | [B] BK-01 (báo giá); [B] accept → `deposit_due_at` theo `depositDueHours` | Báo giá, accept |
| C2.2 | `POST /api/v1/refund-quotes` (SERVICE) `RefundQuoteRequest {bookingId, bookingStatus, cancelledBy, reason, cancelledAt, firstUpcomingStartAt, occurrences[{occurrenceId, startAt, status, hasSession}]}` → `RefundQuoteResponse {items[{occurrenceId, priceAmount, paidAmount, feeAmount, refundAmount, rule}], totalPaid, totalFee, totalRefund, policyId}` | [A] PAY-09 | [B] BK-03 | Preview hủy |
| C2.3 | `POST /api/v1/refunds` (SERVICE) `RefundCreateRequest {bookingId, occurrenceIds[], reason, cancelledBy, cancelledAt, firstUpcomingStartAt, requestedBy, idempotencyKey}` → `RefundBatchResponse {items[{refundId, occurrenceId, amount, feeAmount, status}], totalRefund}` | [A] PAY-10 | [B] BK-03, BK-04, OPS-06 (`CAREGIVER_MISSED`) | Sau commit hủy / MISSED |
| C2.4 | `POST /api/v1/refunds/complaints` (SERVICE) `{bookingId, complaintId, amount, decidedBy, note}` → `RefundBatchResponse` | [A] PAY-12 | [C] FB-02 | Sau admin-resolve |
| C2.5 | `POST /api/v1/remaining-invoices` (SERVICE) `{bookingId, occurrences[{occurrenceId, finalStatus}]}` → `RemainingInvoiceResponse {invoiceId?, amount, dueAt, status}` (amount 0 → không tạo hóa đơn, `invoiceId` null) | [A] PAY-11 | [B] OPS-04 | Booking COMPLETED |
| C2.6 | `GET /api/v1/payments/me/outstanding` (CUSTOMER, token khách chuyển tiếp) → `{hasDebt, overdueInvoices[], pendingInvoices[]}`; `GET /api/v1/payments?bookingId=` (thêm quyền SERVICE) → danh sách payment + `refundedAmount` | [A] PAY-11, PAY-16 | [B] BK-02; [C] FB-02 | Tạo booking; trước admin-resolve |

`reason` enum (dùng chung, chép vào cả hai service): `CUSTOMER_CANCEL`, `CENTER_REJECT`, `CENTER_CANCEL`, `CENTER_LOCKED`, `CAREGIVER_MISSED`, `EXPIRED`, `COMPLAINT_ACCEPTED`, `LATE_PAYMENT`, `BOOKING_NOT_CONFIRMABLE`.

### C3 — Payment → Booking

| # | Chữ ký | Cài đặt | Gọi bởi | Khi nào |
|---|---|---|---|---|
| C3.1 | `POST /api/v1/bookings/{id}/deposit-confirmations` (SERVICE) `DepositConfirmationRequest {paymentId, amount, paidAt, policySnapshot, occurrenceDeposits[{occurrenceId, depositAmount}]}` → `BookingDetailResponse`; idempotent theo `paymentId` | [A] PAY-08 (file Booking, [B] review) | [A] PAY-06 (outbox) | IPN thành công — thay sự kiện `PaymentSucceeded(bookingId)` |
| C3.2 | `GET /api/v1/bookings/{id}/payment-context` (SERVICE) → `{bookingId, bookingCode, customerId, centerId, status, totalAmount, depositDueAt, firstStartAt, depositPaidAt, occurrences[{occurrenceId, occurrenceNo, startAt, endAt, status, priceAmount, depositAmount, caregiverId, hasSession}]}` | [A] PAY-08 (dữ liệu giá từ [B] BK-01) | [A] PAY-05, PAY-09 | Tạo phiên, tính hoàn |

### C4 — Thông báo (BookingService)

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C4.1 | Java trong Booking: `NotificationService.notify(List<UUID> recipientUserIds, NotificationType type, Map<String,Object> params, String refType, UUID refId)` — chỉ gọi sau commit | [C] NTF-01 | [B] (BK-03/04/05, OPS-01/02/04/06, PAY-08), [C] (OPS-03/05, FB, NTF-02) |
| C4.2 | `POST /api/v1/notifications` (SERVICE) `{recipientUserIds[], type, title, body, refType, refId}` → `{created}` | [C] NTF-01 | [A] PAY-06/10/11/14 |
| C4.3 | `GET /api/v1/notifications/stream` (SSE) — event `notification`, `session-update`, `heartbeat` | [C] NTF-01 | FE |

### C5 — Provider ↔ Booking

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C5.1 | `POST /api/v1/booking-occurrences/busy-caregivers` (SERVICE) `{caregiverIds[≤ 200], slots[{startAt, endAt}] (≤ 60)}` → `{busyCaregiverIds[]}` (buổi ∈ {SCHEDULED, ACKNOWLEDGED, IN_PROGRESS} của booking đang giữ chỗ) | [B] (Booking, làm trong SRCH-01) | [B] SRCH-01, PRV-02 |
| C5.2 | `POST /api/v1/caregivers/availability-checks` (SERVICE) `{caregiverId, slots[]}` → `{available, violations[{startAt, endAt, reason}]}` | [B] PRV-02 | [B] BK-02 |
| C5.3 | `PUT /api/v1/caregivers/{caregiverId}/rating` (SERVICE) `{ratingAverage (0..5, 2 số lẻ), ratingCount}` → 200 | [C] FB-01 | [C] FB-01 (Booking) |
| C5.4 | `POST /api/v1/bookings/system-cancellations` (SERVICE) `{centerId?, caregiverId?, reason: CENTER_LOCKED \| CAREGIVER_DEACTIVATED, note}` → `{affectedBookings, affectedOccurrences}` | [B] BK-04 | [C] PRV-04 |
| C5.5 | `GET /api/v1/caregivers/{id}` (SERVICE được phép) → thêm `careServiceIds[]`, `centerServiceIds[]` | [B] PRV-01 | [B] Booking `CaregiverPortImpl` |
| C5.6 | `GET /api/v1/care-centers/{id}` (SERVICE được phép) → `managerUserId` | [C] PRV-06 | [C] NTF-02 |

### C6 — Provider → Identity

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C6.1 | `PATCH /api/v1/users/centers/{centerId}/status` (SERVICE / ADMIN) `{status, reason}` → `{affectedUsers}` | [C] IAM-06 | [C] PRV-04 |

### C7 — CareRecipient ↔ Booking

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C7.1 | `GET /api/v1/care-recipients/{id}/snapshot`, `GET /{id}/access?userId&minLevel` (SERVICE) | [C] CR-01 | [B] Booking `create` |
| C7.2 | `GET /api/v1/bookings/caregiver-access?caregiverUserId&recipientId` (SERVICE) → `{allowed}` theo buổi | [B] BK-07 | [C] CareRecipient `getForCaregiver` |

### C8 — Booking / Provider → Media

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C8.1 | `POST /api/v1/media` (SERVICE, multipart `file`, `purpose`, `uploadedBy`, `referenceType`, `referenceId`; `sourceService` = `client_id`) | [C] MED-01 | [C] OPS-05, Provider media |
| C8.2 | `GET /api/v1/media/{id}/download-url`, `DELETE /api/v1/media/{id}` (SERVICE, cùng `source_service`) | [C] MED-01 | như trên |

### C9 — Catalog

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C9.1 | `POST /api/v1/center-services/quote` — **bắt buộc** `pricePlanId` | [B] CAT-01 | [B] BK-01 |
| C9.2 | `GET /api/v1/center-services/price-summary?careServiceId&minPrice&maxPrice` (SERVICE) → `[{centerId, minPrice}]` | [B] CAT-02 | [B] SRCH-02 |

### Hàm nội bộ dùng chung trong BookingService (cần thống nhất giữa [B] và [C])

| Hàm | Chủ | Dùng bởi |
|---|---|---|
| `BookingLifecycle.apply(Booking, BookingAction, actor, reason)` | [B] (hiện có trong `BSI.applyAction`) | Mọi chuyển trạng thái booking |
| `BookingLifecycle.finalizeIfDone(Booking)` + action `FINALIZE` | [B] OPS-04 | [B] BK-03/04, OPS-06 |
| `OccurrenceTransitions.cancel / markMissed / markNoShow / start / complete / acknowledge / resetAcknowledgement` | [B] OPS-01/02/04/06, BK-03 | [B]; [C] chỉ đọc trạng thái (OPS-03/05, FB-01) |
| `BookingOutbox.enqueue(eventType, payload)` + dispatcher | [B] OPS-04 (tạo) | [B] BK-03/04, OPS-06; [C] FB-01/02 (rating, hoàn tiền khiếu nại) |
| `BookingEvents.record(...)` (`booking_events`) | [B] BK-05 | Cả hai |

---

## 8. Lộ trình theo sprint (1 sprint = 1 tuần)

🔒 = **blocker**: task khác chờ task này; làm đầu sprint.

| Sprint | [Người A] — Payment | [Người B] — Luồng booking | [Người C] — Hậu mãi / bảo mật | Mục tiêu demo cuối sprint |
|---|---|---|---|---|
| **S1** | 🔒 PAY-01 (5), PAY-02 (5), **PAY-08 bước 1** (hotfix SEC-01) — 10 SP | 🔒 IAM-01 (5), 🔒 GW-01 (3), BK-07 (3), BK-08 (2), CAT-01 (3) — 16 SP | CR-01 (5), IAM-02 (5), IAM-03 (3), IAM-04 (3) — 16 SP | Hết toàn bộ lỗ hổng SEC-01…08; có token SERVICE; PaymentService chạy trong Docker; publish stub contract §7 |
| **S2** | PAY-03 (3), PAY-04 (3), PAY-05 (8) — 14 SP | CAT-02 (3), 🔒 PRV-01 (5), 🔒 BK-01 (5), OPS-01 (3) — 16 SP | PRV-06 (3), MED-01 (5), 🔒 NTF-01 (5) — 13 SP | Báo giá có giá từng buổi; khách nhận URL VNPay; caregiver acknowledge; thông báo in-app + SSE |
| **S3** | 🔒 PAY-06 (8), PAY-07 (3), PAY-08 (5) — 16 SP | 🔒 OPS-02 (8), 🔒 OPS-04 (5), PRV-03 (5) — 18 SP | OPS-03 (3), NTF-02 (3), PRV-05 (5) — 11 SP | **Luồng chính end-to-end**: đặt → duyệt → cọc VNPay sandbox → CONFIRMED → check-in → task → check-out → COMPLETED; xác minh chứng chỉ 2 bước |
| **S4** | 🔒 PAY-09 (8), 🔒 PAY-10 (8) — 16 SP | 🔒 PRV-02 (8), BK-02 (5), OPS-06 (5) — 18 SP | OPS-05 (5), FB-01 (5) — 10 SP | Kiểm lịch rảnh / kỹ năng / nợ khi đặt; MISSED + khách vắng tự động hoàn tiền; care report có ảnh; đánh giá caregiver; preview hoàn tiền đúng bảng 2.2 |
| **S5** | PAY-11 (8), PAY-12 (3), PAY-13 (5) — 16 SP | SRCH-01 (8), BK-03 (8), SRCH-02 (3) — 19 SP | FB-02 (8), IAM-05 (3) — 11 SP | Tìm caregiver đủ dịch vụ / lịch trống; hủy từng buổi / cả booking có hoàn tiền; REMAINING + chặn nợ; khiếu nại có escalate |
| **S6** | PAY-14 (8), PAY-15 (5), PAY-16 (3), PAY-17 (3) — 19 SP | BK-04 (5), BK-05 (3), BK-06 (3), BK-09 (3), INF-01 (3) — 17 SP | PRV-04 (5), IAM-06 (3), STAT-01 (5) — 13 SP | Khóa trung tâm dây chuyền (hủy hệ thống + hoàn 100%); lịch sử booking; đối soát; dashboard doanh thu / vận hành; e2e toàn hệ thống PASSED |
| **Tổng** | **91** | **104** | **74** | |

**Chuỗi phụ thuộc chính (đường găng):**

```
IAM-01 ─┬─► PAY-05 ─► PAY-06 ─► PAY-08 ─► OPS-02 (B) ─► OPS-04 (B) ─► PAY-11
        │      ▲
        │   BK-01 ─► PAY-04
        ├─► CR-01, MED-01, NTF-01, BK-07
        └─► PAY-09 ─► PAY-10 ─► OPS-06 / BK-03 / BK-04 (B) ─► PRV-04 (C)
PRV-01 + PRV-03 ─► PRV-02 ─► BK-02 ─► SRCH-01   (đều của B)
```

**Ghi chú điều phối:**
- **S1:** [Người B] ưu tiên IAM-01 trong 2 ngày đầu. Trong lúc chờ, các task S1 khác dùng token ADMIN trong test, rồi chuyển sang token SERVICE ngay khi IAM-01 merge.
- **S3:** OPS-02 cần booking CONFIRMED thật. Nếu PAY-06/08 chưa xong, [Người B] test bằng hotfix ADMIN `deposit-paid` của PAY-08 bước 1. Hotfix này bị xóa ở PAY-08 bước 2, nên phải gộp hai việc trước khi kết thúc S3.
  - OPS-03 ([Người C]) dựa trên bảng `care_sessions` / `care_tasks` của OPS-02, nên B merge phần schema + entity của OPS-02 **trong 2 ngày đầu S3**.
- **[Người B] tải 16–19 SP/sprint, cao hơn vận tốc 15.**
  - S3–S5 của [Người C] chỉ 10–11 SP, nên C dành phần dư để review PR Booking và viết test e2e cho luồng chăm sóc.
  - Ngưỡng kích hoạt dự phòng: cuối S4, B còn > 40 SP → chuyển BK-09 và INF-01 sang C. SRCH-02 (P1) có thể dời sau S6.
- **S6 của [Người A] là 19 SP.** PAY-14/15 là P2, có thể cắt theo §5.
---

## 9. Rủi ro và cách xử lý

| Rủi ro | Ảnh hưởng | Xử lý |
|---|---|---|
| VNPay sandbox không gọi được IPN về máy dev (localhost) | Không test được PAY-06 | Dùng ngrok / cloudflared cho `VNPAY_IPN_PUBLIC_URL`; test tự động tự ký IPN bằng hash secret test |
| Sandbox VNPay không hỗ trợ refund thật | PAY-10 không kiểm chứng được với cổng | `VNPAY_REFUND_MODE=SIMULATED` cho dev / test; ghi rõ trong README; production `LIVE` |
| Nhiều người cùng sửa `BookingService` | Xung đột merge | Chủ file theo §2; mỗi task một branch `feature/<mã-task>`, PR nhỏ, merge hằng ngày vào `dev`; change script SQL đặt tên theo ngày + mã task |
| SSE chỉ đúng khi một instance | Mất realtime khi scale | Polling `unread-count` 30 giây làm dự phòng; Redis pub/sub để giai đoạn sau |
| Thay đổi contract FE (path `/api/v1`, `PageResponse`, bỏ `deposit-paid`) | FE vỡ | Cập nhật `../FE-careconnect/openapi/openapi.yaml` từ `/api-docs/*` của Gateway sau mỗi sprint; alias path cũ giữ một sprint khi có ghi chú |
| Không có broker → sự kiện liên service có thể mất | Thiếu hoàn tiền / xác nhận | Outbox ở bên gửi + idempotency ở bên nhận (C2/C3/C5) |
| [Người B] quá tải (104 SP, nằm trên đường găng) | Trễ luồng booking kéo trễ Payment và hậu mãi | Theo dõi burndown mỗi sprint; kích hoạt dự phòng ở §8 (C nhận BK-09, INF-01); nếu A có dư thì A nhận nhóm G1 ở §5 |
| Điểm còn mở O1–O7 (GAP_ANALYSIS §1.3) | Đổi nghiệp vụ giữa chừng | Mặc định đã ghi trong task; nhóm review lại ở buổi planning S2 |
