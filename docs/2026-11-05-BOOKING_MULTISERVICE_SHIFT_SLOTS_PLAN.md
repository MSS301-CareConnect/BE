# 2026-11-05 — Kế hoạch & Tiến độ: Đa dịch vụ theo khung giờ tách biệt, Chia khung giờ Ca làm việc (`SHIFT`), Trừ hao 60 phút & `maxBookings`

Tài liệu này ghi lại đầy đủ **kế hoạch chỉnh sửa**, **quyết định nghiệp vụ đã chốt**, **tiến độ hiện tại** ở phía Backend (`CareCatalogService`, `ProviderService`, `BookingService`) và **các quy tắc bắt buộc tuân thủ** theo [`BE/AGENTS.md`](../AGENTS.md).

---

## 1. Quyết định nghiệp vụ đã chốt với Owner (2026-11-05)

| Hạng mục | Quyết định đã chốt |
|---|---|
| **Hạng mục 1 — Đa dịch vụ & Chọn Caregiver** | • **Phương án 1B mở rộng**: Cho phép khách chọn nhiều dịch vụ trong cùng 1 booking (mỗi dịch vụ chọn 1 `PricePlan`), nhưng **các dịch vụ được xếp vào các khung giờ tách biệt, không đụng độ (non-overlapping)** để trong suốt buổi chăm sóc (`CareSession`), caregiver ghi nhận tiến trình và đánh dấu checklist công việc (`care_tasks` gắn `booking_detail_id`) đúng theo từng dịch vụ.<br>• **Cách A (Tách `BookingOccurrence` theo từng khung giờ dịch vụ)**: Mỗi khung giờ khách chọn tạo ra 1 `BookingOccurrence` gắn với `careServiceId` tương ứng. Khi caregiver bắt đầu ca (`CareSessionServiceImpl.buildTasks`), nếu `occurrence.careServiceId != null` thì chỉ sinh `CareTask` (`task_type = 'SERVICE'`) cho dịch vụ của buổi đó (nếu `null` như booking cũ thì giữ nguyên sinh cho mọi `BookingDetail`).<br>• **Không ẩn mất Caregiver ở Bước 2**: Khi khách chọn nhiều dịch vụ, giữ hiển thị toàn bộ caregiver của trung tâm; caregiver thiếu kỹ năng nào sẽ hiện nhãn `"Chưa đảm nhận: [Tên dịch vụ]"` và khóa chọn thay vì biến mất. |
| **Hạng mục 2 — Chia khung giờ trong Ca làm việc (`SHIFT`), `RECURRING`, `CUSTOM`** | • **Theo ca (`SHIFT`)**: Ca làm việc đã duyệt của caregiver (`CaregiverSchedule` / `WorkShift`, ví dụ `08:00–20:00`, 12 giờ) là **khung giờ rảnh tổng**. Khi khách chọn gói dịch vụ (ví dụ 4 giờ, 8 giờ hoặc theo giờ), khách được chọn **một hoặc nhiều khung giờ con không chồng lấn** nằm trong ca (ví dụ `08:00–12:00` và `12:00–16:00`), hiển thị rõ giờ bắt đầu – kết thúc của từng khung giờ.<br>• **Trong cùng 1 booking**: Cho phép chọn nhiều khung giờ liên tiếp (khoảng cách `0 phút`, ví dụ `08:00–12:00` rồi `12:00–16:00`) cho cùng 1 dịch vụ hoặc cho các dịch vụ khác nhau đã chọn.<br>• **Giữa các booking khác nhau**: Áp dụng **khoảng thời gian trừ hao di chuyển / nghỉ ngơi = 60 phút** (`[busy.startAt - 60m, busy.endAt + 60m)`), đồng thời giới hạn **số lượng booking tối đa trên mỗi ca (`maxBookings`)** được thiết lập khi Quản lý trung tâm tạo/sửa ca (`work_shifts.max_bookings`, mặc định `2`, phạm vi `1..20`).<br>• **Định kỳ (`RECURRING`) & Tự chọn buổi (`CUSTOM`)**: Đồng bộ thời lượng khung giờ với `durationMinutes` của gói giá đã chọn (gói `DAY`/`SHIFT` cố định độ dài khung giờ theo gói; gói `HOUR` kiểm tra tối thiểu `>= plan.durationMinutes`), hỗ trợ nhiều khung giờ theo từng `careServiceId` không chồng lấn và kiểm tra trừ hao 60 phút khi đã chọn caregiver. |
| **Hạng mục 3 — Chi tiết gói ở "Giá từng dịch vụ"** | • Bổ sung `planName` và `planDurationMinutes` từ `CareCatalogService` → `BookingService` (`ServiceQuote`, `QuoteLineResponse`, `BookingDetail`, `BookingItemResponse`) để hiển thị rõ tên gói và số giờ gói đã chọn trong bảng báo giá và chi tiết booking. |

---

## 2. Nguyên tắc tuân thủ cấu trúc code, thư mục và rule dự án (`BE/AGENTS.md`)

Khi tiếp tục triển khai các bước còn lại trong `BE/`, **bắt buộc tuân thủ**:

1. **Cấu trúc package chuẩn của mỗi microservice** (`mss301.<servicename>`):
   - `client/` & `client/dto/`: Feign client và DTO giao tiếp liên service.
   - `config/`: `@Configuration`, `@ConfigurationProperties` (`BookingProperties`, không hardcode hằng số cấu hình).
   - `controller/`: REST Controller mỏng, chỉ nhận request, gọi `service`, trả `APIResponse<T>`.
   - `dto/request/` & `dto/response/`: Tách biệt hoàn toàn với `entity/`; dùng `@Valid` và mã lỗi chuỗi khớp tên `ErrorCode`.
   - `entity/`: JPA Entity (`@AttributeOverride` cho khóa chính, `@Version` cho optimistic locking, không trả trực tiếp ra API).
   - `enums/`, `exception/` (`AppException`, `ErrorCode` đúng dải mã của từng service: Catalog `3000–3999`, Provider `4000–4999`, Booking `5000–5999`), `mapper/` (MapStruct với `MapStructConfig.class`, `unmappedTargetPolicy = ReportingPolicy.ERROR`), `repository/`, `security/`, `service/` + `service/Impl/`, `util/`.
2. **Quy tắc Database & Schema (Không dùng Flyway)**:
   - Mọi thay đổi schema phải cập nhật đồng thời vào `schema/<service>.sql` (dùng cho test H2/Testcontainers và khởi tạo mới) **và** tạo file migration idempotent (`ADD COLUMN IF NOT EXISTS`, `DROP CONSTRAINT IF EXISTS`, ...) trong `schema/changes/2026-11-05_*.sql` vì cấu hình runtime dùng `spring.jpa.hibernate.ddl-auto: validate`.
3. **Quy tắc Transaction & Concurrency**:
   - Không giữ `@Transactional` mở trong lúc gọi Feign sang service khác (`TransactionTemplate` cho các bước đọc/ghi ngắn, gọi mạng ở ngoài transaction).
   - Giữ đúng thứ tự khóa bi quan (`PESSIMISTIC_WRITE`) để tránh deadlock.
4. **Quy tắc Git & Kiểm thử**:
   - Không tự ý `git commit` hay `git push` khi chưa có yêu cầu rõ ràng của Owner.
   - Mọi thay đổi ở service nào phải chạy `./mvnw test` xanh toàn bộ ở service đó trước khi bàn giao.

---

## 3. Tiến độ hiện tại (Backend)

### 3.1. Đã hoàn thành & Đã pass test (`mvnw test` = PASS)

#### A. `BE/CareCatalogService` (Hoàn thành 100% — `mvnw test` xanh)
- [`ServiceQuoteResponse.java`](../CareCatalogService/src/main/java/mss301/carecatalogservice/dto/response/ServiceQuoteResponse.java): Đã thêm trường `String planName`.
- [`CenterOfferingMapper.java`](../CareCatalogService/src/main/java/mss301/carecatalogservice/mapper/CenterOfferingMapper.java): Đã map `@Mapping(target = "planName", source = "pricePlan.planName")` trong `toQuoteResponse(...)`.

#### B. `BE/ProviderService` (Hoàn thành 100% — `mvnw test` 82/82 xanh)
- [`schema/provider.sql`](../ProviderService/schema/provider.sql) & [`schema/changes/2026-11-05_work_shift_lifecycle.sql`](../ProviderService/schema/changes/2026-11-05_work_shift_lifecycle.sql): Đã thêm cột `max_bookings integer NOT NULL DEFAULT 2 CHECK (max_bookings > 0)` vào bảng `work_shifts`.
- [`WorkShift.java`](../ProviderService/src/main/java/mss301/providerservice/entity/WorkShift.java): Đã thêm `@Builder.Default @Column(name = "max_bookings", nullable = false) Integer maxBookings = 2;`.
- [`WorkShiftRequest.java`](../ProviderService/src/main/java/mss301/providerservice/dto/request/WorkShiftRequest.java) & [`WorkShiftBatchRequest.java`](../ProviderService/src/main/java/mss301/providerservice/dto/request/WorkShiftBatchRequest.java): Đã thêm `Integer maxBookings` (`@Min(1) @Max(20)`).
- [`WorkShiftResponse.java`](../ProviderService/src/main/java/mss301/providerservice/dto/response/WorkShiftResponse.java), [`ScheduleResponse.java`](../ProviderService/src/main/java/mss301/providerservice/dto/response/ScheduleResponse.java), [`BookableScheduleResponse.java`](../ProviderService/src/main/java/mss301/providerservice/dto/response/BookableScheduleResponse.java): Đã thêm `Integer maxBookings`.
- [`CaregiverScheduleMapper.java`](../ProviderService/src/main/java/mss301/providerservice/mapper/CaregiverScheduleMapper.java): Đã map `workShift.maxBookings` sang `ScheduleResponse` và `BookableScheduleResponse`.
- [`WorkShiftServiceImpl.java`](../ProviderService/src/main/java/mss301/providerservice/service/Impl/WorkShiftServiceImpl.java): Đã xử lý mặc định `maxBookings = 2` khi tạo mới nếu bỏ trống, giữ nguyên giá trị cũ khi PUT update nếu không truyền, truyền `maxBookings` khi tạo lô (`createBatch`) và kiểm tra phạm vi `1..20`.

---

#### C. `BE/BookingService` (Hoàn thành 2026-11-05 — `mvnw test` 133 test, 0 lỗi, 2 skip PostgreSQL thật)

Đã làm đủ 7 bước của bảng 3.2 bên dưới. Chi tiết thực tế / chỗ khác kế hoạch:

- **Schema**: `schema/booking.sql` + `schema/changes/2026-11-05_multiservice_shift_slots.sql` (bỏ `ck_bookings_shift_single_day`, đổi
  `uk_booking_occurrences_schedule_holding` → `idx_booking_occurrences_schedule_holding` không unique, thêm
  `booking_occurrences.care_service_id`, `booking_details.plan_name` / `plan_duration_minutes` + CHECK > 0). **Owner cần áp change
  script lên DB dev** (`ddl-auto: validate`) rồi rebuild BookingService.
- **Request**: `TimeSlotRequest` thêm `careServiceId` (mọi loại) và `caregiverScheduleId` (chỉ SHIFT; loại khác gửi → `5239`).
  SHIFT: `slots` tùy chọn — trống = một buổi trọn ca như cũ; có = mỗi khung giờ một buổi, phải nằm gọn trong ca của nó
  (`caregiverScheduleId` của slot, mặc định ca của booking) → sai `5247 SLOT_OUTSIDE_SHIFT`. Nhiều ca → BookingService tra từng ca ở Provider.
- **Giá**: hoặc mọi buổi gắn `careServiceId` hoặc không buổi nào (`5246`); dịch vụ lạ `5244`; dịch vụ không có buổi `5245`. Buổi gắn
  dịch vụ chỉ tính dịch vụ đó; gói DAY / SHIFT phải đúng `planDurationMinutes`, gói HOUR ≥ `planDurationMinutes` (`5225`, message
  đổi thành "does not match"). Buổi không gắn giữ nguyên hành vi cũ.
- **Trừ hao**: `booking.schedule.travel-buffer` (`BOOKING_TRAVEL_BUFFER`, mặc định `60m`). Buổi của booking khác cách < 60 phút →
  409 `5248 CAREGIVER_TRAVEL_BUFFER_CONFLICT` (trùng giờ thật vẫn `5215`). Trong cùng booking nối liền được. Áp cho tạo booking, nhận /
  phân công caregiver (`checkCaregiverAvailable`); **không** áp cho `POST /booking-occurrences/busy-caregivers` (picker của manager
  không biết booking đang đổi người — server kiểm lại khi lưu).
- **maxBookings**: `countHoldingBookings` đếm booking khác nhau giữ buổi trên ca, dưới khóa caregiver; `>= maxBookings` → 409 `5411`.
  Provider cũ không gửi `maxBookings` → coi là 1 (giữ hành vi cũ).
- **held-schedules**: thêm `travelBufferMinutes`, `scheduleBookingCounts`, `busySlots[].caregiverScheduleId`. **Khác kế hoạch**:
  `heldScheduleIds` vẫn là "ca có ít nhất một booking" vì BookingService không gọi Provider trong transaction đọc để biết giờ ca /
  `maxBookings` — FE tự so `scheduleBookingCounts[id]` với `maxBookings` của ca (đã có trong `BookableScheduleResponse`).
- **Ca chăm sóc**: `buildTasks` và `GET /booking-occurrences/{id}` chỉ lấy dịch vụ của buổi khi `careServiceId != null`.
- **Response**: `planName` / `planDurationMinutes` trên `lines[]` của báo giá và `services[]` của booking; `careServiceId` trên
  `occurrences[]` (booking, báo giá, caregiver); báo giá thêm `occurrences[].caregiverScheduleId`.
- **Test mới / sửa**: `OccurrenceGeneratorTests` (chia khung giờ trong ca, ngoài ca, chồng nhau), `BookingPriceCalculatorTests` (giá theo
  dịch vụ của buổi, quy tắc slot), `BookingScheduleConflictTests` (trừ hao 60 phút), `BookingApiTests.shiftSplitsIntoServiceSlotsWith
  TravelBufferAndMaxBookings` + sửa `caregiverAndRecipientConflicts…` (booking khác nối liền nay bị 5248).

- **Sửa lỗi phát hiện khi chạy thật (2026-11-05)**: `booking_status_history` thiếu `PENDING_CAREGIVER_CONFIRMATION` trong
  `ck_booking_status_history_from/_to` (sót từ CR-3, 2026-11-03) → mọi booking khách chọn sẵn caregiver bị 400 `5000`. Đã sửa
  `schema/booking.sql` + `schema/changes/2026-11-05_status_history_caregiver_confirmation.sql` (đã áp DB dev). Thêm test
  `shiftQuotesTwoHourlyServicesWithBackToBackSlots`; FE panel Tóm tắt nay hiện lý do lỗi báo giá.

### 3.2. Các bước đã làm ở `BE/BookingService` (kế hoạch gốc)

| Thứ tự | File cần chỉnh sửa / tạo mới | Nội dung chi tiết |
|---|---|---|
| **1. Schema SQL** | • [`BookingService/schema/booking.sql`](../BookingService/schema/booking.sql)<br>• `BookingService/schema/changes/2026-11-05_multiservice_shift_slots.sql` | • Bỏ `ck_bookings_shift_single_day` trên `bookings` để 1 booking `SHIFT` có thể chọn nhiều khung giờ trong cùng ca hoặc nhiều ca.<br>• Thay `UNIQUE INDEX uk_booking_occurrences_schedule_holding` bằng non-unique index `idx_booking_occurrences_schedule_holding` trên `booking_occurrences (caregiver_schedule_id)` (ràng buộc chống chồng lấn thời gian trên cùng caregiver vẫn được bảo vệ bởi `ex_booking_occurrences_caregiver_slot`).<br>• Thêm cột `care_service_id uuid` vào `booking_occurrences`.<br>• Thêm cột `plan_name varchar(150)` và `plan_duration_minutes integer` vào `booking_details`. |
| **2. DTO & Entity (Gói giá & dịch vụ theo buổi)** | • [`CareCatalogQuoteResponse.java`](../BookingService/src/main/java/mss301/bookingservice/client/dto/CareCatalogQuoteResponse.java)<br>• [`ProviderScheduleResponse.java`](../BookingService/src/main/java/mss301/bookingservice/client/dto/ProviderScheduleResponse.java)<br>• [`ServiceQuote.java`](../BookingService/src/main/java/mss301/bookingservice/dto/response/ServiceQuote.java)<br>• [`QuoteLineResponse.java`](../BookingService/src/main/java/mss301/bookingservice/dto/response/QuoteLineResponse.java)<br>• [`BookingDetail.java`](../BookingService/src/main/java/mss301/bookingservice/entity/BookingDetail.java)<br>• [`BookingItemResponse.java`](../BookingService/src/main/java/mss301/bookingservice/dto/response/BookingItemResponse.java)<br>• [`TimeSlotRequest.java`](../BookingService/src/main/java/mss301/bookingservice/dto/request/TimeSlotRequest.java)<br>• [`OccurrenceSlot.java`](../BookingService/src/main/java/mss301/bookingservice/dto/response/OccurrenceSlot.java)<br>• [`BookingOccurrence.java`](../BookingService/src/main/java/mss301/bookingservice/entity/BookingOccurrence.java)<br>• [`OccurrenceResponse.java`](../BookingService/src/main/java/mss301/bookingservice/dto/response/OccurrenceResponse.java)<br>• [`QuoteOccurrenceResponse.java`](../BookingService/src/main/java/mss301/bookingservice/dto/response/QuoteOccurrenceResponse.java)<br>• [`CaregiverOccurrenceResponse.java`](../BookingService/src/main/java/mss301/bookingservice/dto/response/CaregiverOccurrenceResponse.java)<br>• [`CaregiverHeldScheduleResponse.java`](../BookingService/src/main/java/mss301/bookingservice/dto/response/CaregiverHeldScheduleResponse.java) | • Thêm `planName` (và `planDurationMinutes` trên `QuoteLineResponse`, `BookingDetail`, `BookingItemResponse`).<br>• Thêm `Integer maxBookings` vào `ProviderScheduleResponse`.<br>• Thêm `UUID careServiceId` (và `UUID caregiverScheduleId` trên `TimeSlotRequest`) vào chuỗi `TimeSlotRequest` → `OccurrenceSlot` → `BookingOccurrence` → `OccurrenceResponse` / `QuoteOccurrenceResponse` / `CaregiverOccurrenceResponse`.<br>• Trên `CaregiverHeldScheduleResponse`, bổ sung `Integer travelBufferMinutes` (`60`) và `Map<UUID, Integer> scheduleBookingCounts` (số booking đang hoạt động trên mỗi `caregiverScheduleId`). |
| **3. Sinh buổi (`OccurrenceGenerator`)** | • [`OccurrenceGeneratorImpl.java`](../BookingService/src/main/java/mss301/bookingservice/service/Impl/OccurrenceGeneratorImpl.java) | • Với `SHIFT`: Cho phép `request.getSlots()` tùy chọn.<br>  - Nếu `slots` trống: giữ hành vi cũ (sinh 1 buổi trọn ca `[schedule.startTime, schedule.endTime]`, nếu chọn 1 dịch vụ thì gán `careServiceId` của dịch vụ đó).<br>  - Nếu `slots` có truyền: kiểm tra mọi slot không chồng lấn, mỗi slot nằm gọn trong khung giờ `[schedule.startTime, schedule.endTime]` của `ProviderScheduleResponse` tương ứng (nếu vượt ngoài khung ca → `SLOT_OUTSIDE_RECURRENCE_RANGE`), gắn `caregiverScheduleId` và `careServiceId` vào từng `OccurrenceSlot`.<br>• Với `CUSTOM` và `RECURRING`: truyền `careServiceId` từ `TimeSlotRequest` sang `OccurrenceSlot`. |
| **4. Tính giá (`BookingPriceCalculator`)** | • [`BookingPriceCalculatorImpl.java`](../BookingService/src/main/java/mss301/bookingservice/service/Impl/BookingPriceCalculatorImpl.java) | • Nếu `slot.getCareServiceId() != null`: chỉ tính giá của `ServiceQuote` khớp `slot.getCareServiceId()` cho buổi đó, và đếm `occurrenceCount` riêng cho từng dịch vụ theo số buổi được gán.<br>• Nếu `slot.getCareServiceId() == null`: giữ nguyên hành vi cũ (tính mọi dịch vụ trong `services` cho buổi đó) để tương thích ngược 100% với dữ liệu và test cũ.<br>• Kiểm tra `planDurationMinutes`: với gói cố định (`DAY`, `SHIFT`), nếu `occurrenceMinutes > planDurationMinutes` (hoặc khác `planDurationMinutes` khi đặt theo slot dịch vụ) thì trả `PRICE_PLAN_UNIT_MISMATCH`. |
| **5. Trừ hao 60 phút, `maxBookings` & Held Schedules** | • [`BookingOccurrenceRepository.java`](../BookingService/src/main/java/mss301/bookingservice/repository/BookingOccurrenceRepository.java)<br>• [`BookingServiceImpl.java`](../BookingService/src/main/java/mss301/bookingservice/service/Impl/BookingServiceImpl.java) | • Kiểm tra giữa **các booking khác nhau** của cùng caregiver: khoảng cách tối thiểu **60 phút** (`[slot.startAt - 60m, slot.endAt + 60m)` không giao với buổi active của booking khác). Các slot trong **cùng 1 booking** không cần trừ hao 60 phút với nhau.<br>• Kiểm tra `maxBookings` trên `caregiverScheduleId`: đếm số `booking.id` khác nhau đang giữ buổi active trên `caregiverScheduleId`; nếu `>= schedule.maxBookings` thì từ chối với `CAREGIVER_SCHEDULE_NOT_AVAILABLE`.<br>• Cập nhật `listHeldSchedules`: trả `travelBufferMinutes = 60`, `scheduleBookingCounts`, `busySlots`, và chỉ đưa `scheduleId` vào `heldScheduleIds` khi ca đã có buổi chiếm trọn ca hoặc đã đạt `maxBookings`. |
| **6. Checklist Ca chăm sóc (`CareSession`)** | • [`CareSessionServiceImpl.java`](../BookingService/src/main/java/mss301/bookingservice/service/Impl/CareSessionServiceImpl.java)<br>• [`BookingOccurrenceServiceImpl.java`](../BookingService/src/main/java/mss301/bookingservice/service/Impl/BookingOccurrenceServiceImpl.java) | • Trong `CareSessionServiceImpl.buildTasks(bookingId, occurrence)`: nếu `occurrence.getCareServiceId() != null`, chỉ tạo `CareTask` (`task_type = 'SERVICE'`) cho `BookingDetail` khớp `occurrence.getCareServiceId()`.<br>• Trong `BookingOccurrenceServiceImpl.get(...)`: nếu `occurrence.getCareServiceId() != null`, lọc danh sách `services` của buổi theo `occurrence.getCareServiceId()`. |
| **7. Unit & Integration Tests** | • [`OccurrenceGeneratorTests.java`](../BookingService/src/test/java/mss301/bookingservice/service/OccurrenceGeneratorTests.java)<br>• [`BookingPriceCalculatorTests.java`](../BookingService/src/test/java/mss301/bookingservice/service/BookingPriceCalculatorTests.java)<br>• [`BookingApiTests.java`](../BookingService/src/test/java/mss301/bookingservice/BookingApiTests.java) | • Cập nhật và bổ sung test cho: `SHIFT` chia nhiều khung giờ trong ca, đa dịch vụ theo từng khung giờ tách biệt, trừ hao 60 phút giữa 2 booking khác nhau, giới hạn `maxBookings` trên ca, và `planName` / `planDurationMinutes` trên báo giá. |
