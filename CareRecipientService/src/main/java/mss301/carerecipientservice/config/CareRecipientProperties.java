package mss301.carerecipientservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình nghiệp vụ của CareRecipientService (prefix: care-recipient). Mọi giá trị đọc từ application.yaml (trỏ sang
 * biến môi trường).
 */
@Data
@Validated
@ConfigurationProperties("care-recipient")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareRecipientProperties {

    // URL server cho OpenAPI khi đứng sau gateway (vd /api/v1). Để trống = mặc định của springdoc.
    String openApiServerUrl = "";

    @Valid
    Jwt jwt = new Jwt();

    @Valid
    Identity identity = new Identity();

    @Valid
    Booking booking = new Booking();

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
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Booking {
        // Base URL BookingService cho Feign (caregiver-access — quyền xem hồ sơ của caregiver).
        @NotBlank
        String url;
    }
}
