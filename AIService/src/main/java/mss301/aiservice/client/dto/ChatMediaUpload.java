package mss301.aiservice.client.dto;

import lombok.Data;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMediaUpload {
    UUID mediaFileId;

    String originalFileName;

    String contentType;

    Long fileSize;

    String downloadUrl;

    Instant downloadUrlExpiresAt;
}
