package mss301.paymentservice.config;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.UUID;

@Data
@ConfigurationProperties(prefix = "payment")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentProperties {
    Jwt jwt = new Jwt();
    String bookingUrl;
    String identityUrl;
    ServiceClient serviceClient = new ServiceClient();
    long checkoutExpiryMinutes = 30;
    Policy policy = new Policy();
    Vnpay vnpay = new Vnpay();
    Stripe stripe = new Stripe();

    @Data
    public static class Jwt {
        private String issuer;
        private String jwksUri;
    }

    @Data
    public static class ServiceClient {
        private String clientId;
        private String clientSecret;

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    @Data
    public static class Policy {
        private UUID id;
        private int depositPercent = 100;
    }

    @Data
    public static class Vnpay {
        private boolean enabled;
        private String tmnCode;
        private String hashSecret;
        private String payUrl;
        private String returnUrl;
    }

    @Data
    public static class Stripe {
        private boolean enabled;
        private String secretKey;
        private String webhookSecret;
        private String successUrl;
        private String cancelUrl;
    }
}
