package mss301.bookingservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

// booking.* trong application.yaml — URL/issuer bắt buộc cấu hình qua env, app fail khi khởi động nếu thiếu.
@Data
@Validated
@ConfigurationProperties("booking")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingProperties {
    // URL server cho OpenAPI khi đứng sau gateway (vd /api/v1). Để trống = mặc định của springdoc.
    String openApiServerUrl = "";

    @Valid
    Jwt jwt = new Jwt();

    @Valid
    RemoteService identity = new RemoteService();

    @Valid
    RemoteService careRecipient = new RemoteService();

    @Valid
    RemoteService provider = new RemoteService();

    @Valid
    RemoteService careCatalog = new RemoteService();

    // PaymentService qua gateway — chính sách hiệu lực để hiển thị cọc dự kiến (C2.1).
    @Valid
    RemoteService payment = new RemoteService();

    // Danh tính máy của BookingService để xin token SERVICE (IAM-01). Bỏ trống được khi chưa gọi endpoint SERVICE nào —
    // lời gọi cần token sẽ trả 503 SERVICE_TOKEN_UNAVAILABLE.
    @Valid
    ServiceClient serviceClient = new ServiceClient();

    // BK-02 (SEC-12): số booking PENDING / AWAITING_DEPOSIT tối đa một khách được giữ cùng lúc.
    @Min(1)
    @Max(100)
    int maxPendingPerCustomer = 3;

    @Valid
    Schedule schedule = new Schedule();

    @Valid
    Session session = new Session();

    @Valid
    Outbox outbox = new Outbox();

    @Valid
    Cancellation cancellation = new Cancellation();

    @Valid
    Complaint complaint = new Complaint();

    @Valid
    Notification notification = new Notification();

    // D20 (H-09): hạn mở khiếu nại và hạn khách tự chuyển admin.
    @Data
    public static class Complaint {
        // Chỉ mở khiếu nại trong khoảng này sau giờ kết thúc buổi (hoặc sau khi booking bị hủy).
        @NotNull
        Duration openWindow = Duration.ofDays(7);

        // Trung tâm chưa giải quyết sau khoảng này kể từ lúc mở → khách được chuyển admin.
        @NotNull
        Duration customerEscalateAfter = Duration.ofHours(48);
    }

    // H-08 / H-09: Identity chưa có API tra người dùng theo vai trò → cấu hình danh sách admin nhận thông báo vận hành.
    @Data
    public static class Notification {
        java.util.List<java.util.UUID> adminUserIds = new java.util.ArrayList<>();
    }

    // BK-03: previewToken hủy booking. Khóa trống = khóa ngẫu nhiên theo tiến trình (chỉ dev, một instance).
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Cancellation {
        @NotNull
        String previewSecret = "";

        @NotNull
        Duration previewTtl = Duration.ofMinutes(10);
    }


    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Jwt {
        // Phải khớp chính xác claim iss do IdentityService phát (IDENTITY_JWT_ISSUER).
        @NotBlank
        String issuer;

        // JWKS của IdentityService, gọi trực tiếp (không qua gateway).
        @NotBlank
        String jwksUri;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class RemoteService {
        // Base URL service được gọi qua Feign (không có service discovery).
        @NotBlank
        String url;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ServiceClient {
        String clientId = "";

        @ToString.Exclude
        String clientSecret = "";

        @NotNull
        Duration connectTimeout = Duration.ofSeconds(2);

        @NotNull
        Duration readTimeout = Duration.ofSeconds(3);

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    // Ca chăm sóc (OPS-02 / OPS-04).
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Session {
        // Caregiver được check-in sớm nhất bao lâu trước giờ bắt đầu buổi.
        @NotNull
        Duration startWindow = Duration.ofMinutes(30);

        // Session còn IN_PROGRESS quá giờ kết thúc buổi bao lâu thì nhắc (một lần).
        @NotNull
        Duration overdueReminderAfter = Duration.ofMinutes(60);

        @NotNull
        Duration overdueScanInterval = Duration.ofMinutes(5);

        // D21 (H-10): ca quên check-out quá giờ kết thúc buổi + autoCloseAfter → hệ thống tự đóng, tính COMPLETED.
        @NotNull
        Duration autoCloseAfter = Duration.ofHours(4);

        // OPS-06: buổi chưa check-in sau giờ bắt đầu + missedGrace (và không có báo vắng) → MISSED, hoàn tiền khách.
        @NotNull
        Duration missedGrace = Duration.ofMinutes(30);

        @NotNull
        Duration missedScanInterval = Duration.ofMinutes(5);

        // D21: báo vắng chưa được quyết định sau giờ kết thúc + noShowDecisionReminderAfter → hệ thống tự chấp nhận.
        @NotNull
        Duration noShowDecisionReminderAfter = Duration.ofHours(24);
    }

    // booking_outbox: retry có giới hạn, backoff tăng dần (rule #25).
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Outbox {
        @NotNull
        Duration scanInterval = Duration.ofSeconds(30);

        @Min(1)
        @Max(1000)
        int batchSize = 50;

        @Min(1)
        @Max(100)
        int maxAttempts = 10;

        @NotNull
        Duration baseBackoff = Duration.ofSeconds(30);

        @NotNull
        Duration maxBackoff = Duration.ofHours(1);

        // Thời gian "giữ" một dòng đang gửi — lượt khác bỏ qua dòng này trong khoảng đó.
        @NotNull
        Duration claimLease = Duration.ofMinutes(2);
    }

    // Quy tắc lịch đã chốt (docs/2026-10-09-PLAN_BOOKING.md §9) — đổi qua env, không hardcode trong code.
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Schedule {
        @NotNull
        ZoneId timezone = ZoneId.of("Asia/Ho_Chi_Minh");

        @Min(1)
        @Max(366)
        int maxOccurrences = 60;

        @Min(1)
        @Max(366)
        int maxSpanDays = 90;

        // Buổi đầu phải bắt đầu sau thời điểm đặt ít nhất khoảng này.
        @NotNull
        Duration minLeadTime = Duration.ofHours(12);

        // L-08: không đặt lịch bắt đầu quá xa (giá / lịch làm việc chưa có), mỗi buổi tối thiểu một khoảng.
        @Min(1)
        @Max(366)
        int maxAdvanceDays = 90;

        @NotNull
        Duration minOccurrenceDuration = Duration.ofHours(1);

        // Hạn trung tâm duyệt booking PENDING (không quá giờ buổi đầu).
        @NotNull
        Duration acceptanceTimeout = Duration.ofHours(24);

        // Hạn khách đặt cọc sau khi trung tâm chấp nhận (không quá giờ buổi đầu).
        @NotNull
        Duration depositTimeout = Duration.ofHours(24);

        // Caregiver còn được xem hồ sơ người bệnh bao lâu sau khi buổi COMPLETED kết thúc (D5, SEC-09).
        @NotNull
        Duration caregiverAccessGrace = Duration.ofHours(24);

        // Số booking tối đa xử lý mỗi lượt quét hết hạn.
        @Min(1)
        @Max(1000)
        int expiryBatchSize = 100;

        // Chu kỳ quét hết hạn — BookingExpiryScheduler đọc thẳng placeholder; khai báo ở đây để kiểm khi khởi động.
        @NotNull
        Duration expiryScanInterval = Duration.ofMinutes(5);
    }
}
