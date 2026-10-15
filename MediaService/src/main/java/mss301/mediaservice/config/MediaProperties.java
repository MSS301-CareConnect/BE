package mss301.mediaservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "media")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaProperties {

    String internalSecret;

    @Valid
    R2 r2 = new R2();

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class R2 {

        @NotBlank
        String endpoint;

        @NotBlank
        String bucket;

        @NotBlank
        String accessKeyId;

        @NotBlank
        String secretAccessKey;

        @Min(1)
        long downloadUrlTtlSeconds = 300;
    }
}
