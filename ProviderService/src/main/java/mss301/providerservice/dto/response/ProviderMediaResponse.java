package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.MediaFileType;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProviderMediaResponse {
    UUID mediaFileId;
    MediaFileType mediaFileType;
    String originalFileName;
    String contentType;
    Long fileSize;
    Integer displayOrder;
    String downloadUrl;
    Instant downloadUrlExpiresAt;
}
