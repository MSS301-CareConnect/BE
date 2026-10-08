package mss301.providerservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("provider")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProviderProperties {
    @ToString.Exclude
    String internalSecret = "";
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
