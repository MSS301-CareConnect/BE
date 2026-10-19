# Original User Request

## 2026-10-18T14:25:51Z

# Teamwork Project Prompt — Final

> Status: Launched
> Goal: Execute microservice architecture refactoring across CareConnect backend
> Requested team: Full multi-agent team (Lead Architect, Service Refactorers, Gateway Specialist, QA/Build Verifier)

Toàn diện tái cấu trúc kiến trúc và cấu trúc thư mục các Microservices trong CareConnect BE: Hợp nhất Controller/Service theo Domain Aggregate (không phân tách theo role), chuẩn hóa Interface-Impl, bổ sung API Versioning (/api/v1), chuyển các endpoint Auth về đúng vị trí, xóa bỏ hoàn toàn /internal và chuyển sang gọi Service-to-Service và FE qua ApiGateway bằng OpenFeign, giữ @Getter @Setter của Lombok trên Entity và chuẩn hóa MapStruct Mapper (tương thích JPA Buddy workflow), sau khi hoàn thành cập nhật các quy tắc vào AGENTS.md.

Working directory: d:\FA26\MSS\CareConnect\BE
Integrity mode: development

## Requirements

### R1. Hợp nhất Controller & Service theo Domain Aggregate (Không phân tách theo Role)
- Không phân tách Controller hay Service theo vai trò người dùng (không tạo các class riêng biệt như `CustomerBookingController`, `AdminBookingController`, `CaregiverCareRecipientController`, `AdminCareCenterController`...).
- Mỗi Domain Aggregate phải tuân thủ nghiêm ngặt mô hình:
  - Entity: `[Domain]` (giữ `@Getter @Setter` của Lombok)
  - Repository: `[Domain]Repository` (Bắt buộc là `interface` trong `repository/`)
  - Service: `[Domain]Service` (Bắt buộc là `interface` trong `service/`)
  - ServiceImpl: `[Domain]ServiceImpl` (Bắt buộc là `class` trong `service/Impl/`)
  - Controller: `[Domain]Controller` (Gom toàn bộ các endpoint của Aggregate vào một nơi trong `controller/`, phân quyền đầy đủ bằng `@PreAuthorize` tại từng method).
- Áp dụng cụ thể:
  - **`CareRecipientService`**: Hợp nhất `AdminCareRecipientController`, `CaregiverCareRecipientController`, `CareRecipientController` thành duy nhất 1 `CareRecipientController` (`/api/v1/care-recipients/**`). Gom các service phân mảnh (`CaregiverRecipientService`, `CaregiverAssignmentService`) vào `CareRecipientService` / `CareRecipientServiceImpl`.
  - **`ProviderService`**: Hợp nhất 11 controllers phân mảnh thành các Controller chuẩn theo Aggregate:
    - `CareCenterController` (`/api/v1/care-centers/**`) (hợp nhất từ `AdminCareCenterController`, `CenterScheduleController`)
    - `CaregiverController` (`/api/v1/caregivers/**`) (hợp nhất từ `CaregiverManagementController`, `CaregiverWorkController`, `AdminVerificationController`)
    - `WorkShiftController` (`/api/v1/work-shifts/**`)
    - `CertificateController` (`/api/v1/certificates/**`)
  - **`BookingService`**: Hợp nhất và chuẩn hóa:
    - `BookingController` (`/api/v1/bookings/**`) (hợp nhất toàn bộ luồng tạo, xem, duyệt, hủy, phân công của Customer, Center Manager, Admin)
    - `BookingOccurrenceController` (`/api/v1/booking-occurrences/**`) (quản lý trạng thái từng ca/buổi: check-in, complete, cancel occurrence)
  - **`IdentityService`**:
    - Chuyển `AccountController` / `AccountService` thành `UserController` (`/api/v1/users/**`) và `UserService` / `UserServiceImpl` để đồng nhất với Entity `User` và `UserRepository`.

### R2. Chuẩn hóa tầng Interface & Implementation thuần túy
- Toàn bộ Repository trong `repository/` và Service trong `service/` BẮT BUỘC là `interface`.
- Nghiệp vụ chỉ được hiện thực trong các class `*ServiceImpl` đặt trong thư mục `service/Impl/`.
- Sửa dứt điểm 6 Service đang là `public class` trực tiếp ở `ProviderService`:
  - `CaregiverService` -> Interface `CaregiverService` + Class `CaregiverServiceImpl`
  - `CaregiverScheduleService` -> Interface `CaregiverScheduleService` + Class `CaregiverScheduleServiceImpl`
  - `CertificateService` -> Interface `CertificateService` + Class `CertificateServiceImpl`
  - `IdentityAccountService` -> Interface `IdentityAccountService` + Class `IdentityAccountServiceImpl`
  - `ShiftRegistrationService` -> Interface `ShiftRegistrationService` + Class `ShiftRegistrationServiceImpl`
  - `WorkShiftService` -> Interface `WorkShiftService` + Class `WorkShiftServiceImpl`

### R3. Tái cấu trúc IdentityService (Auth vs User) & API Versioning đồng bộ
- Chuyển toàn bộ các endpoint liên quan đến xác thực / đăng ký / OTP / mật khẩu từ `AccountController` sang `AuthenticationController` với tiền tố version `/api/v1/auth/**`:
  - `/api/v1/auth/register`
  - `/api/v1/auth/register/resend-otp`
  - `/api/v1/auth/register/verify`
  - `/api/v1/auth/forgot-password`
  - `/api/v1/auth/reset-password`
  - `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`, `/api/v1/auth/csrf`
  - `/api/v1/auth/mobile/**`
- `UserController` (`/api/v1/users/**`): Chỉ giữ lại các chức năng quản lý tài khoản của chính mình (`/me`, cập nhật profile, đổi mật khẩu) và quản trị người dùng của Admin / Center Manager.
- Bổ sung phiên bản API `/api/v1/` vào tất cả các Controller trên toàn bộ 6 services.

### R4. Xóa bỏ hoàn toàn `/internal/**` & Định tuyến toàn bộ qua ApiGateway
- Xóa bỏ toàn bộ:
  - Các Controller nội bộ: `InternalUserController`, `InternalCareRecipientController`, `InternalCatalogController`, `InternalProviderController`, `InternalBookingController`, `InternalMediaController`.
  - Các Service nội bộ tương ứng.
  - Các bộ lọc và cấu hình: `InternalSecretFilter`, `InternalSecurityConfig`, header `X-Internal-Secret`.
  - Bộ lọc `InternalPathBlockFilter` tại ApiGateway.
- Chuyển toàn bộ các OpenFeign Client giữa các Service sang gọi qua ApiGateway (`http://localhost:8080/api/v1/...`).
- Sử dụng Feign RequestInterceptor để chuyển tiếp header `Authorization: Bearer <token>` (forward JWT token của caller context).
- Cập nhật ApiGateway (`GatewayPaths` & `RouteConfig`): Bổ sung đầy đủ route cho cả 6 services (`IdentityService`, `CareRecipientService`, `BookingService`, `ProviderService`, `CareCatalogService`, `MediaService`).
- Đảm bảo Frontend chỉ kết nối duy nhất qua ApiGateway.

### R5. Quy chuẩn Entity & MapStruct Mapper
- Entity: Giữ vững quy chuẩn `@Entity @Table @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @FieldDefaults(level = AccessLevel.PRIVATE)` của Lombok. Tuyệt đối không dùng `@Data` trên Entity.
- Mapper: Sử dụng MapStruct (`@Mapper(componentModel = "spring")`) tương thích hoàn toàn với workflow sinh mã tự động từ Entity sang DTO của JPA Buddy plugin.

### R6. Cập nhật tài liệu AGENTS.md
- Bổ sung các rule mới về:
  - Quy ước đặt tên và gom nhóm theo Domain Aggregate (Entity -> Repo Interface -> Service Interface + Impl -> Controller Unified).
  - Quy chuẩn phân chia rõ ràng Interface trong `service/` và Class trong `service/Impl/`.
  - Quy tắc bảo mật Service-to-Service qua ApiGateway với Feign & JWT (không dùng `/internal` và `X-Internal-Secret`).
  - Hướng dẫn Developer workflow dùng JPA Buddy kết hợp MapStruct mapper và lý do giữ `@Getter @Setter`, cấm `@Data`.
  - Chuẩn versioning bắt buộc `/api/v1` trên mọi Controller.

## Acceptance Criteria

### Tính toàn vẹn cấu trúc & Codebase
- [ ] Không còn Controller hay Service nào bị tách lẻ theo role (`AdminCareRecipientController`, `CaregiverCareRecipientController`, `AdminCareCenterController`...).
- [ ] 100% Repository và Service ở tầng root là `interface`; 100% logic nằm trong `*ServiceImpl` ở `Impl/`.
- [ ] 100% các file `*Internal*.java` (`InternalController`, `InternalSecurityConfig`, `InternalSecretFilter`) đã bị xóa bỏ hoàn toàn.
- [ ] 100% API endpoints của toàn bộ services đều có tiền tố `/api/v1/`.
- [ ] `ApiGateway` định tuyến đầy đủ 6 services; các OpenFeign client trỏ về ApiGateway URL và forward Authorization header.
- [ ] Các chức năng đăng ký, xác thực, quên mật khẩu nằm trọn vẹn trong `AuthenticationController` (`/api/v1/auth/**`).
- [ ] Toàn bộ 6 service build thành công bằng Maven Wrapper (`mvn clean compile`) không có lỗi biên dịch.
- [ ] File `AGENTS.md` được cập nhật đầy đủ các quy tắc mới.
