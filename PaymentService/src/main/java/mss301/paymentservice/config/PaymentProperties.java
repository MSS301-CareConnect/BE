package mss301.paymentservice.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.RefundMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

// payment.* trong application.yaml — mọi giá trị đọc từ env; secret không vào toString.
@Data
@ConfigurationProperties(prefix = "payment")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentProperties {
    Jwt jwt = new Jwt();
    String bookingUrl;
    String identityUrl;
    ServiceClient serviceClient = new ServiceClient();
    Policy policy = new Policy();
    Vnpay vnpay = new Vnpay();
    Stripe stripe = new Stripe();
    Refund refund = new Refund();
    Invoice invoice = new Invoice();
    Outbox outbox = new Outbox();
    Expiry expiry = new Expiry();

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Jwt {
        String issuer;
        String jwksUri;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ServiceClient {
        String clientId;

        @ToString.Exclude
        String clientSecret;

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    // D17: số thật nằm ở platform_policies (seed GLOBAL 30 %); giá trị này chỉ để cảnh báo lúc khởi động khi lệch.
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Policy {
        BigDecimal depositPercent = BigDecimal.valueOf(30);
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Vnpay {
        boolean enabled;
        String tmnCode;

        @ToString.Exclude
        String hashSecret;

        String payUrl;
        String returnUrl;
        // API merchant (refund / querydr).
        String apiUrl;
        // IP máy chủ gửi kèm lệnh refund (vnp_IpAddr).
        String serverIp = "127.0.0.1";
        Duration connectTimeout = Duration.ofSeconds(5);
        Duration readTimeout = Duration.ofSeconds(15);
        // Job hết hạn hỏi lại VNPay (querydr) trước khi đánh EXPIRED — tắt thì chỉ đánh EXPIRED.
        boolean querydrEnabled;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Stripe {
        boolean enabled;

        @ToString.Exclude
        String secretKey;

        @ToString.Exclude
        String webhookSecret;

        String successUrl;
        String cancelUrl;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Refund {
        RefundMode mode = RefundMode.SIMULATED;
        int batchSize = 20;
        int maxAttempts = 5;
        Duration backoffBase = Duration.ofMinutes(1);
        Duration backoffMax = Duration.ofHours(1);
        // "Thuê" một dòng khi đang gửi: lượt dispatcher khác không gửi trùng.
        Duration claimLease = Duration.ofMinutes(2);
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Invoice {
        int batchSize = 50;
    }

    // Outbox xác nhận cọc sang Booking (Đợt 1b).
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Outbox {
        int batchSize = 20;
        int maxAttempts = 10;
        Duration backoffBase = Duration.ofSeconds(30);
        Duration backoffMax = Duration.ofMinutes(30);
        Duration claimLease = Duration.ofMinutes(2);
    }

    // Job phiên thanh toán quá hạn (PAY-07).
    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Expiry {
        // Chờ thêm sau expiresAt để IPN đến trễ kịp tới trước khi job tự đánh EXPIRED.
        Duration grace = Duration.ofMinutes(5);
        // querydr trả "chưa rõ" quá lâu → đánh EXPIRED (IPN trễ hơn nữa xử lý như trả trễ).
        Duration maxUnknown = Duration.ofHours(24);
        int batchSize = 50;
    }
}
