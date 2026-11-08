package mss301.aiservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMediaUpload {
    UUID mediaFileId;
    String originalFileName;
    String contentType;
    Long fileSize;
    String downloadUrl;
    Instant downloadUrlExpiresAt;
}
