package mss301.mediaservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.mediaservice.enums.FileType;
import mss301.mediaservice.enums.MediaPurpose;
import mss301.mediaservice.enums.MediaStatus;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaResponse {
    UUID mediaFileId;
    String originalFileName;
    String contentType;
    FileType fileType;
    Long fileSize;
    MediaPurpose mediaPurpose;
    MediaStatus mediaStatus;
    UUID uploadedBy;
    String sourceService;
    String referenceType;
    UUID referenceId;
    Instant createdAt;
}
