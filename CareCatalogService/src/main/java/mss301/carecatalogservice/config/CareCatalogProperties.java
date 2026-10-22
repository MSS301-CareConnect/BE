package mss301.carecatalogservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

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
}
