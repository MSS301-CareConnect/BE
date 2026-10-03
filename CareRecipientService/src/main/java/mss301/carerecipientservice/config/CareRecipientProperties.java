package mss301.carerecipientservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// care-recipient.* trong application.yaml — URL/issuer bắt buộc cấu hình qua env, app fail khi khởi động nếu thiếu.
@Data
@Validated
@ConfigurationProperties("care-recipient")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareRecipientProperties {
    // Secret cho /internal/** của service này (Booking… gọi vào). Để trống = khóa toàn bộ API nội bộ (fail-closed).
    @ToString.Exclude
    String internalSecret = "";

    // URL server cho OpenAPI khi đứng sau gateway (vd /api/v1). Để trống = mặc định của springdoc.
    String openApiServerUrl = "";

    @Valid
    Jwt jwt = new Jwt();

    @Valid
    Identity identity = new Identity();

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
    public static class Identity {
        // Base URL IdentityService cho Feign (không có service discovery).
        @NotBlank
        String url;

        // Giá trị IDENTITY_INTERNAL_SECRET của IdentityService, gửi qua header X-Internal-Secret.
        @ToString.Exclude
        String internalSecret = "";
    }
}
