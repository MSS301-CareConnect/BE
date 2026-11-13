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
import java.util.UUID;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

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
    PolicyDefaults policyDefaults = new PolicyDefaults();

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
        List<UUID> adminUserIds = new ArrayList<>();
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

        // Kiểm tra đã cấu hình đủ clientId và clientSecret.
        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    // Ca chăm sóc (OPS-02 / OPS-04).
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Session {
        // Cửa sổ check-in / mốc MISSED lấy từ center policy chụp trên booking (Q12), không cấu hình ở đây.

        // Session còn IN_PROGRESS quá giờ kết thúc buổi bao lâu thì nhắc (một lần).
        @NotNull
        Duration overdueReminderAfter = Duration.ofMinutes(60);

        @NotNull
        Duration overdueScanInterval = Duration.ofMinutes(5);

        // 2026-11-11: check-in xa địa chỉ chăm sóc hơn bán kính này (mét) thì gắn cờ cho trung tâm, không chặn.
        @Min(50)
        int checkInRadiusMeters = 300;

        // 2026-11-11: sau khi buổi kết thúc, khách có khoảng này để phản ánh; trong lúc đó buổi chưa được quyết toán.
        @NotNull
        Duration customerReportWindow = Duration.ofHours(24);

        // D21 (H-10): ca quên check-out quá giờ kết thúc buổi + autoCloseAfter → hệ thống tự đóng, tính COMPLETED.
        @NotNull
        Duration autoCloseAfter = Duration.ofHours(4);

        // OPS-06: chu kỳ quét buổi chưa check-in sau giờ bắt đầu + check_in_late_minutes của booking → MISSED.
        @NotNull
        Duration missedScanInterval = Duration.ofMinutes(5);

        // 2026-11-10: chu kỳ quét buổi đã tới giờ mà chưa check-in để cảnh báo sớm (OccurrenceMissedScheduler đọc thẳng
        // placeholder; khai báo ở đây để kiểm khi khởi động).
        @NotNull
        Duration lateWarningScanInterval = Duration.ofMinutes(1);

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

    // Center booking policy mặc định (spec §8) — áp cho trung tâm chưa cấu hình riêng (center_booking_policies). Tên field
    // trùng CenterBookingPolicy để mapper chép thẳng; ràng buộc giữa các giá trị kiểm ở CenterBookingPolicyServiceImpl.
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PolicyDefaults {
        @Min(0)
        Integer shiftMinAdvanceMinutes = 120;

        @Min(0)
        Integer recurringMinAdvanceMinutes = 2880;

        @Min(0)
        Integer customMinAdvanceMinutes = 2880;

        @Min(1)
        Integer confirmationTimeoutMinutes = 1440;

        @Min(1)
        Integer assignmentTimeoutMinutes = 1440;

        @Min(1)
        Integer initialPaymentTimeoutMinutes = 1440;

        @Min(0)
        Integer remainingPaymentDueBeforeMinutes = 1440;

        @NotNull
        List<Integer> remainingPaymentReminderMinutes = new ArrayList<>(List.of(4320, 2880));

        @NotNull
        BigDecimal depositPercent = BigDecimal.valueOf(30);

        @Min(0)
        Integer checkInEarlyMinutes = 30;

        @Min(0)
        Integer checkInLateMinutes = 30;

        @Min(0)
        Integer refundThresholdMinutes = 1440;

        @NotNull
        BigDecimal refundPercentBeforeThreshold = BigDecimal.valueOf(100);

        @NotNull
        BigDecimal refundPercentAfterThreshold = BigDecimal.valueOf(70);

        // 2026-11-10 (quyết định 2026-11-05): hủy còn dưới 2 giờ thì không hoàn.
        @Min(0)
        Integer lateCancelNoRefundMinutes = 120;

        @NotNull
        BigDecimal overduePaymentForfeitPercent = BigDecimal.valueOf(100);
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

        // L-08: không đặt lịch bắt đầu quá xa (giá / lịch làm việc chưa có), mỗi buổi tối thiểu một khoảng.
        @Min(1)
        @Max(366)
        int maxAdvanceDays = 90;

        @NotNull
        Duration minOccurrenceDuration = Duration.ofHours(1);

        // Caregiver còn được xem hồ sơ người bệnh bao lâu sau khi buổi COMPLETED kết thúc (D5, SEC-09).
        @NotNull
        Duration caregiverAccessGrace = Duration.ofHours(24);

        // Khách đổi địa chỉ chăm sóc cho buổi bắt đầu sau ít nhất khoảng này (2026-11-05).
        @NotNull
        Duration addressChangeLead = Duration.ofHours(24);

        // Số booking tối đa xử lý mỗi lượt quét hết hạn.
        @Min(1)
        @Max(1000)
        int expiryBatchSize = 100;

        // Chu kỳ quét hết hạn — BookingExpiryScheduler đọc thẳng placeholder; khai báo ở đây để kiểm khi khởi động.
        @NotNull
        Duration expiryScanInterval = Duration.ofMinutes(1);

        // Lượt quét đầu sau khi khởi động: dọn ngay booking quá hạn trong lúc service tắt.
        @NotNull
        Duration expiryInitialDelay = Duration.ofSeconds(30);

        // Nhắc trung tâm trước hạn xác nhận / phân công (phút trước hạn, mỗi mốc một lần).
        @NotNull
        List<@NotNull @Min(1) Integer> centerReminderMinutes = new ArrayList<>(List.of(720, 120));

        // Cửa sổ "sắp quá hạn" của thẻ hàng đợi trung tâm.
        @NotNull
        Duration dueSoonWindow = Duration.ofHours(2);

        // Trừ hao di chuyển / nghỉ giữa hai booking khác nhau của cùng caregiver (2026-11-05, Owner chốt 60 phút).
        @NotNull
        Duration travelBuffer = Duration.ofMinutes(60);

        // Giữ chỗ chờ cọc (2026-11-09): booking chờ thanh toán ngay lúc đặt (SHIFT tự nhận) chỉ giữ slot trong khoảng này.
        @NotNull
        Duration paymentHold = Duration.ofMinutes(5);

        // Khoảng ẩn sau hạn cọc cho IPN / return VNPay tới muộn: vẫn nhận deposit-paid, scheduler chưa nhả slot.
        @NotNull
        Duration paymentHoldGrace = Duration.ofMinutes(2);

        // Chu kỳ quét riêng nhả slot hết hạn cọc (BookingExpiryScheduler đọc thẳng placeholder).
        @NotNull
        Duration holdScanInterval = Duration.ofSeconds(15);

        // Caregiver chỉ xin hủy buổi bắt đầu sau ít nhất khoảng này (2026-11-09).
        @NotNull
        Duration caregiverWithdrawalLead = Duration.ofHours(24);

        // Tự phân công (2026-11-09): chu kỳ quét buổi đã cọc chưa có người, thời gian chờ giữa hai lần thử cùng booking,
        // số ứng viên tối đa tra hồ sơ để thử, các mốc nhắc trung tâm khi chưa tìm được người.
        @NotNull
        Duration autoAssignInterval = Duration.ofMinutes(1);

        @NotNull
        Duration autoAssignRetry = Duration.ofMinutes(15);

        @Min(1)
        @Max(20)
        int autoAssignCandidates = 5;

        // 2026-11-13: báo trung tâm ngay lần đầu không tìm được người, rồi nhắc lại khi buổi thiếu người sớm nhất còn đúng
        // các khoảng này tới giờ bắt đầu (đều phải lớn hơn unassignedCancelLead).
        @NotNull
        List<Duration> unassignedRemindBefore = new ArrayList<>(List.of(Duration.ofHours(48), Duration.ofHours(24),
                Duration.ofHours(16)));

        // Một lịch bị chia cho nhiều hơn số người này → báo trung tâm xem lại (2026-11-13).
        @Min(1)
        int splitCaregiverWarning = 3;

        // Buổi đã cọc còn khoảng này tới giờ bắt đầu mà vẫn chưa có người → hệ thống hủy buổi, hoàn 100 % (Q-B2).
        @NotNull
        Duration unassignedCancelLead = Duration.ofHours(12);
    }
}
