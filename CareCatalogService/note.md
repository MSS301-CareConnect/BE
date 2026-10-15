# Ghi chú: Tích hợp BookingService ↔ CareCatalogService

> Tài liệu này ghi lại **toàn bộ những gì đã được thêm / sửa** khi thực hiện kết nối luồng nghiệp vụ
> giữa **BookingService** và **CareCatalogService** theo kiến trúc microservice của CareConnect.
>
> Ngày thực hiện: 2026-10-12 ~ 2026-10-13

---

## 1. Mục tiêu tích hợp

Khi khách hàng tạo booking, `BookingService` cần lấy giá dịch vụ thực tế từ `CareCatalogService`
thay vì hardcode hay bỏ qua giá. Luồng cụ thể:

1. **BookingService** gọi nội bộ `/internal/center-services/quote` của **CareCatalogService**.
2. **CareCatalogService** tra cứu `CareService → CenterService → PricePlan`, trả về `basePrice`,
   `pricingUnit`, `durationMinutes` cho từng dịch vụ trong booking.
3. **BookingService** dùng kết quả báo giá để tính `totalAmount`, `depositAmount`, lưu vào bảng
   `bookings` và `booking_details`.

---

## 2. Phía CareCatalogService — những gì đã thêm

### 2.1 InternalSecurityConfig (đã có sẵn)

**File:** `src/main/java/mss301/carecatalogservice/security/InternalSecurityConfig.java`

Chain bảo mật riêng (Order = 1) cho path `/internal/**`:
- Không JWT, không cookie, không CSRF/CORS.
- Dùng `InternalSecretFilter`: kiểm header `X-Internal-Secret` so với `care-catalog.internal-secret`.
- Nếu sai → 401; nếu đúng → gán authority `INTERNAL_SERVICE`.

### 2.2 InternalCatalogController — endpoint báo giá

**File:** `src/main/java/mss301/carecatalogservice/controller/InternalCatalogController.java`

```
POST /internal/center-services/quote
Body: InternalServiceQuoteRequest
Response: APIResponse<List<InternalServiceQuoteResponse>>
```

### 2.3 DTO request/response nội bộ

**File:** `dto/request/InternalServiceQuoteRequest.java`

| Field | Kiểu | Ghi chú |
|---|---|---|
| centerId | UUID (NotNull) | Trung tâm cung cấp dịch vụ |
| items[].careServiceId | UUID (NotNull) | Loại dịch vụ gốc |
| items[].centerServiceId | UUID (nullable) | null = tự tìm CenterService active |
| items[].pricePlanId | UUID (nullable) | null = lấy PricePlan active đầu tiên |

**File:** `dto/response/InternalServiceQuoteResponse.java`

| Field | Kiểu | Ghi chú |
|---|---|---|
| careServiceId | UUID | |
| centerServiceId | UUID | |
| pricePlanId | UUID | |
| serviceName | String | Từ CareService.name |
| pricingUnit | PricingUnit | HOURLY / DAILY / SESSION |
| durationMinutes | Integer | Thời lượng (phút) |
| basePrice | BigDecimal | Đơn giá từ PricePlan |
| active | boolean | Luôn true (đã kiểm tra) |

### 2.4 CatalogQuoteService + CatalogQuoteServiceImpl

**File:** `service/CatalogQuoteService.java` (interface)
**File:** `service/Impl/CatalogQuoteServiceImpl.java`

Logic `quote()`:
1. Tra CareService (careServiceId) → CARE_SERVICE_NOT_FOUND nếu thiếu/xóa mềm.
2. Kiểm CareService.status == ACTIVE → SERVICE_NOT_ACTIVE.
3. Tra CenterService (centerServiceId hoặc auto) → CENTER_SERVICE_NOT_FOUND.
4. Kiểm CenterService.status == ACTIVE.
5. Tra PricePlan (pricePlanId hoặc auto-active đầu tiên) → PRICE_PLAN_NOT_FOUND.
6. Trả InternalServiceQuoteResponse đầy đủ.

Chạy trong `@Transactional(readOnly = true)` — không ghi DB.

---

## 3. Phía BookingService — những gì đã thêm

### 3.1 CareCatalogClient (Feign)

**File:** `client/CareCatalogClient.java`

```java
@FeignClient(name = "care-catalog", url = "${booking.care-catalog.url}",
        configuration = CareCatalogClientConfig.class,
        fallbackFactory = CareCatalogClientFallbackFactory.class)
public interface CareCatalogClient {
    @PostMapping("/internal/center-services/quote")
    RemoteApiResponse<List<CareCatalogQuoteResponse>> quote(@RequestBody CareCatalogQuoteRequest request);
}
```

### 3.2 CareCatalogClientConfig

**File:** `client/CareCatalogClientConfig.java`

- `RequestInterceptor`: gắn `X-Internal-Secret` từ `booking.care-catalog.internal-secret`.
- `ErrorDecoder`: `CareCatalogErrorDecoder`.

### 3.3 CareCatalogClientFallbackFactory

**File:** `client/CareCatalogClientFallbackFactory.java`

- `AppException` → ném lại nguyên.
- Lỗi mạng/timeout → `AppException(CARE_CATALOG_UNAVAILABLE)`.

### 3.4 CareCatalogErrorDecoder

**File:** `client/CareCatalogErrorDecoder.java`

| HTTP Status | ErrorCode |
|---|---|
| 404 | PRICE_PLAN_NOT_FOUND |
| 400, 409 | CENTER_SERVICE_NOT_SUPPORTED |
| 401, 403 | CARE_CATALOG_UNAVAILABLE + log.error |
| Khác | CARE_CATALOG_UNAVAILABLE |

### 3.5 DTOs phía BookingService (bản sao độc lập)

**File:** `client/dto/CareCatalogQuoteRequest.java` — tương đương InternalServiceQuoteRequest
**File:** `client/dto/CareCatalogQuoteResponse.java` — tương đương InternalServiceQuoteResponse, có `@JsonIgnoreProperties(ignoreUnknown = true)`

### 3.6 CareServiceCatalogPort + CareServiceCatalogPortImpl

**File:** `service/CareServiceCatalogPort.java` (interface / Port)

```java
BookingQuote quote(UUID centerId, List<BookingServiceRequest> services, List<OccurrenceSlot> slots);
```

**File:** `service/Impl/CareServiceCatalogPortImpl.java` (Adapter)

- Enabled bởi flag `booking.care-catalog.enabled`.
- Gọi `careCatalogClient.quote()`, map response → `BookingQuote` (tính totalAmount, depositAmount).
- Khi `enabled = false`: dùng stub giá cứng (dev/test).

### 3.7 Sử dụng trong BookingServiceImpl.create()

```java
// Gọi ngoài transaction (rule #24 AGENTS.md)
BookingQuote quote = careServiceCatalogPort.quote(request.getCenterId(), request.getServices(), slots);
// Ghi trong TransactionTemplate
transactionTemplate.execute(status ->
    persistBooking(actorId, request, slots, recipientSnapshot, caregiver, quote, ...));
// Trong persistBooking:
bookingDetailRepository.saveAll(bookingDetailMapper.toBookingDetailList(quote.getItems(), savedBooking));
```

---

## 4. Cấu hình env cần thiết

### BookingService `.env`

```env
BOOKING_CARE_CATALOG_URL=http://localhost:8083
BOOKING_CARE_CATALOG_INTERNAL_SECRET=<secret_của_catalog>
BOOKING_CARE_CATALOG_ENABLED=true
```

### CareCatalogService `.env`

```env
CATALOG_INTERNAL_SECRET=<giá_trị_khớp_bên_Booking>
```

### BookingService `application.yaml`

```yaml
booking:
  care-catalog:
    url: ${BOOKING_CARE_CATALOG_URL:http://localhost:8083}
    internal-secret: ${BOOKING_CARE_CATALOG_INTERNAL_SECRET:}
    enabled: ${BOOKING_CARE_CATALOG_ENABLED:false}
```

### CareCatalogService `application.yaml`

```yaml
care-catalog:
  internal-secret: ${CATALOG_INTERNAL_SECRET:}
```

---

## 5. Luồng gọi đầy đủ

```
Customer → POST /bookings → ApiGateway → BookingService
    BookingService.create()
        ├── careRecipientDirectoryService.getBookableSnapshot()  → CareRecipientService
        ├── centerPort.checkBookable()                           → ProviderService
        ├── caregiverPort.resolveAssignable()                    → ProviderService
        └── careServiceCatalogPort.quote()                       → CareCatalogService
                POST /internal/center-services/quote
                    ├── lookup CareService
                    ├── lookup CenterService
                    └── lookup PricePlan → basePrice, pricingUnit, durationMinutes
        ← BookingQuote(totalAmount, depositAmount, items[])
        TransactionTemplate:
            ├── scheduleConflictService.lock() + check
            ├── bookingRepository.save()            ← totalAmount, depositAmount
            ├── bookingOccurrenceRepository.saveAll()
            ├── bookingDetailRepository.saveAll()   ← snapshot giá từ quote.items
            └── bookingLifecycleService.recordCreated()
← 201 BookingDetailResponse
```

---

## 6. Bảo mật nội bộ

| Điểm | Cơ chế |
|---|---|
| CareCatalogService `/internal/**` | `InternalSecretFilter` kiểm `X-Internal-Secret` |
| BookingService → CareCatalogService | `CareCatalogClientConfig.RequestInterceptor` tự gắn header |
| ApiGateway | Chặn tuyệt đối mọi path `/internal/**` từ client ngoài |
| Secret riêng | `CATALOG_INTERNAL_SECRET` ≠ `IDENTITY_INTERNAL_SECRET` |

---

## 7. Nguyên tắc kiến trúc đã áp dụng

| Nguyên tắc | Cách thực hiện |
|---|---|
| Không FK vật lý xuyên DB | `booking_details.center_service_id` lưu UUID — không FK sang Catalog DB |
| Snapshot giá | `unitPrice`, `totalPrice`, `serviceName`, `pricingUnit` lưu khi đặt booking |
| Gọi mạng ngoài transaction | `careServiceCatalogPort.quote()` trước `transactionTemplate.execute()` |
| Circuit breaker / fallback | `CareCatalogClientFallbackFactory` (Feign FallbackFactory) |
| Port / Adapter pattern | `BookingServiceImpl` phụ thuộc interface `CareServiceCatalogPort`, không Feign trực tiếp |
| DTO bản sao | `client/dto/` trong BookingService — không share class với CareCatalogService |

---

## 8. Danh sách tất cả file liên quan

### CareCatalogService

| File | Vai trò |
|---|---|
| `controller/InternalCatalogController.java` | Endpoint POST `/internal/center-services/quote` |
| `dto/request/InternalServiceQuoteRequest.java` | Request body nội bộ |
| `dto/response/InternalServiceQuoteResponse.java` | Response nội bộ |
| `service/CatalogQuoteService.java` | Interface |
| `service/Impl/CatalogQuoteServiceImpl.java` | Logic báo giá (lookup 3 bảng) |
| `security/InternalSecurityConfig.java` | Chain bảo mật `/internal/**` |
| `config/CareCatalogProperties.java` | `care-catalog.*` (có `internalSecret`) |

### BookingService

| File | Vai trò |
|---|---|
| `client/CareCatalogClient.java` | Feign client gọi Catalog |
| `client/CareCatalogClientConfig.java` | Config secret + ErrorDecoder |
| `client/CareCatalogClientFallbackFactory.java` | Fallback khi Catalog down |
| `client/CareCatalogErrorDecoder.java` | Map HTTP status → ErrorCode |
| `client/dto/CareCatalogQuoteRequest.java` | DTO request gửi sang Catalog |
| `client/dto/CareCatalogQuoteResponse.java` | DTO nhận từ Catalog |
| `service/CareServiceCatalogPort.java` | Port interface |
| `service/Impl/CareServiceCatalogPortImpl.java` | Adapter (gọi Feign + map quote) |
| `service/Impl/BookingServiceImpl.java` | Dùng port tại `create()` |
