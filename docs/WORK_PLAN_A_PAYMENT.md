# WORK PLAN — [Người A]: Payment & tài chính

> Tách từ `docs/WORK_PLAN.md` (2026-10-21; phân lại B / C cùng ngày: B xử lý chính luồng booking, C nhẹ hơn). File này chứa **tổng quan tính năng theo role**, **toàn bộ task chi tiết** của [Người A], các contract liên quan và lịch sprint riêng.
> Phần dùng chung — nguyên tắc chia việc, quy ước ErrorCode / token SERVICE / tiền tệ / thời gian (§1–§3), toàn bộ hợp đồng (§7), lộ trình chung (§8), rủi ro (§9) — nằm ở `docs/WORK_PLAN.md`.
> **Số mục giữ nguyên như bản gốc**, nên mọi tham chiếu `§x`, `C2.3`… trỏ đúng sang `docs/WORK_PLAN.md`. Khảo sát hiện trạng: `docs/GAP_ANALYSIS.md`.

## Tổng quan: [Người A] làm gì

**Vai trò trong nhóm:** sở hữu trọn **PaymentService** (service mới, port 8087) và mọi thứ liên quan đến tiền: chính sách nền tảng, đặt cọc, thanh toán phần còn lại, hoàn tiền, hoa hồng, đối soát, doanh thu. Không phụ trách màn hình / API booking, nhưng là bên quyết định **số tiền**: Booking hỏi Payment "hủy buổi này hoàn bao nhiêu", Payment báo Booking "đã cọc xong".

| Role | Tính năng [Người A] làm cho role này | Task |
|---|---|---|
| **CUSTOMER** | Thanh toán cọc qua VNPay (phiên 15 phút) và xem kết quả; thanh toán phần còn lại (REMAINING) sau khi booking hoàn thành, hạn 48h; bị chặn đặt lịch mới khi nợ quá hạn; số tiền hoàn dự kiến khi hủy (Booking hiển thị, Payment tính); nhận tiền hoàn tự động; xem lịch sử giao dịch / hoàn tiền | PAY-05, PAY-06, PAY-09, PAY-10, PAY-11, PAY-16 |
| **CENTER_MANAGER** | Xem giao dịch của trung tâm; xem bảng đối soát theo kỳ (số tiền trung tâm được nhận); thống kê doanh thu trung tâm | PAY-14, PAY-15, PAY-16 |
| **ADMIN** | Cấu hình chính sách nền tảng có hiệu lực theo ngày: % cọc, bảng % phí hủy theo mốc giờ, thời gian cân nhắc, % hoa hồng, hạn thanh toán, kỳ đối soát (toàn hệ thống hoặc riêng từng trung tâm); hoàn tiền theo quyết định khiếu nại; miễn nợ; xem / thử lại hoàn tiền lỗi; tạo, xác nhận, đánh dấu đã chuyển tiền đối soát; doanh thu toàn nền tảng và theo trung tâm | PAY-02, PAY-10, PAY-11, PAY-12, PAY-14, PAY-15 |
| **CAREGIVER** | Không có tính năng trực tiếp | — |
| **Hệ thống / service khác** | Nhận IPN VNPay (kiểm chữ ký, chống ghi nhận trùng, đối chiếu số tiền); job hết hạn phiên + truy vấn lại giao dịch (querydr); xác nhận cọc sang Booking (thay endpoint `deposit-paid` đang bị lỗ hổng); snapshot chính sách vào booking; phân bổ cọc theo buổi; tính phí khách vắng mặt; sổ cái hoa hồng | PAY-01, PAY-03, PAY-04, PAY-06, PAY-07, PAY-08, PAY-12, PAY-13, PAY-17 |

**Service đụng tới:** PaymentService (chủ), BookingService (PAY-08: endpoint xác nhận cọc + `payment-context`, [Người B] review).

---

## 4. GÓI PAYMENT — phần việc mặc định của [Người A]

> **Trạng thái hiện tại: CHƯA BẮT ĐẦU.** Không có code Payment trên `dev`/`main`, không có branch local (xác nhận 2026-10-21). Phần "đã xong" theo code thực tế chỉ gồm:
> - stub `POST /api/v1/bookings/{id}/deposit-paid` (`BookingController.java:198-204`) — **có lỗ hổng SEC-01**;
> - `RefundPolicyImpl` (CENTER/SYSTEM → 100%, CUSTOMER → `null`), kết quả chỉ được log (`BookingServiceImpl.java:666-671`);
> - báo giá + cọc `subtotal × 0.30` hardcode trong Booking (`CareServiceCatalogPortImpl.java:80-143`).
>
> Cả ba được thay thế trong gói này. **Tổng gói Payment: 91 SP** (17 task).

### Tóm tắt gói Payment

| Mã | Tên | Ưu tiên | SP | Sprint |
|---|---|---|---|---|
| PAY-01 | Dựng PaymentService | P0 | 5 | S1 |
| PAY-02 | PlatformPolicy có hiệu lực theo thời gian | P0 | 5 | S1 |
| PAY-03 | Chính sách hiệu lực + snapshot vào booking | P0 | 3 | S2 |
| PAY-04 | Phân bổ cọc theo buổi | P0 | 3 | S2 |
| PAY-05 | Tạo phiên thanh toán cọc VNPay (15 phút) | P0 | 8 | S2 |
| PAY-06 | IPN + return URL, idempotency, đối chiếu tiền | P0 | 8 | S3 |
| PAY-07 | Job hết hạn phiên + đối soát querydr | P1 | 3 | S3 |
| PAY-08 | Thay stub `deposit-paid` bằng luồng chuẩn (SEC-01) | P0 | 5 | S1 (hotfix) + S3 |
| PAY-09 | Preview-cancel: bộ tính phí / hoàn theo bảng 2.2 | P0 | 8 | S4 |
| PAY-10 | Thực hiện hoàn tiền + API refund VNPay | P0 | 8 | S4 |
| PAY-11 | Thanh toán REMAINING, hạn 48h, nợ | P1 | 8 | S5 |
| PAY-12 | Phí CUSTOMER_NO_SHOW + hoàn tiền theo khiếu nại | P1 | 3 | S5 |
| PAY-13 | Hoa hồng & sổ cái (ledger) | P1 | 5 | S5 |
| PAY-14 | Đối soát (Settlement) theo kỳ | P2 | 8 | S6 |
| PAY-15 | Thống kê doanh thu nền tảng / trung tâm | P2 | 5 | S6 |
| PAY-16 | Lịch sử giao dịch | P1 | 3 | S6 |
| PAY-17 | Kiểm thử end-to-end Payment + tài liệu sandbox | P1 | 3 | S6 |
| | **Tổng** | | **91** | |

---

#### PAY-01 — Dựng PaymentService
**Người:** [Người A] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S1 · **Phụ thuộc:** — (chặn mọi PAY khác)

- **Role:** —
- **API:** `GET /actuator/health`, Swagger `/swagger-ui.html`. Gateway route `/api/v1/payments/**`, `/api/v1/payment-policies/**`, `/api/v1/refunds/**`, `/api/v1/remaining-invoices/**`, `/api/v1/settlements/**`, `/api/v1/revenue/**`, `/api/v1/refund-quotes/**` (cấu hình gateway do GW-01 làm; PAY-01 cung cấp URL).
- **Nội dung:**
  1. Maven project `PaymentService/`, package `mss301.paymentservice`, Java 21, Boot 4.1.1. Dependency giống BookingService: WebMVC, JPA, Validation, Security, OAuth2 Resource Server, Actuator, OpenFeign, Resilience4j, MapStruct, springdoc, PostgreSQL; test dùng H2 + security-test.
  2. Chép mẫu chung: `APIResponse`, `AppException`, `ErrorCode` (6000–6099 chung), `GlobalExceptionHandler`, `BaseEntity`, `MapStructConfig`, `IgnoreServerManagedFields`, `SecurityConfig` / `JwtConfig` (aud `careconnect-api`), `CurrentUserId`, `CurrentCenterId`, `RequestIdFilter`, `Clock` bean, `FeignAuthForwardingInterceptor`, `ServiceTokenInterceptor` (§3.2).
  3. Cấu hình env prefix `PAYMENT_`:
     - `PAYMENT_SERVER_PORT=8087`, `PAYMENT_DB_*` (127.0.0.1:5447, DB `CARECONNECT_PAYMENT`);
     - `PAYMENT_GATEWAY_URL`, `PAYMENT_SERVICE_CLIENT_ID/SECRET`;
     - `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL`, `VNPAY_API_URL`, `VNPAY_RETURN_URL`, `VNPAY_IPN_PUBLIC_URL`.
  4. Thêm `docker-compose.yml` riêng, `compose.stack.yaml`, `Dockerfile`, một dòng `include` trong `compose.yaml`, `.env.example` chia nhóm.
  5. `schema/payment.sql` rỗng khung (các bảng do PAY-02… thêm vào).
  6. Thêm `PaymentService/AGENTS.md` (port, dải lỗi, quy tắc tiền tệ ở §3.3), README; cập nhật bảng service trong `AGENTS.md` gốc.
- **DB:** tạo DB `CARECONNECT_PAYMENT`.
- **Lỗi:** `INVALID_REQUEST (6000, 400)`, `SERVICE_UNAVAILABLE (6004, 503)`, `AUTH_DEPENDENCY_UNAVAILABLE (6005, 503)`, `CONCURRENT_UPDATE (6006, 409)`, `CENTER_SCOPE_REQUIRED (6007, 403)`.
- **AC:**
  - Given stack Docker, When `start.bat`, Then `payment-service` healthy và Swagger hiển thị.
  - Given token sai audience, When gọi bất kỳ API nào, Then 401 envelope `APIResponse`.
  - Given test context, When `mvnw verify`, Then xanh.

#### PAY-02 — PlatformPolicy có hiệu lực theo thời gian
**Người:** [Người A] · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S1 · **Phụ thuộc:** PAY-01

- **Role:** ADMIN (ghi), mọi user đã đăng nhập (đọc chính sách đang hiệu lực).
- **API:**
  - `GET /api/v1/payment-policies?scope=&centerId=&page=&size=` (ADMIN)
  - `GET /api/v1/payment-policies/{id}` (ADMIN)
  - `POST /api/v1/payment-policies` (ADMIN)
  - `PUT /api/v1/payment-policies/{id}` (ADMIN — chỉ khi chưa tới `effectiveFrom`)
  - `DELETE /api/v1/payment-policies/{id}` (ADMIN — xóa mềm, chỉ khi chưa hiệu lực)
- **Request `PolicyRequest`:**

  | Field | Kiểu | Validation |
  |---|---|---|
  | `scope` | `GLOBAL` / `CENTER` | `@NotNull` |
  | `centerId` | UUID | bắt buộc khi `CENTER`, cấm khi `GLOBAL` |
  | `effectiveFrom` | Instant | `@NotNull`, phải ở tương lai (≥ now + 1 phút) |
  | `depositPercent` | 0–100, 2 chữ số thập phân | mặc định đề xuất trên form = 30 |
  | `commissionPercent` | 0–100 | |
  | `paymentSessionTtlMinutes` | 1–60 | 15 |
  | `depositDueHours` | 1–72 | 24 |
  | `coolingOffMinutes` | ≥ 0 | 60 |
  | `coolingOffMinLeadHours` | ≥ 0 | 24 |
  | `refundTiers[]` | `{minLeadHours, maxLeadHours (null = ∞), feePercentOfOccurrence}` | xem quy tắc 1 |
  | `customerNoShowChargePercent` | 0–100 | 100 |
  | `remainingDueHours` | 1–720 | 48 |
  | `settlementCycle` | `MONTHLY` / `WEEKLY` | |
  | `note` | ≤ 500 | |

  **Response `PolicyResponse`:** mọi field + `id`, `status` (`SCHEDULED` / `ACTIVE` / `SUPERSEDED`, tính lúc đọc), `createdBy`, `createdAt`.
- **Quy tắc nghiệp vụ:**
  1. Kiểm `refundTiers` khi tạo / sửa:
     - không rỗng;
     - sắp theo `minLeadHours`;
     - liên tục và không chồng (`max` tầng trước = `min` tầng sau);
     - tầng đầu `minLeadHours = 0`, tầng cuối `maxLeadHours = null`.

     Bộ seed mặc định theo bảng 2.2: `[0,24) → 30%`, `[24,72) → 10%`, `[72,∞) → 0%`.
  2. Chính sách **bất biến khi đã hiệu lực**: muốn đổi thì tạo bản mới với `effectiveFrom` tương lai. Không có hai chính sách cùng `(scope, centerId, effectiveFrom)`.
  3. Hiệu lực tại thời điểm `t` cho trung tâm `c`: bản `CENTER` của `c` có `effectiveFrom ≤ t` mới nhất. Nếu không có thì lấy bản `GLOBAL` có `effectiveFrom ≤ t` mới nhất. **Bản CENTER thay thế toàn bộ** bản GLOBAL, không trộn từng field.
  4. Phải luôn tồn tại ít nhất một bản `GLOBAL` đã hiệu lực. `DataInitializer` seed bản GLOBAL theo mục 2 của quyết định nếu bảng rỗng (idempotent, `created_by` = UUID hệ thống).
  5. Mọi thay đổi ghi `log.info` và lưu `created_by`, `updated_by` từ token.
- **DB (`platform_policies`):**

  | Cột | Kiểu / ràng buộc |
  |---|---|
  | `policy_id` | uuid PK |
  | `scope` | varchar CHECK |
  | `center_id` | uuid null |
  | `effective_from` | timestamptz NN |
  | `deposit_percent`, `commission_percent` | numeric(5,2) CHECK 0..100 |
  | `payment_session_ttl_minutes`, `deposit_due_hours`, `cooling_off_minutes`, `cooling_off_min_lead_hours`, `remaining_due_hours` | int |
  | `refund_tiers` | jsonb NN |
  | `customer_no_show_charge_percent` | numeric(5,2) |
  | `settlement_cycle` | varchar CHECK |
  | `note` | |
  | `created_by`, `updated_by` | uuid |
  | `created_at`, `updated_at`, `deleted_at` | timestamptz |
  | `version` | bigint |

  - Unique một phần `(scope, coalesce(center_id,'00000000-0000-0000-0000-000000000000'), effective_from) WHERE deleted_at IS NULL`.
  - CHECK `scope='GLOBAL' = (center_id IS NULL)`.
- **Lỗi:** `POLICY_NOT_FOUND (6100, 404)`, `POLICY_EFFECTIVE_FROM_IN_PAST (6101, 400)`, `POLICY_ALREADY_EFFECTIVE (6102, 409)`, `INVALID_PERCENT (6103, 400)`, `INVALID_REFUND_TIERS (6104, 400)`, `NO_EFFECTIVE_POLICY (6105, 503)`, `POLICY_DUPLICATED (6106, 409)`, `CENTER_ID_REQUIRED (6107, 400)`.
- **AC:**
  - Given bản GLOBAL hiệu lực từ hôm qua và bản CENTER cho C1 hiệu lực từ ngày mai, When tra hiệu lực cho C1 tại thời điểm hiện tại, Then trả bản GLOBAL; tại ngày mai + 1 phút trả bản CENTER.
  - Given chính sách đã hiệu lực, When `PUT`, Then 409 `POLICY_ALREADY_EFFECTIVE`.
  - Given tiers có khoảng hở 24–48h, When tạo, Then 400 `INVALID_REFUND_TIERS`.
  - Given CUSTOMER, When `POST`, Then 403.

#### PAY-03 — Chính sách hiệu lực + snapshot vào booking
**Người:** [Người A] · **Ưu tiên:** P0 · **SP:** 3 · **Sprint:** S2 · **Phụ thuộc:** PAY-02; BK-01 (Booking hiển thị cọc dự kiến)

- **Role:** mọi user đã đăng nhập (đọc), SERVICE.
- **API:** `GET /api/v1/payment-policies/effective?centerId={uuid}&at={instant?}` → `PolicySnapshotResponse`.
  - Thiếu `at` thì lấy now.
  - Response gồm mọi tham số tính tiền + `policyId`, `resolvedAt`.
- **Quy tắc nghiệp vụ:**
  1. BK-01 (báo giá) gọi API này để hiển thị "cọc dự kiến", **không** snapshot.
  2. **Snapshot chính thức** được chụp khi tạo phiên thanh toán cọc (PAY-05):
     - chụp chính sách hiệu lực tại thời điểm đó, lưu `booking_financials.policy_snapshot`;
     - khi IPN thành công thì gửi sang Booking trong `deposit-confirmations` (PAY-08) để Booking lưu `bookings.policy_snapshot`.
  3. Nếu khách tạo lại phiên (phiên trước hết hạn) mà chính sách đã đổi thì dùng chính sách **mới**: snapshot chỉ cố định khi cọc **thành công**.
  4. Xóa cấu hình `booking.deposit-rate` / `BOOKING_DEPOSIT_RATE` khỏi BookingService (`BookingProperties.java:43`, `application.yaml:105`, `CareServiceCatalogPortImpl.java:109-111`). Báo giá của Booking lấy `depositPercent` từ API này. Phần sửa này nằm trong file của Booking — [Người A] sửa, [Người B] review.
- **DB:** `booking_financials`:
  - `booking_id` uuid PK, `customer_id`, `center_id`;
  - `policy_snapshot` jsonb NN, `total_amount`, `deposit_amount`, `commission_percent` numeric(5,2);
  - `deposit_payment_id` uuid null, `deposit_paid_at`;
  - `status` (`AWAITING_DEPOSIT`, `DEPOSIT_PAID`, `CLOSED`);
  - `created_at`, `updated_at`, `version`.
- **Lỗi:** `NO_EFFECTIVE_POLICY (6105, 503)`, `CENTER_ID_REQUIRED (6107, 400)`.
- **AC:**
  - Given admin đổi deposit 30% → 40% từ ngày mai, When khách cọc hôm nay, Then `policy_snapshot.depositPercent = 30` và các lần hủy về sau dùng 30%.
  - Given BookingService, When khởi động không có `BOOKING_DEPOSIT_RATE`, Then không lỗi.

#### PAY-04 — Phân bổ cọc theo buổi
**Người:** [Người A] · **Ưu tiên:** P0 · **SP:** 3 · **Sprint:** S2 · **Phụ thuộc:** BK-01 (`price_amount` từng buổi), PAY-03

- **Role:** nội bộ (thư viện trong PaymentService) — không có API riêng.
- **Nội dung:** `DepositAllocator.allocate(List<OccurrencePrice> occurrences, BigDecimal depositPercent) → List<OccurrenceDeposit>`.
- **Quy tắc nghiệp vụ:**
  1. Sắp các buổi theo `startAt` tăng dần, buổi cùng giờ thì theo `occurrenceNo`.
  2. `depositTotal = round(depositPercent% × Σ price_i)` (làm tròn đồng, HALF_UP).
  3. Với mọi buổi trừ buổi cuối: `deposit_i = round(depositPercent% × price_i)`.
  4. Buổi cuối: `deposit_last = depositTotal − Σ deposit_(các buổi trước)`. Phần lệch làm tròn dồn vào buổi cuối; `deposit_last` có thể lệch ±(n−1) đồng so với công thức.
  5. Chỉ phân bổ cho buổi `SCHEDULED` hoặc `ACKNOWLEDGED` tại thời điểm cọc (buổi đã hủy không được phân bổ).
  6. Ghi kết quả vào `occurrence_financials` (Payment) khi IPN thành công và gửi sang Booking (PAY-08) để lưu `booking_occurrences.deposit_amount`.
- **DB:** `occurrence_financials`:
  - `occurrence_id` PK, `booking_id` FK → `booking_financials`, `center_id`;
  - `price_amount`, `deposit_amount`, `charged_amount` (mặc định 0), `refunded_amount` (mặc định 0);
  - `final_status` varchar null (`COMPLETED` / `CANCELLED` / `MISSED` / `CUSTOMER_NO_SHOW`);
  - `updated_at`, `version`;
  - CHECK `refunded_amount ≤ deposit_amount + charged_amount`.
- **Lỗi:** `BOOKING_PRICE_MISSING (6208, 409)` khi có buổi `price_amount` null.
- **AC:**
  - Given 3 buổi giá 100.000 / 100.000 / 100.001 và cọc 30%, When phân bổ, Then cọc = 30.000 / 30.000 / 30.000 và tổng = round(90.000,3) = 90.000.
  - Given 1 buổi, Then `deposit = depositTotal`.
  - Unit test tối thiểu 6 trường hợp, gồm: giá lẻ, 60 buổi, 0%, 100%.

#### PAY-05 — Tạo phiên thanh toán cọc VNPay (hạn 15 phút)
**Người:** [Người A] · **Ưu tiên:** P0 · **SP:** 8 · **Sprint:** S2 · **Phụ thuộc:** PAY-03, PAY-04, IAM-01, BK-01 (`payment-context`)

- **Role:** CUSTOMER (chủ booking).
- **API:** `POST /api/v1/payments/deposits`
  - Request `DepositPaymentRequest`: `bookingId` (`@NotNull`), `bankCode` (tùy chọn: `VNPAYQR` / `VNBANK` / `INTCARD`), `locale` (`vn` / `en`, mặc định `vn`).
  - Response `PaymentSessionResponse`: `paymentId`, `bookingId`, `amount`, `paymentUrl`, `expiresAt`, `status`.
  - Header `Idempotency-Key` tùy chọn.
- **Quy tắc nghiệp vụ** (không giữ transaction khi gọi mạng):
  1. Gọi Booking `GET /api/v1/bookings/{id}/payment-context` bằng token SERVICE (§7, C3.2). Kiểm:
     - `customerId == sub` (khác thì 404 `BOOKING_NOT_FOUND`);
     - `status == AWAITING_DEPOSIT`;
     - `now < depositDueAt`;
     - mọi buổi đã có `priceAmount`.
  2. Lấy chính sách hiệu lực (PAY-03) cho `centerId`. Tính `depositAmount` bằng `DepositAllocator` (PAY-04, chưa lưu phân bổ).
  3. Trong transaction ngắn, khóa dòng `booking_financials` (`findForUpdate`; tạo mới nếu chưa có):
     - Nếu đã có payment `DEPOSIT` `PENDING` chưa hết hạn với **cùng số tiền** → trả lại phiên đó (idempotent). Khác số tiền → đánh `EXPIRED` phiên cũ.
     - Nếu `booking_financials.status == DEPOSIT_PAID` → 409 `DEPOSIT_ALREADY_PAID`.
     - Tạo `payments` (type `DEPOSIT`, status `PENDING`, `amount`, `txn_ref` = 20 ký tự `[A-Z0-9]` duy nhất, `expires_at = now + paymentSessionTtlMinutes` nhưng không quá `depositDueAt`); lưu `policy_snapshot` vào `booking_financials`.
  4. Dựng URL VNPay:
     - `vnp_Version=2.1.0`, `vnp_Command=pay`, `vnp_TmnCode`, `vnp_Amount=amount×100`, `vnp_CurrCode=VND`, `vnp_TxnRef`;
     - `vnp_OrderInfo="Coc booking <bookingCode>"` (không dấu), `vnp_OrderType=other`, `vnp_Locale`, `vnp_ReturnUrl`, `vnp_IpAddr` (IP client lấy qua gateway, không tin header client);
     - `vnp_CreateDate`, `vnp_ExpireDate` (định dạng `yyyyMMddHHmmss` giờ GMT+7);
     - `vnp_SecureHash = HMAC-SHA512(hashSecret, query đã sắp xếp theo key và URL-encode)`.
  5. Ghi `payment_transactions` (event `CREATE_URL`, không lưu hash secret).
- **Trạng thái:** payment `PENDING`; booking không đổi.
- **DB:**
  - `payments`:

    | Cột | Kiểu / ràng buộc |
    |---|---|
    | `payment_id` | uuid PK |
    | `booking_id`, `customer_id`, `center_id` | uuid NN |
    | `payment_type` | CHECK `DEPOSIT` / `REMAINING` |
    | `amount` | numeric(14,2) CHECK > 0 |
    | `status` | CHECK `PENDING`, `SUCCESS`, `FAILED`, `EXPIRED`, `PARTIALLY_REFUNDED`, `REFUNDED` |
    | `txn_ref` | varchar(34) UNIQUE |
    | `provider` | `VNPAY` |
    | `provider_transaction_no`, `bank_code`, `card_type`, `response_code` | |
    | `expires_at`, `paid_at` | |
    | `refunded_amount` | mặc định 0 |
    | `invoice_id` | null (REMAINING) |
    | `created_at`, `updated_at`, `version` | |

    Unique một phần `(booking_id, payment_type) WHERE status='PENDING'`.
  - `payment_transactions`: `id`, `payment_id` null, `refund_id` null, `event_type` (`CREATE_URL` / `RETURN` / `IPN` / `QUERY` / `REFUND_CALL`), `payload` jsonb (đã che `vnp_SecureHash`), `signature_valid` bool, `created_at`.
- **Lỗi:**

  | Key | Mã | HTTP | Điều kiện |
  |---|---|---|---|
  | `BOOKING_NOT_FOUND` | 6207 | 404 | Booking không tồn tại hoặc không thuộc khách |
  | `BOOKING_NOT_PAYABLE` | 6201 | 409 | Booking không ở `AWAITING_DEPOSIT` |
  | `DEPOSIT_DUE_PASSED` | 6205 | 409 | Đã quá `depositDueAt` |
  | `DEPOSIT_ALREADY_PAID` | 6209 | 409 | Booking đã cọc |
  | `BOOKING_PRICE_MISSING` | 6208 | 409 | Có buổi chưa có giá |
  | `BOOKING_SERVICE_UNAVAILABLE` | 6206 | 503 | Không gọi được Booking |
  | `NO_EFFECTIVE_POLICY` | 6105 | 503 | Không có chính sách hiệu lực |
- **AC:**
  - Given booking AWAITING_DEPOSIT của khách K, When K tạo phiên, Then 200, `paymentUrl` chứa `vnp_SecureHash` hợp lệ (test tính lại hash), `expiresAt` = now + 15 phút.
  - Given K gọi lại trong 15 phút, Then trả **cùng** `paymentId`.
  - Given khách khác, Then 404.
  - Given booking CONFIRMED, Then 409 `BOOKING_NOT_PAYABLE`.
  - Given `depositDueAt` còn 5 phút, Then `expiresAt = depositDueAt`.

#### PAY-06 — IPN + return URL, idempotency, đối chiếu số tiền
**Người:** [Người A] · **Ưu tiên:** P0 · **SP:** 8 · **Sprint:** S3 · **Phụ thuộc:** PAY-05, PAY-08, GW-01 (route public)

- **Role:** public (VNPay gọi; xác thực bằng chữ ký); CUSTOMER cho return.
- **API:**
  - `GET /api/v1/payments/vnpay/ipn?vnp_*` — **public**, permit trong `SecurityConfig` và Gateway, có `@SecurityRequirements({})`. Trả **JSON thô theo chuẩn VNPay** `{ "RspCode": "00", "Message": "Confirm Success" }`.

    > ⚠️ **Cần ghi ngoại lệ rule #3 vào `AGENTS.md`**, giống JWKS: giao thức VNPay bắt buộc định dạng này.
  - `GET /api/v1/payments/vnpay/return?vnp_*` — public. Chỉ kiểm chữ ký và trả `APIResponse<PaymentResultResponse>` (`paymentId`, `bookingId`, `status`, `amount`) cho FE hiển thị. **Không đổi trạng thái** (nguồn sự thật là IPN).
  - `GET /api/v1/payments/{id}` (CUSTOMER chủ / ADMIN) — FE poll trạng thái sau khi quay về.
- **Quy tắc nghiệp vụ (IPN):**
  1. Tính lại HMAC-SHA512 trên mọi `vnp_*` trừ `vnp_SecureHash` / `vnp_SecureHashType`. Sai → `{"RspCode":"97","Message":"Invalid signature"}` và ghi transaction với `signature_valid=false`.
  2. Tìm payment theo `vnp_TxnRef`. Không có → `"01" Order not found`.
  3. `vnp_Amount / 100 ≠ amount` → `"04" Invalid amount`; log `warn`, payment giữ nguyên.
  4. Payment đã `SUCCESS` / `FAILED` → `"02" Order already confirmed` (**idempotent** — không ghi nhận lần 2).
  5. `vnp_ResponseCode == "00"` **và** `vnp_TransactionStatus == "00"`:
     - transaction ngắn: payment → `SUCCESS` (lưu `paid_at`, `provider_transaction_no`, `bank_code`); `booking_financials` → `DEPOSIT_PAID`; lưu `occurrence_financials` (PAY-04);
     - ghi một dòng `outbox_events` (`DEPOSIT_CONFIRMED`, payload = request của C3.1) trong cùng transaction;
     - trả `"00"`.
  6. Ngược lại: payment → `FAILED` (lưu `response_code`), trả `"00"`.
  7. Payment đã `EXPIRED` mà IPN báo thành công (khách trả trễ): vẫn ghi `SUCCESS` nếu booking còn `AWAITING_DEPOSIT` và chưa quá `depositDueAt`; ngược lại ghi `SUCCESS` + tạo refund 100% lý do `LATE_PAYMENT` (PAY-10) và log `warn`.
  8. **Outbox dispatcher** (`@Scheduled` 10 giây):
     - gọi Booking `POST /bookings/{id}/deposit-confirmations` (C3.1) bằng token SERVICE, ngoài transaction;
     - thành công → đánh dấu `SENT`; lỗi → `attempts++` với backoff (tối đa 10 lần), sau đó `FAILED` và log `error`;
     - Booking trả 409 `DEPOSIT_DUE_PASSED` / booking không còn AWAITING_DEPOSIT → tạo refund 100% lý do `BOOKING_NOT_CONFIRMABLE`.
  9. Gửi thông báo cho khách "Đặt cọc thành công" qua C4 (NTF-01), best-effort. Thông báo cho manager / caregiver do Booking gửi khi CONFIRMED.
- **Trạng thái:** payment `PENDING → SUCCESS | FAILED`; Booking `AWAITING_DEPOSIT → CONFIRMED` (qua PAY-08).
- **DB:** `outbox_events`:
  - `event_id` PK, `event_type`, `aggregate_id`, `payload` jsonb;
  - `status` (`PENDING` / `SENT` / `FAILED`), `attempts`, `next_attempt_at`, `last_error`, `created_at`;
  - index `(status, next_attempt_at)`.
- **Lỗi** (IPN không dùng `ErrorCode`, chỉ dùng `RspCode`): 00, 01, 02, 04, 97, 99. Return URL sai chữ ký → `VNPAY_SIGNATURE_INVALID (6300, 400)`.
- **AC:**
  - Given IPN hợp lệ thành công, When gọi 2 lần, Then lần 1 `00` + booking CONFIRMED; lần 2 `02`, chỉ có 1 dòng `SUCCESS`, chỉ 1 outbox event.
  - Given IPN sửa `vnp_Amount`, Then `97` (chữ ký sai).
  - Given IPN chữ ký đúng nhưng số tiền khác, Then `04`.
  - Given Booking tắt khi IPN tới, Then IPN vẫn trả `00`, và outbox gửi lại thành công khi Booking bật.
  - Given return URL, Then không đổi trạng thái payment.

#### PAY-07 — Job hết hạn phiên 15 phút + đối soát querydr
**Người:** [Người A] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S3 · **Phụ thuộc:** PAY-05, PAY-06

- **Role:** hệ thống.
- **Nội dung:** `scheduler/PaymentExpiryScheduler` (mỗi 1 phút, cấu hình `PAYMENT_EXPIRY_SCAN_INTERVAL`).
- **Quy tắc nghiệp vụ:**
  1. Lấy tối đa 100 payment `PENDING` có `expires_at + 5 phút ≤ now`; chừa 5 phút để IPN trễ kịp tới.
  2. Với mỗi payment, gọi VNPay querydr (`vnp_Command=querydr`, `vnp_RequestId` duy nhất, chữ ký HMAC-SHA512 trên chuỗi nối bằng `|` theo tài liệu VNPay) ngoài transaction:
     - `vnp_TransactionStatus=00` → xử lý như IPN thành công (dùng chung method nội bộ của PAY-06, idempotent);
     - giao dịch không tồn tại hoặc thất bại → `EXPIRED`;
     - querydr lỗi mạng → giữ `PENDING`, thử lại ở lần quét sau. Sau 24h vẫn lỗi thì → `EXPIRED` và log `error`.
  3. Mỗi payment xử lý trong transaction riêng; lỗi một item không làm dừng job.
  4. **Không** tự hủy booking: booking AWAITING_DEPOSIT vẫn còn hạn `deposit_due_at`, khách tạo phiên mới được. Booking EXPIRED do scheduler của BookingService.
- **Lỗi:** `VNPAY_QUERY_FAILED (6303, 503)` (chỉ log).
- **AC:**
  - Given payment PENDING quá hạn và querydr trả "không tìm thấy", When job chạy, Then payment EXPIRED và khách tạo được phiên mới.
  - Given querydr trả thành công, Then payment SUCCESS và booking CONFIRMED (một lần duy nhất).

#### PAY-08 — Thay stub `POST /bookings/{id}/deposit-paid` bằng luồng chuẩn (SEC-01)
**Người:** [Người A] (sửa file BookingService, [Người B] review) · **Ưu tiên:** P0 · **SP:** 5 · **Sprint:** S1 (bước 1) + S3 (bước 2–6) · **Phụ thuộc:** IAM-01 (bước 2)

- **Role:** SERVICE (PaymentService).
- **API:**
  - **Bước 1 (hotfix S1, trước khi có IAM-01):** đổi `@PreAuthorize` của `markDepositPaid` (`BookingController.java:200`) thành `hasRole('ADMIN')`. Sửa test `BookingApiTests.java:781-784` để khẳng định CUSTOMER / CAREGIVER / MANAGER nhận 403. Helper `acceptAndConfirm` dùng token ADMIN.
  - **Bước 2:** xóa `POST /bookings/{id}/deposit-paid`, `DepositPaidRequest`. Thêm `POST /api/v1/bookings/{id}/deposit-confirmations` — `hasRole('SERVICE')`.
    - Request `DepositConfirmationRequest`: `paymentId` (`@NotNull`), `amount` (`@NotNull`, > 0), `paidAt` (`@NotNull`), `policySnapshot` (object `@NotNull`), `occurrenceDeposits[]` (`{occurrenceId, depositAmount}`, `@NotEmpty`).
    - Response `BookingDetailResponse`.
  - **Bước 3:** thêm `GET /api/v1/bookings/{id}/payment-context` — `hasRole('SERVICE')` → `{bookingId, bookingCode, customerId, centerId, status, totalAmount, depositDueAt, firstStartAt, occurrences[{occurrenceId, occurrenceNo, startAt, endAt, status, priceAmount, depositAmount, caregiverId}]}`.
- **Quy tắc nghiệp vụ (deposit-confirmations):**
  1. Khóa dòng booking (`findForUpdate`).
  2. Booking đã `CONFIRMED` với **cùng** `paymentId` → 200 no-op (idempotent). Đã CONFIRMED với `paymentId` khác → 409 `DEPOSIT_ALREADY_CONFIRMED_OTHER_PAYMENT`.
  3. Status ≠ `AWAITING_DEPOSIT`, hoặc `paidAt > depositDueAt + 15 phút` (sai số phiên) → 409 `INVALID_BOOKING_TRANSITION` (Payment sẽ hoàn tiền).
  4. Kiểm:
     - `Σ occurrenceDeposits.depositAmount == amount`;
     - `amount == round(policySnapshot.depositPercent% × Σ price_amount buổi chưa hủy)`;
     - mọi `occurrenceId` thuộc booking.

     Sai → 409 `DEPOSIT_AMOUNT_MISMATCH`.
  5. Áp `BookingAction.PAY_DEPOSIT`. Lưu `bookings.policy_snapshot`, `deposit_payment_id`, `deposit_paid_at`, `booking_occurrences.deposit_amount`. Ghi `booking_status_history` (reason `paymentId=...`).
  6. Sau commit: gửi thông báo (NTF-02) cho khách, manager trung tâm và caregiver được gán: "Lịch đã xác nhận".
- **Trạng thái:** booking `AWAITING_DEPOSIT → CONFIRMED`.
- **DB (Booking):**
  - `bookings`: + `policy_snapshot` jsonb null, `deposit_payment_id` uuid null, `deposit_paid_at` timestamptz null; CHECK `status NOT IN ('CONFIRMED','IN_PROGRESS','COMPLETED') OR deposit_payment_id IS NOT NULL` (backfill: dữ liệu dev cũ đặt `deposit_payment_id` = UUID hệ thống).
  - `booking_occurrences`: + `deposit_amount` numeric(14,2) null.
- **Lỗi:** `DEPOSIT_AMOUNT_MISMATCH (5155, 409)`, `DEPOSIT_ALREADY_CONFIRMED_OTHER_PAYMENT (5156, 409)`, `INVALID_BOOKING_TRANSITION (5101, 409)`, `BOOKING_NOT_FOUND (5100, 404)`.
- **AC:**
  - Given token CUSTOMER, When gọi `deposit-confirmations`, Then 403.
  - Given token SERVICE và dữ liệu khớp, Then CONFIRMED, history có `paymentId`, mỗi buổi có `deposit_amount`.
  - Given gọi lại cùng `paymentId`, Then 200 và không thêm history.
  - Given số tiền lệch 1 đồng, Then 409.
  - Given endpoint cũ `deposit-paid`, Then 404.

#### PAY-09 — Preview-cancel: bộ tính phí / hoàn tiền theo bảng 2.2
**Người:** [Người A] · **Ưu tiên:** P0 · **SP:** 8 · **Sprint:** S4 · **Phụ thuộc:** PAY-04, PAY-06

- **Role:** SERVICE (Booking gọi). Booking expose preview cho khách ở BK-03.
- **API:** `POST /api/v1/refund-quotes` — `hasRole('SERVICE')`.
  - Request `RefundQuoteRequest`:
    - `bookingId`, `bookingStatus`;
    - `cancelledBy` (`CUSTOMER` / `CENTER` / `SYSTEM` / `ADMIN`);
    - `reason` (`CUSTOMER_CANCEL`, `CENTER_REJECT`, `CENTER_CANCEL`, `CENTER_LOCKED`, `CAREGIVER_MISSED`, `EXPIRED`);
    - `cancelledAt`;
    - `occurrences[]` (`{occurrenceId, startAt, status, hasSession}`) — các buổi **sẽ bị hủy**;
    - `firstUpcomingStartAt` (giờ bắt đầu buổi sớm nhất chưa diễn ra của cả booking).
  - Response `RefundQuoteResponse`: `items[]` (`{occurrenceId, priceAmount, paidAmount, feeAmount, refundAmount, rule}`), `totalPaid`, `totalFee`, `totalRefund`, `policyId`. `rule` ∈ {`NOT_PAID`, `COOLING_OFF`, `TIER_<min>_<max>`, `FULL_REFUND_CENTER`, `FULL_REFUND_MISSED`, `NOT_CANCELLABLE`}.
- **Quy tắc nghiệp vụ** (hàm thuần `RefundCalculator`, dùng `policy_snapshot` của booking; chưa cọc thì không cần snapshot):
  1. `paid_i = deposit_amount_i + charged_amount_i − refunded_amount_i` (từ `occurrence_financials`; chưa cọc thì 0).
  2. Booking ở `PENDING` / `AWAITING_DEPOSIT` → mọi buổi: fee 0, refund 0, rule `NOT_PAID`.
  3. `reason ∈ {CENTER_REJECT, CENTER_CANCEL, CENTER_LOCKED}` → buổi chưa diễn ra (không có session): fee 0, refund = `paid_i`, rule `FULL_REFUND_CENTER`.
  4. `reason = CAREGIVER_MISSED` → fee 0, refund = `paid_i`, rule `FULL_REFUND_MISSED`; buổi không tính tiền.
  5. `reason = CUSTOMER_CANCEL`:
     - (a) buổi `hasSession` hoặc status ∉ {SCHEDULED, ACKNOWLEDGED} → `NOT_CANCELLABLE` (Booking chặn bằng 409, Payment chỉ báo);
     - (b) **cooling-off**: `cancelledAt − deposit_paid_at ≤ coolingOffMinutes` **và** `firstUpcomingStartAt − cancelledAt ≥ coolingOffMinLeadHours` → fee 0, refund = `paid_i`, rule `COOLING_OFF`;
     - (c) ngược lại: `lead = startAt_i − cancelledAt` (giờ, số thực). Chọn tier `minLeadHours ≤ lead < maxLeadHours`. `fee_i = min(round(tier.feePercent% × price_i), paid_i)`; `refund_i = paid_i − fee_i`.
  6. Tổng = Σ các dòng. Không ghi DB (thuần preview).
  7. Unit test bảng: mỗi dòng của bảng 2.2 ít nhất 1 ca; biên đúng 24h / 72h (24h thuộc tier 10%, 72h thuộc tier 0%); cooling-off 60 phút đúng biên; `paid_i = 0`; fee vượt paid.
- **Lỗi:** `FINANCIALS_NOT_FOUND (6405, 404)` (booking đã CONFIRMED nhưng Payment không có dữ liệu — log `error`), `REFUND_REASON_REQUIRED (6403, 400)`.
- **AC:**
  - Given booking CONFIRMED cọc lúc T, buổi giá 500.000 (cọc 150.000) bắt đầu T + 48h, When khách hủy lúc T + 2h, Then fee 50.000, refund 100.000, rule `TIER_24_72`.
  - Given hủy lúc T + 30 phút và buổi đầu còn 30h, Then refund 150.000, rule `COOLING_OFF`.
  - Given buổi còn 10h, Then refund 0, fee 150.000.
  - Given trung tâm hủy, Then refund 150.000.

#### PAY-10 — Thực hiện hoàn tiền + API refund VNPay
**Người:** [Người A] · **Ưu tiên:** P0 · **SP:** 8 · **Sprint:** S4 · **Phụ thuộc:** PAY-09

- **Role:** SERVICE (Booking); ADMIN (xem, thử lại).
- **API:**
  - `POST /api/v1/refunds` — `hasRole('SERVICE')`.
    - Request `RefundCreateRequest`: `bookingId`, `occurrenceIds[]` (rỗng = theo complaint), `reason`, `cancelledBy`, `cancelledAt`, `firstUpcomingStartAt`, `requestedBy` (UUID actor), `idempotencyKey` (Booking tạo, vd `CANCEL:<bookingId>:<occurrenceId>`).
    - Response `RefundBatchResponse`: `items[]` (`{refundId, occurrenceId, amount, feeAmount, status}`), `totalRefund`.
  - `GET /api/v1/refunds?bookingId=&status=&page=&size=` — ADMIN; CUSTOMER chỉ booking của mình (lọc `customer_id = sub`).
  - `POST /api/v1/refunds/{id}/retry` — ADMIN, chỉ khi `FAILED`.
- **Quy tắc nghiệp vụ:**
  1. Tính lại bằng `RefundCalculator` (PAY-09). **Không tin số tiền từ Booking.**
  2. Transaction ngắn, khóa `booking_financials`:
     - mỗi buổi có `refund_i > 0` → tạo `refunds` `REFUND_PENDING`. Unique `(booking_id, coalesce(occurrence_id, 0-uuid), reason)` → trùng thì trả bản cũ (idempotent);
     - cập nhật `occurrence_financials`: `final_status`, `refunded_amount += refund_i`, ghi phí giữ lại (`fee_i` → ledger PAY-13);
     - buổi có `refund_i = 0` (fee = paid hoặc chưa trả) vẫn ghi `final_status` + ledger phí.
  3. **Gộp gọi VNPay theo payment gốc**: mỗi dòng refund trỏ `payment_id` (payment DEPOSIT, hoặc REMAINING khi hoàn phần đã thu sau hoàn thành). Dispatcher (`@Scheduled` 30 giây) gom các refund `REFUND_PENDING` cùng payment, gọi VNPay ngoài transaction:
     - `POST vnp_ApiUrl`, `vnp_Command=refund`;
     - `vnp_TransactionType=02` (toàn phần) nếu tổng refund = `payment.amount − refunded_amount`, ngược lại `03` (một phần);
     - kèm `vnp_TxnRef`, `vnp_Amount ×100`, `vnp_TransactionNo`, `vnp_TransactionDate` (= `vnp_PayDate`), `vnp_CreateBy`, `vnp_RequestId` duy nhất;
     - `vnp_SecureHash` HMAC-SHA512 trên chuỗi nối `|` theo đặc tả.
  4. `vnp_ResponseCode=00`:
     - refund → `REFUNDED`;
     - `payment.refunded_amount += …`;
     - payment → `PARTIALLY_REFUNDED` hoặc `REFUNDED`;
     - gửi thông báo cho khách "Đã hoàn X đ" (C4).
  5. Lỗi tạm thời (timeout, 5xx, mã 94 trùng request) → giữ `REFUND_PENDING`, backoff tối đa 5 lần. Lỗi nghiệp vụ (mã 02/03/91…) → `FAILED` + `last_error`, log `error`, thông báo ADMIN.
  6. Tổng refund của một payment không vượt `payment.amount` (CHECK + kiểm trong code).
  7. Sandbox VNPay có thể không hỗ trợ refund thật. Cờ `VNPAY_REFUND_MODE=SIMULATED` cho dev / test giả lập `00`; production bắt buộc `LIVE`.
- **Trạng thái:** refund `REFUND_PENDING → REFUNDED | FAILED`; `FAILED → REFUND_PENDING` (retry); payment `SUCCESS → PARTIALLY_REFUNDED → REFUNDED`.
- **DB:** `refunds`:
  - `refund_id` PK, `booking_id`, `occurrence_id` null, `payment_id` FK, `customer_id`, `center_id`;
  - `amount` numeric(14,2) CHECK > 0, `fee_amount`;
  - `reason` CHECK, `cancelled_by`, `requested_by`;
  - `status` CHECK, `idempotency_key` UNIQUE;
  - `provider_request_id`, `provider_response_code`, `attempts`, `next_attempt_at`, `last_error`, `refunded_at`;
  - `created_at`, `updated_at`, `version`;
  - unique một phần `(booking_id, occurrence_id, reason)`.
- **Lỗi:** `REFUND_EXCEEDS_PAID (6401, 409)`, `REFUND_NOT_FOUND (6400, 404)`, `REFUND_NOT_RETRYABLE (6406, 409)`, `VNPAY_REFUND_REJECTED (6302, 502 — chỉ log/hiển thị)`, `VNPAY_UNAVAILABLE (6301, 503)`.
- **AC:**
  - Given hủy 3 buổi (đã cọc 150.000/buổi; buổi 1 phí 10%, buổi 2 phí 0, buổi 3 phí 30%), When `POST /refunds`, Then tạo 2 refund (buổi 1: 100.000; buổi 2: 150.000) và 1 ledger phí cho buổi 3.
  - Given gọi lại cùng `idempotencyKey`, Then không tạo thêm.
  - Given VNPay timeout, Then refund vẫn PENDING và thành REFUNDED ở lần chạy sau.
  - Given tổng hoàn vượt số đã trả, Then 409.

#### PAY-11 — Thanh toán REMAINING (hạn 48h) và chặn khách nợ
**Người:** [Người A] · **Ưu tiên:** P1 · **SP:** 8 · **Sprint:** S5 · **Phụ thuộc:** PAY-05, PAY-06, OPS-04 (booking COMPLETED), BK-02 (kiểm nợ)

- **Role:** SERVICE (Booking tạo hóa đơn), CUSTOMER (thanh toán, xem), ADMIN (xem, miễn nợ).
- **API:**
  - `POST /api/v1/remaining-invoices` — SERVICE. Request: `bookingId`, `occurrences[]` (`{occurrenceId, finalStatus}`). Response `RemainingInvoiceResponse`.
  - `GET /api/v1/remaining-invoices?status=&page=&size=` — CUSTOMER (của mình), ADMIN (tất cả, thêm lọc `customerId`).
  - `POST /api/v1/remaining-invoices/{id}/payments` — CUSTOMER chủ hóa đơn → `PaymentSessionResponse` (dùng chung luồng PAY-05/06 với `payment_type=REMAINING`, hạn phiên 15 phút).
  - `GET /api/v1/payments/me/outstanding` — CUSTOMER → `{hasDebt, overdueInvoices[], pendingInvoices[]}`. Booking gọi bằng token khách được chuyển tiếp.
  - `PATCH /api/v1/remaining-invoices/{id}/waive` — ADMIN, bắt buộc `reason` (miễn nợ theo khiếu nại).
- **Quy tắc nghiệp vụ:**
  1. Booking gọi khi booking chuyển COMPLETED (OPS-04). Cập nhật `occurrence_financials.final_status` theo payload. Chỉ chấp nhận `finalStatus` thuộc {`COMPLETED`, `CUSTOMER_NO_SHOW`, `CANCELLED`, `MISSED`}.
  2. Với mỗi buổi:
     - `COMPLETED` → `charge_i = price_i`;
     - `CUSTOMER_NO_SHOW` → `charge_i = round(customerNoShowChargePercent% × price_i)`;
     - buổi bị hủy / MISSED → không tính (đã xử lý ở PAY-10).
  3. `remaining = Σ(charge_i − deposit_i)` với các buổi được tính tiền, chặn dưới 0 cho từng buổi. `remaining = 0` → không tạo hóa đơn, `booking_financials.status = CLOSED`.
  4. Tạo `remaining_invoices` `PENDING`, `due_at = now + remainingDueHours` (snapshot), idempotent theo `booking_id` (UNIQUE). Gửi thông báo cho khách "Vui lòng thanh toán X đ trước <due>".
  5. IPN thành công cho payment `REMAINING` → hóa đơn `PAID`, ledger ghi nhận doanh thu (PAY-13), `booking_financials.status = CLOSED`.
  6. Job mỗi 10 phút: `PENDING` có `due_at ≤ now` → `OVERDUE`; gửi thông báo nhắc nợ.
  7. Booking `create` (BK-02) gọi `GET /payments/me/outstanding`; `hasDebt=true` khi có hóa đơn `OVERDUE` → chặn đặt mới. Hóa đơn `PENDING` chưa quá hạn không chặn.
- **Trạng thái:** invoice `PENDING → PAID`; `PENDING → OVERDUE → PAID`; `PENDING/OVERDUE → WAIVED` (admin).
- **DB:** `remaining_invoices`:
  - `invoice_id` PK, `booking_id` UNIQUE, `customer_id`, `center_id`;
  - `amount`, `due_at`, `status` CHECK;
  - `payment_id` null, `paid_at`, `waived_by`, `waive_reason`;
  - `created_at`, `updated_at`, `version`;
  - index `(customer_id, status)`, `(status, due_at)`.
- **Lỗi:** `INVOICE_NOT_FOUND (6500, 404)`, `INVOICE_NOT_PAYABLE (6501, 409)`, `INVALID_FINAL_STATUS (6502, 400)`, `WAIVE_REASON_REQUIRED (6503, 400)`.
- **AC:**
  - Given booking 3 buổi giá 300.000, cọc 90.000/buổi, buổi 2 bị hủy (đã hoàn), buổi 3 CUSTOMER_NO_SHOW 100%, When COMPLETED, Then remaining = (300.000 − 90.000) × 2 = 420.000.
  - Given quá 48h chưa trả, Then OVERDUE và `POST /bookings` của khách trả 409 `CUSTOMER_HAS_OUTSTANDING_DEBT`.
  - Given trả xong, Then đặt mới được.

#### PAY-12 — Phí CUSTOMER_NO_SHOW + hoàn tiền theo khiếu nại
**Người:** [Người A] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S5 · **Phụ thuộc:** PAY-10, PAY-11, OPS-06, FB-02

- **Role:** SERVICE (Booking); ADMIN (gián tiếp qua FB-02).
- **API:** `POST /api/v1/refunds/complaints` — SERVICE.
  - Request: `bookingId`, `complaintId`, `amount` (> 0), `decidedBy`, `note`.
  - Response: `RefundBatchResponse`.
- **Quy tắc nghiệp vụ:**
  1. **Khách vắng mặt:** Booking báo `finalStatus=CUSTOMER_NO_SHOW` trong PAY-11. Không có refund. Phần `charge_i − deposit_i` vào REMAINING. Ledger ghi `NO_SHOW_CHARGE`.
  2. **Khiếu nại được chấp nhận:**
     - `amount ≤ Σ paid toàn booking − Σ đã hoàn`. Vượt → 409 `REFUND_EXCEEDS_PAID`.
     - Phân bổ vào payment theo thứ tự REMAINING trước, rồi DEPOSIT.
     - Tạo refund lý do `COMPLAINT_ACCEPTED`, idempotent theo `complaintId`; dispatcher PAY-10 gọi VNPay.
     - Ghi ledger đảo chiều (`REFUND`) chia theo tỉ lệ hoa hồng snapshot.
  3. Hóa đơn REMAINING còn `PENDING` / `OVERDUE` → không gọi VNPay mà **giảm** hóa đơn trước. Phần dư (nếu có) mới hoàn tiền.
- **Lỗi:** `REFUND_EXCEEDS_PAID (6401, 409)`, `COMPLAINT_REFUND_EXISTS (6407, 409)`.
- **AC:**
  - Given khiếu nại được chấp nhận hoàn 200.000 trên booking đã trả 900.000, Then tạo refund 200.000 lý do `COMPLAINT_ACCEPTED`.
  - Given gửi lại cùng `complaintId`, Then không tạo thêm.
  - Given đề nghị 1.000.000, Then 409.

#### PAY-13 — Hoa hồng & sổ cái (ledger)
**Người:** [Người A] · **Ưu tiên:** P1 · **SP:** 5 · **Sprint:** S5 · **Phụ thuộc:** PAY-06, PAY-10, PAY-11

- **Role:** nội bộ; ADMIN đọc qua PAY-14/15.
- **Quy tắc nghiệp vụ:** ghi nhận khi **tiền đã thu và quyền giữ tiền đã xác lập** (điểm mở O6).

  | Sự kiện | `entry_type` | `gross` |
  |---|---|---|
  | Buổi COMPLETED khi booking COMPLETED (phần cọc đã thu của buổi) | `SERVICE_REVENUE` | `deposit_i` |
  | Hóa đơn REMAINING `PAID` | `SERVICE_REVENUE` | phân bổ theo từng buổi `charge_i − deposit_i` |
  | Phí hủy giữ lại (PAY-10) | `CANCELLATION_FEE` | `fee_i` |
  | Khách vắng mặt | `NO_SHOW_CHARGE` | phần cọc + phần REMAINING khi thu |
  | Hoàn tiền theo khiếu nại | `REFUND_ADJUSTMENT` | âm |

  1. Với mọi entry: `platform_amount = round(gross × commissionPercent% snapshot)`, `center_amount = gross − platform_amount`.
  2. Ledger **chỉ ghi thêm** (append-only), không sửa. Sai thì ghi bút toán đảo.
  3. Idempotent theo `(source_type, source_id, entry_type, occurrence_id)` UNIQUE.
- **DB:** `ledger_entries`:
  - `entry_id` PK, `booking_id`, `occurrence_id` null, `center_id`;
  - `entry_type` CHECK, `source_type` (`PAYMENT` / `REFUND` / `INVOICE` / `BOOKING`), `source_id`;
  - `gross_amount`, `platform_amount`, `center_amount` (cho phép âm), `commission_percent`;
  - `occurred_at`, `settlement_id` null, `created_at`;
  - index `(center_id, occurred_at)`, `(settlement_id)`.
- **Lỗi:** —
- **AC:**
  - Given hoa hồng 15% và phí hủy 50.000, Then ledger `platform 7.500 / center 42.500`.
  - Given sự kiện lặp, Then không nhân đôi.
  - Given admin đổi hoa hồng sau khi khách đã cọc, Then entry dùng % trong snapshot.

#### PAY-14 — Đối soát (Settlement) theo kỳ
**Người:** [Người A] · **Ưu tiên:** P2 · **SP:** 8 · **Sprint:** S6 · **Phụ thuộc:** PAY-13

- **Role:** ADMIN (tạo, xác nhận, đánh dấu đã chuyển); CENTER_MANAGER (xem của trung tâm mình, claim `center_id`).
- **API:**
  - `GET /api/v1/settlements?centerId=&status=&from=&to=&page=&size=` — ADMIN; MANAGER bị ép `centerId` = claim.
  - `GET /api/v1/settlements/{id}` — kèm danh sách ledger entry, phân trang `/entries`.
  - `POST /api/v1/settlements/generate` — ADMIN. Request: `periodStart`, `periodEnd` (date, theo `Asia/Ho_Chi_Minh`), `centerId?`.
  - `PATCH /api/v1/settlements/{id}/confirm` — ADMIN.
  - `PATCH /api/v1/settlements/{id}/paid` — ADMIN. Request: `transferReference` (`@NotBlank`), `paidAt`.
- **Quy tắc nghiệp vụ:**
  1. Job đầu kỳ (ngày 1 hằng tháng 02:00 giờ VN khi `settlementCycle=MONTHLY`, thứ Hai khi `WEEKLY`) tự gọi generate cho kỳ trước. Admin cũng gọi tay được.
  2. Generate: chỉ cho kỳ đã kết thúc (`periodEnd < hôm nay`). Với mỗi trung tâm có ledger `settlement_id IS NULL` và `occurred_at` trong kỳ:
     - tạo settlement `DRAFT`: `gross = Σ gross`, `platform_fee = Σ platform`, `center_payable = Σ center`;
     - gắn `settlement_id` cho các entry.

     Transaction theo từng trung tâm. Unique `(center_id, period_start, period_end)` → chạy lại thì bỏ qua.
  3. Entry đến trễ (vd hoàn tiền sau khi đã chốt kỳ) rơi vào kỳ sau (`occurred_at` mới).
  4. `DRAFT → CONFIRMED` (admin rà xong; khóa không thêm entry) → `PAID` (nhập mã chuyển khoản). Không có chiều ngược lại.
  5. Gửi thông báo cho manager khi `CONFIRMED` và khi `PAID`.
- **DB:** `settlements`:
  - `settlement_id` PK, `center_id`, `period_start` date, `period_end` date;
  - `gross_amount`, `platform_fee`, `center_payable`, `entry_count`;
  - `status` CHECK `DRAFT` / `CONFIRMED` / `PAID`;
  - `confirmed_by/at`, `paid_by/at`, `transfer_reference`;
  - `created_at`, `updated_at`, `version`;
  - UNIQUE `(center_id, period_start, period_end)`.
- **Lỗi:** `SETTLEMENT_NOT_FOUND (6600, 404)`, `SETTLEMENT_PERIOD_NOT_CLOSED (6601, 400)`, `SETTLEMENT_INVALID_TRANSITION (6602, 409)`, `TRANSFER_REFERENCE_REQUIRED (6604, 400)`.
- **AC:**
  - Given ledger tháng 10 của C1 = 10.000.000 gross, hoa hồng 15%, When generate tháng 10, Then settlement C1 DRAFT `center_payable` 8.500.000.
  - Given generate lại, Then không trùng.
  - Given manager C2 xem settlement của C1, Then 404.
  - Given `PAID` → `confirm`, Then 409.

#### PAY-15 — Thống kê doanh thu toàn nền tảng và theo trung tâm
**Người:** [Người A] · **Ưu tiên:** P2 · **SP:** 5 · **Sprint:** S6 · **Phụ thuộc:** PAY-13

- **Role:** ADMIN (toàn hệ thống); CENTER_MANAGER (trung tâm mình).
- **API:**
  - `GET /api/v1/revenue/summary?from=&to=&centerId=` → `{grossCollected, refunded, netCollected, serviceRevenue, cancellationFees, noShowCharges, platformFee, centerPayable, outstandingRemaining, overdueRemaining}`.
  - `GET /api/v1/revenue/timeseries?from=&to=&granularity=DAY|WEEK|MONTH&centerId=` → `[{periodStart, gross, platformFee, refunds}]`.
  - `GET /api/v1/revenue/by-center?from=&to=&page=&size=&sort=gross,desc` — chỉ ADMIN.
- **Quy tắc nghiệp vụ:**
  1. Nguồn số liệu: `ledger_entries` (doanh thu ghi nhận), `payments` / `refunds` (dòng tiền), `remaining_invoices` (công nợ).
  2. Quy đổi ngày theo `Asia/Ho_Chi_Minh`. Khoảng `[from, to]` tối đa 366 ngày.
  3. MANAGER: `centerId` bị ép theo claim; truyền `centerId` khác → 403 `CENTER_SCOPE_REQUIRED`.
  4. Truy vấn tổng hợp bằng `@Query` có `@Param`, không load entity vào bộ nhớ.
- **Lỗi:** `INVALID_DATE_RANGE (6700, 400)`, `CENTER_SCOPE_REQUIRED (6007, 403)`.
- **AC:**
  - Given dữ liệu mẫu 2 trung tâm, When admin gọi `by-center`, Then tổng các dòng = `summary` toàn hệ thống.
  - Given manager C1, Then chỉ thấy C1.
  - Given `from > to`, Then 400.

#### PAY-16 — Lịch sử giao dịch
**Người:** [Người A] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S6 · **Phụ thuộc:** PAY-06, PAY-10

- **Role:** CUSTOMER (của mình), CENTER_MANAGER (booking trung tâm mình), ADMIN (tất cả).
- **API:**
  - `GET /api/v1/payments?bookingId=&type=&status=&from=&to=&page=&size=` → `PaymentResponse` (`id`, `bookingId`, `type`, `amount`, `status`, `paidAt`, `refundedAmount`, `bankCode`, `providerTransactionNo` — đã che bớt).
  - `GET /api/v1/payments/{id}` (đã có ở PAY-06; bổ sung scope MANAGER).
- **Quy tắc nghiệp vụ:**
  1. Phạm vi:
     - CUSTOMER: `customer_id = sub`;
     - MANAGER: `center_id = claim`;
     - ngoài phạm vi → 404.
  2. Không bao giờ trả `payload` thô của VNPay hay `vnp_SecureHash`.
  3. Phân trang tối đa 100 dòng, sắp `created_at desc`.
- **Lỗi:** `PAYMENT_NOT_FOUND (6200, 404)`, `INVALID_PAGE (6003, 400)`.
- **AC:**
  - Given khách K1, When xem payment của K2, Then 404.
  - Given manager C1, Then chỉ thấy payment có `center_id = C1`.

#### PAY-17 — Kiểm thử end-to-end Payment + tài liệu sandbox
**Người:** [Người A] · **Ưu tiên:** P1 · **SP:** 3 · **Sprint:** S6 · **Phụ thuộc:** PAY-01 → PAY-16, INF-01 (khung e2e)

- **Nội dung:**
  1. Thêm phase `g` vào `scripts/e2e/e2e.py`: tạo booking → accept → tạo phiên cọc → giả lập IPN (tự ký bằng hash secret test) → CONFIRMED → hủy 1 buổi (preview + refund `SIMULATED`) → hoàn thành các buổi còn lại (dùng API OPS) → REMAINING → giả lập IPN → hóa đơn PAID → generate settlement.
  2. Test tích hợp `PaymentApiTests`, gồm: IPN trùng, sai chữ ký, sai số tiền, Booking down (outbox), refund idempotent, nợ quá hạn chặn booking (stub Booking).
  3. README PaymentService: cách dùng VNPay sandbox (tài khoản thẻ test, ngrok cho IPN), bảng biến môi trường, bảng mã `RspCode`.
- **AC:** Given stack e2e, When `Run-E2E.ps1`, Then phase g PASSED.

---

## 5. GÓI CHỌN THÊM CHO [Người A]

**Điều kiện kích hoạt: KHÔNG thỏa ở phân bổ mặc định.**
- Gói Payment (91 SP) nhẹ hơn [Người B] (104) nhưng **nặng hơn** [Người C] (74), nên không phải "nhẹ hơn phần của 2 người còn lại". [Người A] **không** nhận thêm task.

**Mục này chỉ kích hoạt khi** nhóm quyết định dời PAY-14 (Settlement, 8 SP) và PAY-15 (Thống kê doanh thu, 5 SP) sang giai đoạn sau.
- Khi đó gói Payment còn **78 SP**, và [Người A] chọn **một** nhóm dưới đây.
- Nhóm không được chọn **giữ nguyên chủ hiện tại**.

| Nhóm | Task (SP) | Tổng | Phụ thuộc với Payment | Lý do phù hợp | Không được chọn thì |
|---|---|---|---|---|---|
| **G1 — Danh tính service & cổng** (khuyến nghị) | IAM-01 (5), GW-01 (3) | 8 | **Rất chặt**: PAY-06/08/10/11 cần token SERVICE; IPN cần route public + CORS ở Gateway | [Người A] là bên dùng token service nhiều nhất; đồng thời **giảm tải cho [Người B]**, người đang nặng nhất | Giữ ở [Người B] |
| **G2 — Thông báo** | NTF-01 (5), NTF-02 (3) | 8 | **Trung bình**: Payment gửi thông báo cọc thành công, hoàn tiền, nhắc nợ qua C4 | Hợp đồng gọn, tách biệt | Giữ ở [Người C] |
| **G3 — Khóa trung tâm dây chuyền** | PRV-04 (5), IAM-06 (3) | 8 | **Chặt**: khóa trung tâm sinh hoàn tiền `CENTER_LOCKED` 100% | Hiểu rõ luồng tiền khi hủy hàng loạt | Giữ ở [Người C] |

**Cân bằng sau khi chọn** (sau khi dời 13 SP; tổng còn 256 SP):

| [Người A] chọn | [Người A] | [Người B] | [Người C] | Nhận xét |
|---|---|---|---|---|
| G1 | 86 | 96 | 74 | Giữ đúng định hướng "B chính, C nhẹ" mà B bớt quá tải → **khuyến nghị** |
| G2 | 86 | 104 | 66 | B vẫn quá tải, C càng nhẹ |
| G3 | 86 | 104 | 66 | Như G2 |

Nếu chọn G1, bảng phân việc phải điều chỉnh: **G1 phải xong trong sprint 1** vì là blocker. Do đó [Người A] làm IAM-01 trước PAY-02, còn [Người B] chuyển CAT-02 sang S1.

---

## Lịch sprint của [Người A]

| Sprint | Task (SP) | Mục tiêu demo chung cuối sprint |
|---|---|---|
| **S1** | 🔒 PAY-01 (5), PAY-02 (5), **PAY-08 bước 1** (hotfix SEC-01) — 10 SP | Hết toàn bộ lỗ hổng SEC-01…08; có token SERVICE; PaymentService chạy trong Docker; publish stub contract §7 |
| **S2** | PAY-03 (3), PAY-04 (3), PAY-05 (8) — 14 SP | Báo giá có giá từng buổi; khách nhận URL VNPay; caregiver acknowledge; thông báo in-app + SSE |
| **S3** | 🔒 PAY-06 (8), PAY-07 (3), PAY-08 (5) — 16 SP | **Luồng chính end-to-end**: đặt → duyệt → cọc VNPay sandbox → CONFIRMED → check-in → task → check-out → COMPLETED; xác minh chứng chỉ 2 bước |
| **S4** | 🔒 PAY-09 (8), 🔒 PAY-10 (8) — 16 SP | Kiểm lịch rảnh / kỹ năng / nợ khi đặt; MISSED + khách vắng tự động hoàn tiền; care report có ảnh; đánh giá caregiver; preview hoàn tiền đúng bảng 2.2 |
| **S5** | PAY-11 (8), PAY-12 (3), PAY-13 (5) — 16 SP | Tìm caregiver đủ dịch vụ / lịch trống; hủy từng buổi / cả booking có hoàn tiền; REMAINING + chặn nợ; khiếu nại có escalate |
| **S6** | PAY-14 (8), PAY-15 (5), PAY-16 (3), PAY-17 (3) — 19 SP | Khóa trung tâm dây chuyền (hủy hệ thống + hoàn 100%); lịch sử booking; đối soát; dashboard doanh thu / vận hành; e2e toàn hệ thống PASSED |
| **Tổng** | **91** |  |

---

## Contract [Người A] cài đặt hoặc sử dụng (trích §7)

Ký hiệu `[A]`, `[B]`, `[C]` là người cài đặt / gọi. Chi tiết đầy đủ và các contract còn lại: `docs/WORK_PLAN.md` §7.

### C2 — Booking → Payment

| # | Chữ ký | Cài đặt | Gọi bởi | Khi nào |
|---|---|---|---|---|
| C2.1 | `GET /api/v1/payment-policies/effective?centerId&at` → `PolicySnapshotResponse {policyId, depositPercent, commissionPercent, refundTiers[], coolingOffMinutes, coolingOffMinLeadHours, customerNoShowChargePercent, remainingDueHours, paymentSessionTtlMinutes, depositDueHours}` | [A] PAY-03 | [B] BK-01 (báo giá); [B] accept → `deposit_due_at` theo `depositDueHours` | Báo giá, accept |
| C2.2 | `POST /api/v1/refund-quotes` (SERVICE) `RefundQuoteRequest {bookingId, bookingStatus, cancelledBy, reason, cancelledAt, firstUpcomingStartAt, occurrences[{occurrenceId, startAt, status, hasSession}]}` → `RefundQuoteResponse {items[{occurrenceId, priceAmount, paidAmount, feeAmount, refundAmount, rule}], totalPaid, totalFee, totalRefund, policyId}` | [A] PAY-09 | [B] BK-03 | Preview hủy |
| C2.3 | `POST /api/v1/refunds` (SERVICE) `RefundCreateRequest {bookingId, occurrenceIds[], reason, cancelledBy, cancelledAt, firstUpcomingStartAt, requestedBy, idempotencyKey}` → `RefundBatchResponse {items[{refundId, occurrenceId, amount, feeAmount, status}], totalRefund}` | [A] PAY-10 | [B] BK-03, BK-04, OPS-06 (`CAREGIVER_MISSED`) | Sau commit hủy / MISSED |
| C2.4 | `POST /api/v1/refunds/complaints` (SERVICE) `{bookingId, complaintId, amount, decidedBy, note}` → `RefundBatchResponse` | [A] PAY-12 | [C] FB-02 | Sau admin-resolve |
| C2.5 | `POST /api/v1/remaining-invoices` (SERVICE) `{bookingId, occurrences[{occurrenceId, finalStatus}]}` → `RemainingInvoiceResponse {invoiceId?, amount, dueAt, status}` (amount 0 → không tạo hóa đơn, `invoiceId` null) | [A] PAY-11 | [B] OPS-04 | Booking COMPLETED |
| C2.6 | `GET /api/v1/payments/me/outstanding` (CUSTOMER, token khách chuyển tiếp) → `{hasDebt, overdueInvoices[], pendingInvoices[]}`; `GET /api/v1/payments?bookingId=` (thêm quyền SERVICE) → danh sách payment + `refundedAmount` | [A] PAY-11, PAY-16 | [B] BK-02; [C] FB-02 | Tạo booking; trước admin-resolve |

### C3 — Payment → Booking

| # | Chữ ký | Cài đặt | Gọi bởi | Khi nào |
|---|---|---|---|---|
| C3.1 | `POST /api/v1/bookings/{id}/deposit-confirmations` (SERVICE) `DepositConfirmationRequest {paymentId, amount, paidAt, policySnapshot, occurrenceDeposits[{occurrenceId, depositAmount}]}` → `BookingDetailResponse`; idempotent theo `paymentId` | [A] PAY-08 (file Booking, [B] review) | [A] PAY-06 (outbox) | IPN thành công — thay sự kiện `PaymentSucceeded(bookingId)` |
| C3.2 | `GET /api/v1/bookings/{id}/payment-context` (SERVICE) → `{bookingId, bookingCode, customerId, centerId, status, totalAmount, depositDueAt, firstStartAt, depositPaidAt, occurrences[{occurrenceId, occurrenceNo, startAt, endAt, status, priceAmount, depositAmount, caregiverId, hasSession}]}` | [A] PAY-08 (dữ liệu giá từ [B] BK-01) | [A] PAY-05, PAY-09 | Tạo phiên, tính hoàn |

### C4 — Thông báo (BookingService)

| # | Chữ ký | Cài đặt | Gọi bởi |
|---|---|---|---|
| C4.2 | `POST /api/v1/notifications` (SERVICE) `{recipientUserIds[], type, title, body, refType, refId}` → `{created}` | [C] NTF-01 | [A] PAY-06/10/11/14 |
