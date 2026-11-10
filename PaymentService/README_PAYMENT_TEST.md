# PaymentService — chạy và test Booking → VNPay / Stripe

Tài liệu này bám theo code hiện tại của CareConnect:

- API Gateway: `http://localhost:8080`
- IdentityService: `8081`
- BookingService: `8085`
- PaymentService: `8087`
- PostgreSQL Payment: `localhost:5447`, database `CARECONNECT_PAYMENT`
- Luồng đúng: `PENDING` → manager chấp nhận → `AWAITING_DEPOSIT` → thanh toán thành công → `CONFIRMED`

> Không gọi tay API `/deposit-paid`. Sau khi VNPay IPN hoặc Stripe webhook hợp lệ, PaymentService tự gọi BookingService bằng token `SERVICE`.

## 1. Thư viện cần có

Trong `PaymentService/pom.xml` phải có:

```xml
<!-- Swagger UI + OpenAPI annotations: @Tag, @Operation, @Parameter... -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.1.0</version>
</dependency>

<!-- Stripe Java SDK -->
<dependency>
    <groupId>com.stripe</groupId>
    <artifactId>stripe-java</artifactId>
    <version>34.0.0</version>
</dependency>
```

Sau khi thêm dependency:

```powershell
cd PaymentService
.\mvnw.cmd clean compile
```

VNPay không cần thư viện Maven riêng. Code tạo URL và kiểm tra HMAC SHA-512 nằm trong `VnPayGateway` và `VnPaySigner`.

Để test Stripe webhook trên máy local, cài thêm **Stripe CLI**: <https://docs.stripe.com/stripe-cli>.

Để VNPay gọi IPN vào máy local, cần URL public. Có thể dùng **Cloudflare Tunnel** (`cloudflared`):
<https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/>.

## 2. Chuẩn bị `.env`

Không sửa `.env.example` để chứa secret thật. Tạo file `.env`:

```powershell
cd PaymentService
Copy-Item .env.example .env
```

Các biến chung tối thiểu:

```dotenv
PAYMENT_SERVER_PORT=8087
PAYMENT_DB_PORT=5447
PAYMENT_DB_NAME=CARECONNECT_PAYMENT
PAYMENT_DB_URL=jdbc:postgresql://localhost:5447/CARECONNECT_PAYMENT
PAYMENT_DB_USERNAME=postgres
PAYMENT_DB_PASSWORD=postgres

PAYMENT_JWT_ISSUER=careconnect-identity
PAYMENT_JWT_JWKS_URI=http://localhost:8081/.well-known/jwks.json
PAYMENT_IDENTITY_URL=http://localhost:8080
PAYMENT_BOOKING_URL=http://localhost:8080

PAYMENT_SERVICE_CLIENT_ID=payment
PAYMENT_SERVICE_CLIENT_SECRET=<raw-secret-của-service-client-payment>

PAYMENT_CHECKOUT_EXPIRY_MINUTES=30
PAYMENT_POLICY_ID=00000000-0000-0000-0000-000000000001
PAYMENT_DEPOSIT_PERCENT=100
```

`PAYMENT_SERVICE_CLIENT_SECRET` là raw secret được tạo bên IdentityService cho client `payment`. IdentityService phải chứa bản BCrypt tương ứng trong `IDENTITY_SERVICE_CLIENTS`. Hai giá trị không được tự đặt độc lập.

## 3. Tạo và kiểm tra database Payment

### 3.1 Chạy PostgreSQL riêng của PaymentService

```powershell
cd PaymentService
docker compose up -d
docker compose ps
```

Chỉ chạy schema nếu database chưa có bảng Payment:

```powershell
docker compose exec -T postgres psql -U postgres -d CARECONNECT_PAYMENT -c "\dt"
```

Nếu chưa thấy `payments` và `payment_attempts`:

```powershell
Get-Content -Raw schema/payment.sql |
    docker compose exec -T postgres psql `
    -U postgres `
    -d CARECONNECT_PAYMENT `
    -v ON_ERROR_STOP=1
```

Không chạy lại `payment.sql` nếu bảng đã tồn tại.

### 3.2 Chạy PaymentService local

```powershell
.\scripts\Start-Dev.ps1
```

Kiểm tra:

```powershell
Invoke-RestMethod http://localhost:8087/actuator/health
```

Kết quả cần có: `status = UP`.

Nếu chạy toàn bộ bằng stack Docker ở thư mục `BE`, dùng `start.bat` và không chạy thêm `Start-Dev.ps1`, tránh trùng port `8087`.

## 4. Swagger và header frontend cần gửi

Mở Swagger tổng hợp:

```text
http://localhost:8080/swagger-ui.html
```

Chọn definition `payment`. Bấm **Authorize** và dán access token, không thêm chữ `Bearer` trong ô nếu Swagger tự thêm.

Khi frontend/Postman gọi API có bảo vệ:

```http
Authorization: Bearer <access-token>
Content-Type: application/json
```

Khi dùng Bearer token thuần thì không cần CSRF. Bốn callback sau là public và frontend không tự tạo chữ ký:

- `GET /api/v1/payments/vnpay/ipn`
- `GET /api/v1/payments/vnpay/return`
- `POST /api/v1/payments/stripe/webhook`
- `GET /api/v1/payments/stripe/return`

## 5. Chuẩn bị một booking có thể thanh toán

### 5.1 Dữ liệu phải có sẵn

Để tạo booking thành công, hệ thống cần:

1. Care center đang `ACTIVE` và đã gán `CENTER_MANAGER`.
2. Dịch vụ catalog, center service và price plan đang hoạt động.
3. Caregiver đã `VERIFIED`, được gán dịch vụ.
4. Caregiver có đăng ký ca được manager duyệt, phủ giờ booking.
5. Customer có care recipient.
6. Ngày booking phải cách hiện tại ít nhất 12 giờ. Khi test, chọn ngày hiện tại cộng 2 ngày.

Ghi lại các ID:

| Biến Postman | Nội dung |
|---|---|
| `customerToken` | access token role CUSTOMER |
| `managerToken` | access token role CENTER_MANAGER |
| `centerId` | ID trung tâm |
| `caregiverId` | `caregiver_id` bên ProviderService, không phải user ID |
| `recipientId` | ID hồ sơ người được chăm sóc |
| `careServiceId` | ID dịch vụ chuẩn |
| `centerServiceId` | ID dịch vụ của trung tâm |
| `pricePlanId` | ID gói giá |
| `bookingDate` | ngày hiện tại + 2 ngày, dạng `yyyy-MM-dd` |

Lấy ngày test nhanh bằng PowerShell:

```powershell
(Get-Date).AddDays(2).ToString('yyyy-MM-dd')
```

### 5.2 Đăng nhập lấy token

```http
POST http://localhost:8080/api/v1/auth/mobile/login
Content-Type: application/json

{
  "email": "customer@example.com",
  "password": "Abc@12345"
}
```

Lưu `result.accessToken` thành `customerToken`. Làm tương tự tài khoản manager và lưu thành `managerToken`.

### 5.3 Báo giá trước khi tạo booking

```http
POST http://localhost:8080/api/v1/bookings/quotes
Authorization: Bearer {{customerToken}}
Content-Type: application/json

{
  "recipientId": "{{recipientId}}",
  "centerId": "{{centerId}}",
  "caregiverId": "{{caregiverId}}",
  "scheduleType": "ONE_TIME",
  "startDate": "{{bookingDate}}",
  "endDate": "{{bookingDate}}",
  "startTime": "08:00",
  "endTime": "10:00",
  "customerNote": "Test thanh toán booking",
  "services": [
    {
      "careServiceId": "{{careServiceId}}",
      "centerServiceId": "{{centerServiceId}}",
      "pricePlanId": "{{pricePlanId}}"
    }
  ]
}
```

Kiểm tra `result.totalAmount` lớn hơn 0.

### 5.4 Tạo booking

```http
POST http://localhost:8080/api/v1/bookings
Authorization: Bearer {{customerToken}}
Content-Type: application/json
Idempotency-Key: payment-test-001
```

Body dùng lại body ở bước báo giá. Lưu `result.id` thành `bookingId`. Kết quả cần là:

```text
status = PENDING
```

Nếu DTO response dùng tên `bookingId` thay vì `id`, lưu đúng UUID booking trả về.

### 5.5 Manager chấp nhận booking

```http
POST http://localhost:8080/api/v1/bookings/{{bookingId}}/accept
Authorization: Bearer {{managerToken}}
Content-Type: application/json

{
  "caregiverId": "{{caregiverId}}"
}
```

Nếu caregiver đã chọn khi tạo booking, body `{}` cũng được. Kết quả bắt buộc:

```text
status = AWAITING_DEPOSIT
depositDueAt != null
```

Chỉ khi đó mới tạo checkout được.

## 6. Test VNPay Sandbox

### 6.1 Đưa Gateway ra internet để nhận IPN

Mở terminal riêng:

```powershell
cloudflared tunnel --url http://localhost:8080
```

Cloudflare trả về URL dạng:

```text
https://random-name.trycloudflare.com
```

Trong trang đăng ký/cấu hình VNPay Sandbox, đặt:

```text
Return URL: https://random-name.trycloudflare.com/api/v1/payments/vnpay/return
IPN URL:    https://random-name.trycloudflare.com/api/v1/payments/vnpay/ipn
```

Trong `PaymentService/.env`:

```dotenv
VNPAY_ENABLED=true
VNPAY_TMN_CODE=<tmn-code-sandbox>
VNPAY_HASH_SECRET=<hash-secret-sandbox>
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=https://random-name.trycloudflare.com/api/v1/payments/vnpay/return
```

Khởi động lại PaymentService sau khi sửa `.env`. Quick Tunnel đổi URL khi chạy lại, nên phải cập nhật cả VNPay Sandbox và `.env` nếu URL đổi.

### 6.2 Tạo VNPay checkout

```http
POST http://localhost:8080/api/v1/payments/bookings/{{bookingId}}/checkout
Authorization: Bearer {{customerToken}}
Content-Type: application/json

{
  "provider": "VNPAY"
}
```

Kết quả cần có:

```json
{
  "code": 200,
  "result": {
    "provider": "VNPAY",
    "status": "PENDING",
    "checkoutUrl": "https://sandbox.vnpayment.vn/..."
  }
}
```

Copy `result.checkoutUrl` và mở bằng trình duyệt.

### 6.3 Nhập thẻ VNPay test thành công

Chọn ngân hàng **NCB** và nhập:

| Trường | Giá trị test |
|---|---|
| Số thẻ | `9704198526191432198` |
| Tên chủ thẻ | `NGUYEN VAN A` |
| Ngày phát hành | `07/15` |
| OTP | `123456` |

Đây là dữ liệu Sandbox, không dùng thẻ thật. Danh sách chính thức: <https://sandbox.vnpayment.vn/apis/vnpay-demo/>.

Sau khi thanh toán, trình duyệt được chuyển về Return URL. Việc cập nhật database phải đến từ IPN hợp lệ.

### 6.4 Kiểm tra kết quả VNPay

```http
GET http://localhost:8080/api/v1/payments/by-booking/{{bookingId}}
Authorization: Bearer {{customerToken}}
```

Kết quả:

```text
result.status = SUCCESS
result.attempts[...].provider = VNPAY
```

Sau đó:

```http
GET http://localhost:8080/api/v1/bookings/{{bookingId}}
Authorization: Bearer {{customerToken}}
```

Kết quả:

```text
result.status = CONFIRMED
depositPaidAt != null
```

## 7. Test Stripe Checkout

> Dùng một booking mới. Booking đã thanh toán VNPay không thể thanh toán lại bằng Stripe.

Lặp mục 5.4 và 5.5 với `Idempotency-Key` mới, lưu UUID thành `stripeBookingId`.

### 7.1 Cấu hình Stripe test mode

Trong Stripe Dashboard, bật **Test mode** và lấy secret key bắt đầu bằng `sk_test_`.

Trong `PaymentService/.env`:

```dotenv
STRIPE_ENABLED=true
STRIPE_SECRET_KEY=sk_test_xxx
STRIPE_WEBHOOK_SECRET=whsec_tạm_thời_sẽ_thay_ở_bước_sau
STRIPE_SUCCESS_URL=http://localhost:8080/api/v1/payments/stripe/return?session_id={CHECKOUT_SESSION_ID}
STRIPE_CANCEL_URL=http://localhost:3000/payment/cancel
```

Nếu chưa có frontend ở port 3000, Cancel URL chỉ dùng khi bấm hủy và có thể chưa hiển thị được; nó không ảnh hưởng luồng thanh toán thành công.

### 7.2 Chạy Stripe CLI để chuyển webhook về local

Đăng nhập một lần:

```powershell
stripe login
```

Chạy listener:

```powershell
stripe listen `
  --events checkout.session.completed,checkout.session.expired `
  --forward-to http://localhost:8080/api/v1/payments/stripe/webhook
```

CLI in ra signing secret dạng `whsec_...`. Copy đúng giá trị đó vào:

```dotenv
STRIPE_WEBHOOK_SECRET=whsec_xxx
```

Khởi động lại PaymentService, nhưng giữ terminal `stripe listen` tiếp tục chạy. Secret của Stripe CLI khác secret của webhook endpoint tạo trên Dashboard.

### 7.3 Tạo Stripe checkout

```http
POST http://localhost:8080/api/v1/payments/bookings/{{stripeBookingId}}/checkout
Authorization: Bearer {{customerToken}}
Content-Type: application/json

{
  "provider": "STRIPE"
}
```

Copy `result.checkoutUrl` và mở bằng trình duyệt.

### 7.4 Nhập thẻ Stripe test thành công

| Trường | Giá trị test |
|---|---|
| Email | email bất kỳ, ví dụ `student@example.com` |
| Card number | `4242 4242 4242 4242` |
| Expiry | một tháng/năm trong tương lai, ví dụ `12/30` |
| CVC | 3 chữ số bất kỳ, ví dụ `123` |
| Name | tên bất kỳ |
| ZIP/postal code | mã hợp lệ bất kỳ nếu form yêu cầu |

Chỉ dùng dữ liệu test của Stripe trong test mode, không dùng thẻ thật. Xem danh sách thẻ test chính thức:
<https://docs.stripe.com/testing>.

Sau khi bấm thanh toán:

1. Stripe chuyển trình duyệt về `/stripe/return?session_id=...`.
2. Stripe CLI nhận `checkout.session.completed` và chuyển webhook vào PaymentService.
3. PaymentService kiểm tra chữ ký, amount, currency và trạng thái `paid`.
4. PaymentService cập nhật payment `SUCCESS` và gọi BookingService `/deposit-paid`.

### 7.5 Kiểm tra kết quả Stripe

```http
GET http://localhost:8080/api/v1/payments/by-booking/{{stripeBookingId}}
Authorization: Bearer {{customerToken}}
```

Kết quả cần có:

```text
result.status = SUCCESS
result.attempts[...].provider = STRIPE
```

```http
GET http://localhost:8080/api/v1/bookings/{{stripeBookingId}}
Authorization: Bearer {{customerToken}}
```

Kết quả cần có:

```text
result.status = CONFIRMED
depositPaidAt != null
```

## 8. Test thống kê doanh thu cho ADMIN

Đăng nhập bằng tài khoản ADMIN để lấy `adminToken`, sau đó:

```http
GET http://localhost:8080/api/v1/payments/revenue?from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z
Authorization: Bearer {{adminToken}}
```

Có thể thêm:

```text
&centerId={{centerId}}
```

Response gồm:

- `successfulPaymentCount`
- `totalRevenue`
- `averagePayment`

Chỉ payment có status `SUCCESS` và nằm trong khoảng thời gian mới được tính.

## 9. Lỗi thường gặp

| Hiện tượng | Nguyên nhân và cách xử lý |
|---|---|
| `401` | Token hết hạn/sai issuer; mobile login lại, kiểm tra `PAYMENT_JWT_ISSUER` và JWKS URL. |
| `403` khi checkout | Token không phải CUSTOMER hoặc customer không sở hữu booking. |
| `BOOKING_NOT_PAYABLE` | Booking chưa ở `AWAITING_DEPOSIT`, đã quá `depositDueAt`, hoặc đã thanh toán. |
| `PAYMENT_PROVIDER_DISABLED` | Provider chưa enable hoặc thiếu key/secret trong `.env`; restart service sau khi sửa. |
| VNPay trả `RspCode=97` | Sai `VNPAY_HASH_SECRET` hoặc query bị thay đổi. |
| VNPay thanh toán xong nhưng payment vẫn `PENDING` | IPN URL không public, tunnel đã đổi URL, VNPay chưa được cấu hình đúng IPN, hoặc Gateway/Payment đang tắt. |
| Stripe `Invalid signature` | `STRIPE_WEBHOOK_SECRET` không đúng secret mà phiên `stripe listen` hiện tại in ra; restart Payment sau khi sửa. |
| Stripe Return hiện `PENDING` vài giây | Return có thể đến trước webhook; chờ webhook rồi gọi lại `/by-booking/{bookingId}`. |
| Payment `SUCCESS` nhưng Booking chưa `CONFIRMED` | Sai service client `payment`, Identity/Gateway/Booking không chạy; xem Payment log lỗi gọi `/deposit-paid`. |
| `Unable to determine Dialect` | Spring không đọc `PAYMENT_DB_URL` hoặc PostgreSQL chưa chạy. Dùng `Start-Dev.ps1`, không bấm Run trực tiếp nếu IntelliJ chưa cấu hình env. |
| `invalid value for parameter TimeZone: Asia/Saigon` | Dùng `UTC` hoặc `Asia/Ho_Chi_Minh`, không dùng alias `Asia/Saigon`. |

## 10. Checklist hoàn thành

- [ ] Swagger `payment` hiển thị ba tag: Payments, Payment callbacks, Payment policies.
- [ ] Swagger có nút Authorize cho `bearerAuth`.
- [ ] Booking trước checkout ở `AWAITING_DEPOSIT`.
- [ ] VNPay tạo được `checkoutUrl`, thẻ NCB test thanh toán thành công.
- [ ] VNPay IPN cập nhật Payment `SUCCESS` và Booking `CONFIRMED`.
- [ ] Stripe CLI đang listen và PaymentService dùng đúng `whsec_...`.
- [ ] Stripe Checkout nhận thẻ `4242`, webhook được xử lý thành công.
- [ ] Stripe Payment `SUCCESS` và Booking `CONFIRMED`.
- [ ] ADMIN xem được thống kê doanh thu.

