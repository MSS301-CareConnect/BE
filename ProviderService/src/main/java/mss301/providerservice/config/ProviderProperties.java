package mss301.providerservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Validated
@ConfigurationProperties("provider")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProviderProperties {
    String openApiServerUrl = "";

    @Valid
    Jwt jwt = new Jwt();

    @Valid
    Identity identity = new Identity();

    @Valid
    Media media = new Media();

    // CareCatalogService qua ApiGateway — kiểm offering khi gán kỹ năng caregiver (PRV-01).
    @Valid
    CareCatalog careCatalog = new CareCatalog();

    // BookingService qua ApiGateway — hỏi caregiver nào đã có buổi được giao (C5.1, PRV-02 / SRCH-01).
    @Valid
    Booking booking = new Booking();

    // Danh tính máy của ProviderService để xin token SERVICE (IAM-01). Bỏ trống được khi chưa gọi endpoint cần token
    // SERVICE — lời gọi cần token sẽ trả 503 SERVICE_TOKEN_UNAVAILABLE.
    @Valid
    ServiceClient serviceClient = new ServiceClient();

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Jwt {
        @NotBlank
        String issuer;

        @NotBlank
        String jwksUri;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Identity {
        @NotBlank
        String url;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Media {
        @NotBlank
        String url;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class CareCatalog {
        @NotBlank
        String url;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Booking {
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
}
