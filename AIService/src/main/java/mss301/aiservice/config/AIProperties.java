package mss301.aiservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.security.Identity;
import java.time.Duration;
import java.time.ZoneId;

@Data
@Validated
@ConfigurationProperties(prefix = "ai")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AIProperties {
    String openApiServerUrl = "";

    @Valid
    Jwt jwt = new Jwt();

    @Valid
    Identity identity = new Identity();

    @Valid
    Groq groq = new Groq();

    @Valid
    Chat chat = new Chat();

    @Valid
    Media media = new Media();

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
    public static class Groq {

        @NotBlank
        String baseUrl;

        @NotBlank
        @ToString.Exclude
        String apiKey;

        @NotBlank
        String model;

        @DecimalMin("0.0")
        @DecimalMax("2.0")
        double temperature = 0.3;

        @Min(1)
        @Max(16384)
        int maxCompletionTokens = 1024;

        @NotNull
        Duration connectTimeout =
                Duration.ofSeconds(5);

        @NotNull
        Duration readTimeout =
                Duration.ofSeconds(60);
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Chat {

        @Min(1)
        int guestDailyPromptLimit = 5;

        @Min(1)
        @Max(2000)
        int guestMaxPromptLength = 500;

        @Min(1)
        @Max(10000)
        int customerMaxPromptLength = 2000;

        @Min(1)
        @Max(3)
        int maxImages = 3;

        @Min(1)
        int maxImageSizeMb = 10;

        @Min(1)
        int maxTotalImageSizeMb = 20;

        @Min(2)
        int maxHistoryMessages = 20;

        @NotNull
        ZoneId guestTimezone =
                ZoneId.of("Asia/Ho_Chi_Minh");
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Media {

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
        Duration connectTimeout =
                Duration.ofSeconds(2);

        @NotNull
        Duration readTimeout =
                Duration.ofSeconds(3);

        public boolean isConfigured() {
            return clientId != null
                    && !clientId.isBlank()
                    && clientSecret != null
                    && !clientSecret.isBlank();
        }
    }
}
