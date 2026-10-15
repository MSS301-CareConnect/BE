package mss301.providerservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DownloadUrlClientResponse {
    UUID mediaFileId;
    String downloadUrl;
    Instant expiresAt;
}
