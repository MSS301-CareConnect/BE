package mss301.mediaservice.mapper;

import mss301.mediaservice.dto.response.MediaResponse;
import mss301.mediaservice.entity.MediaFile;
import org.mapstruct.Mapper;
import mss301.mediaservice.dto.response.DownloadUrlResponse;
import mss301.mediaservice.enums.FileType;
import mss301.mediaservice.enums.MediaPurpose;
import mss301.mediaservice.enums.MediaStatus;
import java.time.Instant;
import java.util.UUID;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface MediaMapper {

    @Mapping(target = "mediaFileId", source = "id")
    MediaResponse toResponse(MediaFile mediaFile);
    @IgnoreServerManagedFields
    MediaFile toMediaFile(String bucketName, String objectKey, String originalFileName, String contentType,
                         FileType fileType, Long fileSize, MediaPurpose mediaPurpose, MediaStatus mediaStatus,
                         UUID uploadedBy, String sourceService, String referenceType, UUID referenceId, String etag);

    DownloadUrlResponse toDownloadUrlResponse(UUID mediaFileId, String downloadUrl, Instant expiresAt);
}
