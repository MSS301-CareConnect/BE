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
    // Secret cho /internal/** của service này (CareRecipient, Payment… gọi vào). Để trống = khóa toàn bộ (fail-closed).
    @ToString.Exclude
    String internalSecret = "";

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

    BigDecimal depositRate = new BigDecimal("0.30");

    @Valid
    Schedule schedule = new Schedule();

    @Valid
    Integration integration = new Integration();

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

        // Giá trị <SERVICE>_INTERNAL_SECRET của service được gọi, gửi qua header X-Internal-Secret.
        @ToString.Exclude
        String internalSecret = "";
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

        // Hạn trung tâm duyệt booking PENDING (không quá giờ buổi đầu).
        @NotNull
        Duration acceptanceTimeout = Duration.ofHours(24);

        // Hạn khách đặt cọc sau khi trung tâm chấp nhận (không quá giờ buổi đầu).
        @NotNull
        Duration depositTimeout = Duration.ofHours(24);

        // Khoảng ngày tối đa một lần xem lịch của caregiver.
        @Min(1)
        @Max(366)
        int maxCaregiverRangeDays = 62;

        // Số booking tối đa xử lý mỗi lượt quét hết hạn.
        @Min(1)
        @Max(1000)
        int expiryBatchSize = 100;

        // Chu kỳ quét hết hạn — BookingExpiryScheduler đọc thẳng placeholder; khai báo ở đây để kiểm khi khởi động.
        @NotNull
        Duration expiryScanInterval = Duration.ofMinutes(5);
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Integration {
        // Bật tích hợp CareCatalogService (báo giá thật từ catalog; false = trả báo giá tạm không giá).
        boolean catalogEnabled = false;
    }
}
