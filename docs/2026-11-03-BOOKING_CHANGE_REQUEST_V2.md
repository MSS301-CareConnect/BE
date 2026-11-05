# Yêu cầu thay đổi booking v2 (2026-11-03)

> Bổ sung cho `2026-11-03-BOOKING_FLOW_V1.md` (luồng hiện hành). Sáu điểm chủ dự án yêu cầu, quyết định đã chốt ngày
> 2026-11-03. Chưa commit (chủ dự án tự commit). Chưa chạy E2E trên trình duyệt thật với backend.

## 1. Quyết định đã chốt

| # | Yêu cầu | Quyết định |
|---|---|---|
| CR-1 | Bước 3: định kỳ hiện danh sách buổi sửa được như "Tự chọn buổi"; "Tự chọn" có thêm nhanh | Lịch lặp sinh danh sách buổi sửa được từng buổi; sửa rồi thì gửi `slots` kèm quy tắc, booking vẫn là RECURRING. "Tự chọn" có khung **Thêm nhanh** (hằng ngày / hằng tuần) |
| CR-2 | Đổi địa chỉ người được chăm sóc sau khi đặt | **Khách + manager**, bất kỳ lúc nào booking còn giữ chỗ, có buổi sắp tới và **không có buổi IN_PROGRESS**; áp cho mọi buổi chưa bắt đầu; báo trung tâm + caregiver |
| CR-3 | Khách chọn caregiver → caregiver tự nhận, không qua trung tâm; để trung tâm chọn → trung tâm duyệt | Trạng thái mới `PENDING_CAREGIVER_CONFIRMATION`. Caregiver **từ chối / quá hạn**: RECURRING / CUSTOM → trung tâm phân người khác (`PENDING_CAREGIVER_ASSIGNMENT`), SHIFT → `REJECTED` (từ chối) / `EXPIRED` (quá hạn). Web caregiver có trang "Yêu cầu"; **app mobile chưa làm** |
| CR-4 | Cọc bị giữ khi hủy: chưa phân người → trung tâm hưởng; có người nhận → caregiver hưởng toàn bộ | Sổ `deposit_forfeitures` ở PaymentService, **theo từng buổi** (mỗi buổi có thể khác caregiver). Áp cho khách hủy sau mốc (`AFTER_THRESHOLD`, cả `BEFORE_THRESHOLD` khi % hoàn < 100) và quá hạn phần còn lại (`PAYMENT_OVERDUE`). Doanh thu trung tâm trừ phần của caregiver; caregiver xem ở web, manager thấy tách hai phần |
| CR-5 | Trang chủ: footer chìm + trùng nội dung, mất ảnh thứ hai, nhiều chữ | Footer dải màu thương hiệu đậm; bỏ mục hotline/giờ trùng ở thân trang (thay bằng "Thanh toán & hoàn tiền minh bạch"); khôi phục section ảnh (JPEG 145 KB) với 3 ý ngắn |
| CR-6 | Cơ chế chuyển đổi giữa nhiều trung tâm | "Tất cả trung tâm" (mặc định) ↔ "Đang xem: X"; trang `/customer/centers` và `/customer/centers/:id`; chỉ thao tác chọn rõ ràng mới đổi trung tâm (kèm toast); API danh sách / chi tiết trung tâm mở cho khách vãng lai |

## 2. Thay đổi BE

### BookingService

- **CR-1** `OccurrenceGeneratorImpl`: RECURRING nhận thêm `slots` (danh sách đã sửa). Quy tắc vẫn phải hợp lệ; mọi buổi phải
  nằm trong `[startDate, endDate]` theo giờ Việt Nam → 400 `5243 SLOT_OUTSIDE_RECURRENCE_RANGE`; sau đó áp luật như
  CUSTOM (≤ 60 buổi, ≥ 1 giờ, không chồng giờ, đặt trước tối thiểu theo policy RECURRING). Báo giá dùng chung đường này.
- **CR-3** state machine (`BookingAction`):

  | Hành động | Từ | Tới | Ai |
  |---|---|---|---|
  | `CAREGIVER_ACCEPT` | PENDING_CAREGIVER_CONFIRMATION | AWAITING_INITIAL_PAYMENT | caregiver khách chọn (đặt hạn khoản đầu) |
  | `CAREGIVER_DECLINE` | PENDING_CAREGIVER_CONFIRMATION | REJECTED | caregiver, đơn SHIFT |
  | `HAND_OVER_TO_CENTER` | PENDING_CAREGIVER_CONFIRMATION | PENDING_CAREGIVER_ASSIGNMENT | caregiver từ chối hoặc scheduler quá hạn, RECURRING / CUSTOM còn trước hạn trả phần còn lại; buổi RESERVED → PENDING_ASSIGNMENT, bỏ caregiver, ghi `CAREGIVER_CHANGED`, đặt `assignment_due_at` |
  | `REJECT`, `CANCEL_BY_CUSTOMER`, `CANCEL_BY_SYSTEM`, `EXPIRE` | + PENDING_CAREGIVER_CONFIRMATION | như cũ | manager có thể từ chối thay |

  Tạo booking có `caregiverId` → `PENDING_CAREGIVER_CONFIRMATION` (hạn trả lời = `confirmationTimeoutMinutes`, không quá
  mốc chốt), báo caregiver (`CAREGIVER_REQUEST`) và trung tâm. Manager `accept` trên trạng thái này → 409 `5101`.
- **CR-2** `PATCH /bookings/{id}/service-address` thay `service_address` + phần địa chỉ trong `recipient_snapshot` (bỏ
  tọa độ cũ), ghi `booking_events` `SERVICE_ADDRESS_CHANGED` (`old_value` / `new_value`, `reason` = ghi chú), báo khách,
  trung tâm, caregiver các buổi sắp tới (`BOOKING_ADDRESS_CHANGED`). Lịch sử booking trả `oldValue` / `newValue`.
- **CR-4** `payment-context` thêm `caregiverUserId` từng buổi; lệnh hoàn (`RefundCreateRequest`) thêm
  `caregivers[{occurrenceId, caregiverId, caregiverUserId}]` của các buổi được hoàn.

| Endpoint mới | Role | Ghi chú |
|---|---|---|
| `GET /bookings/caregiver-requests?page&size` | CAREGIVER | đơn chờ trả lời của chính mình; khu vực (phường/quận/tỉnh), ghi chú, dịch vụ, buổi, tổng tiền. **2026-11-05 (chủ dự án chọn):** thêm tên khách đặt (`customerName`), tên + tình trạng sức khỏe người được chăm sóc (`recipientName`, `mobilityLevel`, `independenceLevel`, `chronicConditions`, `allergies`, `careRequirements`, `dietaryNotes`, `behaviorNotes`, `medicationCount`). Vẫn **không** số nhà, SĐT, liên hệ khẩn cấp, tên / liều thuốc trước khi CONFIRMED |
| `GET /bookings/caregiver-requests/{id}` | CAREGIVER | (2026-11-05) chi tiết một đơn còn chờ chính mình; đã trả lời / quá hạn / người khác → 404 |
| `POST /bookings/{id}/caregiver-accept` | CAREGIVER | người khác → 404; quá hạn / sai trạng thái → 409 `5101` |
| `POST /bookings/{id}/caregiver-decline` `{reason}` | CAREGIVER | |
| `PATCH /bookings/{id}/service-address` | CUSTOMER (của mình), CENTER_MANAGER (trung tâm mình) | `{addressLine*, ward, district, province*, note}`; 400 `5113` / `5114`; 409 `5115` |

Thông báo mới: `CAREGIVER_REQUEST`, `BOOKING_HANDED_OVER`, `BOOKING_ADDRESS_CHANGED`.

### PaymentService (CR-4)

- `occurrence_financials.caregiver_id / caregiver_user_id`: chụp từ payment-context lúc mở phiên khoản đầu, cập nhật theo
  `caregivers` của lệnh hoàn (Booking là nguồn sự thật khi đã đổi người).
- `deposit_forfeitures` (append-only): mỗi buổi một dòng khi phí giữ > 0, `beneficiary` CAREGIVER nếu buổi có caregiver,
  ngược lại CENTER; idempotent theo `(idempotency_key, occurrence_id)`. Ghi trong cùng transaction lệnh hoàn
  (`DepositForfeitureService.record`, MANDATORY) — kể cả dòng hoàn 0 đ (trước đây phí của dòng này không ghi đâu cả).
- `GET /payments/revenue` thêm `caregiverForfeitAmount`, `centerForfeitAmount`; `totalRevenue` = đã thu − đã hoàn − phần
  caregiver (theo `paid_at` của khoản đầu, cùng mốc với đã thu / đã hoàn).
- `GET /deposit-forfeitures?from&to&page&size` và `/summary` — CAREGIVER phần mình, CENTER_MANAGER trung tâm trong token,
  ADMIN tất cả / `centerId`. Gateway đã route `/api/v1/deposit-forfeitures/**`.

### ProviderService + ApiGateway (CR-6)

`GET /care-centers/public` và `/public/{centerId}` công khai (chỉ GET, chỉ trung tâm ACTIVE, DTO không có dữ liệu riêng
tư; token cũ / hỏng bị bỏ qua). Dịch vụ, caregiver và policy của trung tâm **vẫn cần đăng nhập**.

### Schema (áp tay, theo thứ tự)

1. Booking: `BookingService/schema/changes/2026-11-03_booking_change_request_v2.sql` (CHECK `bookings.status`,
   `booking_events` + 2 cột / loại mới, `notifications.type`, index). `booking.sql` đã cập nhật.
2. Payment: `PaymentService/schema/changes/2026-11-03-deposit-forfeitures.sql` (chạy lại được). `payment.sql` đã cập nhật.
   Phần cọc bị giữ trước script này không có trong sổ (vẫn tính vào doanh thu trung tâm như cũ).

## 3. Thay đổi FE

- Bước 3 đặt lịch: `schedule-rule-fields`, `schedule-slot-list`, `schedule-quick-add`, `hooks/schedule-slots.ts`;
  RECURRING giữ `recurringSlots` + `recurringEdited` (sửa rồi thì đổi quy tắc không ghi đè, có nút "Tạo lại theo lịch
  lặp"); bỏ dòng "Tổng N buổi…". Đổi trung tâm trên header khi đang ở form → form chuyển trung tâm và xóa lựa chọn cũ.
- Trạng thái `PENDING_CAREGIVER_CONFIRMATION` ở mọi nơi (badge, nhóm danh sách, stepper, panel bước tiếp theo, manager hiện
  "chờ người chăm sóc trả lời" + nút từ chối).
- `entities/booking/service-address-dialog.tsx`: popup 2 cột "Đổi địa chỉ chăm sóc" ở chi tiết booking của khách (menu ⋯)
  và manager; lịch sử hiện "Đổi địa chỉ: cũ → mới".
- Caregiver web: `/caregiver/requests` (tab "Yêu cầu" có badge, nhận / từ chối kèm lý do), `/caregiver/earnings` (menu tài
  khoản "Tiền cọc được hưởng": tổng theo tháng / 30 ngày / năm và từng khoản).
- Manager thống kê: thêm "Cọc bị giữ — trung tâm hưởng / người chăm sóc hưởng".
- CR-5 / CR-6: xem `FE-careconnect/docs/2026-11-03-CENTERS_AND_LANDING_PROGRESS.md`.

### Bổ sung CR-1b (sau góp ý, 2026-11-03)

- Danh sách buổi (Định kỳ và Tự chọn) có ô chọn từng buổi + "Chọn tất cả"; thanh "Sửa các buổi đã chọn" đặt cùng giờ
  bắt đầu / kết thúc hoặc xóa các buổi đã chọn (`schedule-bulk-bar.tsx`, `updateSlots` / `removeSlots`).
- Thời lượng lệch: buổi có độ dài khác thời lượng phổ biến nhất được tô vàng, ghi "Dài 3 giờ, các buổi khác 2 giờ" kèm
  nút "Đưa về 2 giờ", và có thông báo chung phía trên danh sách (`durationOutliers`). Chỉ cảnh báo, không chặn đặt lịch.
- Lỗi báo giá: các buổi dài khác nhau thì BE trả `unitsPerOccurrence = null` và FE hiện "0 giờ × N buổi". BE thêm
  `quantity` (tổng đơn vị) vào dòng báo giá; FE hiện "Tổng 7 giờ · 3 buổi dài khác nhau".

- Báo giá chia theo thời lượng (góp ý thứ hai): mỗi dòng dịch vụ có `unitGroups` (số giờ mỗi buổi → số buổi → thành tiền,
  tính ở `BookingPriceCalculatorImpl`); FE hiện "2 giờ × 14 buổi · 2.800.000 ₫", "3 giờ × 1 buổi · 300.000 ₫" (nhóm ít buổi
  hơn được tô vàng) và dòng "Cộng 31 giờ · 15 buổi". Thành tiền nhóm làm tròn đến đồng riêng nên tổng các nhóm có thể lệch
  tổng dòng 1 đồng khi giờ lẻ.

## 4. Kiểm thử

| Phần | Kết quả |
|---|---|
| BookingService `mvnw -o test` | 122 test, 0 lỗi (2 skip có sẵn). Mới: `chosenCaregiverAcceptsTheBookingWithoutTheCenter`, `declinedOrUnansweredRecurringBookingIsHandedOverToTheCenter`, `customerChangesCareAddressOfUpcomingVisits`, `recurringUsesEditedSlotsWithinTheRuleRange` |
| PaymentService | 49 test, 0 lỗi. Mới: `keptDepositGoesToTheCaregiverOfEachOccurrenceOrTheCenter` (3 buổi: 2 caregiver khác nhau + 1 không có, gửi lặp không ghi trùng, phạm vi theo role, doanh thu) |
| ProviderService / ApiGateway | 72 / 21 test, 0 lỗi (gateway có vài lỗi 500 ngẫu nhiên do HttpClient JDK ở lần chạy đầu, chạy lại xanh) |
| FE `pnpm lint`, `typecheck`, `test`, `build` | sạch; 395 test xanh. `prettier --check` chỉ báo `pnpm-lock.yaml` (có sẵn từ trước, không đổi) |

## 5. Còn lại / rủi ro

- **Chủ dự án**: áp 2 change script, build lại booking / payment / provider / gateway, commit.
- **App mobile caregiver** chưa có màn nhận / từ chối đơn (chỉ web). Caregiver dùng mobile vẫn nhận thông báo
  `CAREGIVER_REQUEST`; quá hạn thì đơn tự chuyển trung tâm (RECURRING / CUSTOM) hoặc hết hạn (SHIFT).
- Quá hạn phần còn lại dùng caregiver đã chụp lúc khách trả cọc; nếu trung tâm đổi người sau đó, khoản giữ cọc của case này
  vẫn ghi cho người cũ (hủy thì đúng vì Booking gửi caregiver lúc hủy).
- Lịch tháng của admin vẫn vẽ RECURRING theo quy tắc, chưa theo danh sách đã sửa (giống T6/G15 của CUSTOM).
- Chưa có chi trả thật cho caregiver (chỉ sổ ghi nhận) — đối soát / payout vẫn ngoài phạm vi (D15).
- Chưa chạy E2E thật trên trình duyệt.

## 6. Commit đề xuất (chủ dự án tự commit)

- BE: `feat(booking,payment): caregiver confirms chosen bookings, editable recurring visits, care address change and kept deposits credited to the visit caregiver 3/11`
- BE (agent): `feat(provider,gateway): open the ACTIVE center directory to anonymous visitors and route /deposit-forfeitures to PaymentService 3/11`
- FE: `feat(booking,caregiver): editable recurring visits and quick add, caregiver booking requests and kept deposits, care address change 3/11`
- FE (agent): `feat(customer): public center directory and detail pages, explicit center switching, dark footer and restored landing photo section 3/11`

## Bổ sung 2026-11-05 — chi tiết buổi cho caregiver

`GET /booking-occurrences/{id}` (chi tiết) thêm `customerName`, `bookingType`, `caregiverScheduleId` (SHIFT), `priceAmount` và `bookingVisits` (các buổi của cùng caregiver trong booking, theo giờ bắt đầu); `services[]` thêm `planName`, `planDurationMinutes`. Danh sách buổi cũng có `bookingType`, `caregiverScheduleId`, `priceAmount`. Phạm vi xem hồ sơ (rule 7) không đổi.

## Bổ sung 2026-11-05 (2) — cọc theo ca, tự nhận ca, đổi địa chỉ theo buổi, sổ địa chỉ

Chủ dự án chốt (2026-11-05):

| Hạng mục | Quy tắc mới |
|---|---|
| Cọc SHIFT | Mọi loại booking đặt cọc `depositPercent` của center policy (mặc định 30 %); phần còn lại hạn `buổi đầu − remainingPaymentDueBeforeMinutes` (24h). SHIFT đặt sát hơn mốc đó → không còn hạn phần còn lại → `initialPaymentPercent = 100`. PaymentService không đổi (đã dựa vào % + hạn do Booking gửi). |
| Tự nhận SHIFT | Tạo SHIFT (ca caregiver đã đăng ký, được duyệt) → ghi `PENDING_CAREGIVER_CONFIRMATION` rồi `CAREGIVER_ACCEPT` ngay trong cùng transaction với `changedBy = NULL` (hệ thống, `acceptedBy` NULL) → `AWAITING_INITIAL_PAYMENT`, đặt hạn khoản đầu. Không gửi `CAREGIVER_REQUEST`; caregiver nhận thông báo "đã đặt vào ca của bạn". SHIFT không còn trong `GET /bookings/caregiver-requests`. |
| Đổi địa chỉ theo buổi | `PATCH /bookings/{id}/service-address` thêm `occurrenceIds` (trống = mọi buổi còn đổi được). Khách: chỉ buổi bắt đầu sau `BOOKING_ADDRESS_CHANGE_LEAD` (24h), sai → 409 `5117 ADDRESS_CHANGE_TOO_LATE`; trung tâm: mọi buổi chưa bắt đầu. Chọn đủ mọi buổi sắp tới → đổi địa chỉ booking (buổi đã qua giữ địa chỉ cũ ở cột riêng); chọn một phần → `booking_occurrences.service_address` riêng. Bỏ điều kiện "không có buổi IN_PROGRESS" (buổi đang chạy không bị đổi). |
| Địa chỉ lúc đặt | `BookingCreateRequest.serviceAddress` (tùy chọn) thay phần địa chỉ của snapshot. |
| Sổ địa chỉ | CareRecipientService `recipient_addresses` (≤ 20 / hồ sơ), `GET/POST /care-recipients/{id}/addresses`, `PUT/DELETE /{addressId}`; VIEWER đọc, EDITOR/OWNER ghi; mã lỗi 2800–2804. |

SQL cần áp: `BookingService/schema/changes/2026-11-05_occurrence_service_address.sql`,
`CareRecipientService/schema/changes/2026-11-05-recipient-addresses.sql`. Test: BookingService 136 (mới:
`shiftBookedInsideTheRemainingCutoffIsPaidInFull` — tự bỏ qua khi chạy sau 18:00 vì cần ca 1 giờ trong ngày,
`customerChangesCareAddressOfChosenVisitsOnly`), CareRecipientService 94 (mới: `savedAddressesOfARecipient`).
