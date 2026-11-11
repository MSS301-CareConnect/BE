package mss301.carecatalogservice.config;

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

// care-catalog.* trong application.yaml — URL/issuer bắt buộc cấu hình qua env, app fail khi khởi động nếu thiếu.
@Data
@Validated
@ConfigurationProperties("care-catalog")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareCatalogProperties {
    // URL server cho OpenAPI khi đứng sau gateway (vd /api/v1). Để trống = mặc định của springdoc.
    String openApiServerUrl = "";

    @Valid
    Jwt jwt = new Jwt();

    // IdentityService qua gateway — chỉ để xin token SERVICE (POST /api/v1/auth/service-token).
    @Valid
    Remote identity = new Remote();

    // MediaService qua gateway — lưu ảnh minh họa dịch vụ chuẩn (CATALOG_ITEM_IMAGE).
    @Valid
    Remote media = new Remote();

    // Token SERVICE (client-credentials, IAM-01) của CareCatalogService; secret thô, Identity giữ BCrypt hash.
    @Valid
    ServiceClient serviceClient = new ServiceClient();

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
    public static class Remote {
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
}
