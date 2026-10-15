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

    @Valid
    Media media = new Media();

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

        @ToString.Exclude
        String internalSecret = "";
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Media {
        @NotBlank
        String url;

        @ToString.Exclude
        String internalSecret = "";
    }
}
