package mss301.apigateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

// gateway.* trong application.yaml — URL service phía sau, JWT, CORS bắt buộc cấu hình qua env (fail khi khởi động nếu thiếu).
@Data
@Validated
@ConfigurationProperties("gateway")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GatewayProperties {
    // Base URL các service phía sau (không có service discovery).
    @NotBlank
    String identityUrl;

    @NotBlank
    String careRecipientUrl;

    // Origin của FE được gọi kèm credentials (cookie). Không dùng wildcard.
    @NotEmpty
    List<String> allowedOrigins;

    @Valid
    Jwt jwt = new Jwt();

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Jwt {
        // Phải khớp chính xác IDENTITY_JWT_ISSUER.
        @NotBlank
        String issuer;

        // JWKS của IdentityService, gọi trực tiếp.
        @NotBlank
        String jwksUri;
    }
}
