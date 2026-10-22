package mss301.identityservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

// identity.* trong application.yaml — TTL/key/issuer bắt buộc cấu hình qua env, app fail khi khởi động nếu thiếu.
@Data
@Validated
@ConfigurationProperties("identity")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IdentityProperties {
    @NotBlank
    String issuer;

    @NotBlank
    String audience;

    @NotBlank
    String keyId;

    @NotBlank
    String privateKey;

    @NotBlank
    String publicKey;

    @NotNull
    Duration accessTtl;

    @NotNull
    Duration refreshTtl;

    // TTL token SERVICE (client-credentials) — ngắn vì không thu hồi được tại service khác.
    @NotNull
    Duration serviceTokenTtl = Duration.ofMinutes(10);

    // Danh sách client seed: "booking:<bcrypt>,payment:<bcrypt>" (env IDENTITY_SERVICE_CLIENTS). Không chứa secret thô.
    @ToString.Exclude
    String serviceClients = "";

    @NotNull
    Duration otpTtl;

    @NotNull
    Duration otpCooldown;

    @Min(1)
    int otpMaxAttempts;

    @NotNull
    List<String> allowedOrigins;

    boolean cookieSecure = true;

    @Pattern(regexp = "Lax|Strict|None")
    String cookieSameSite = "Lax";

    // URL server cho OpenAPI khi đứng sau gateway (vd /api/v1). Để trống = mặc định của springdoc.
    String openApiServerUrl = "";

    @Valid
    Brevo brevo = new Brevo();

    @AssertTrue(message = "Identity TTLs must be positive, refresh TTL must exceed access TTL")
    public boolean isValidTtls() {
        return accessTtl != null && accessTtl.toSeconds() > 0
                && refreshTtl != null && refreshTtl.compareTo(accessTtl) > 0
                && otpTtl != null && otpTtl.toSeconds() > 0
                && otpCooldown != null && otpCooldown.toSeconds() > 0
                && serviceTokenTtl != null && serviceTokenTtl.toSeconds() > 0;
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Brevo {
        String apiKey;

        String senderEmail;

        String senderName = "CareConnect";

        String baseUrl = "https://api.brevo.com/v3";
    }
}
