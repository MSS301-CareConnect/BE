package mss301.aiservice.mapper;

import mss301.aiservice.client.dto.ChatMediaUpload;
import mss301.aiservice.client.dto.DownloadUrlClientResponse;
import mss301.aiservice.client.dto.MediaClientResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface MediaClientMapper {
    @Mapping(
            target = "mediaFileId",
            source = "media.mediaFileId"
    )
    @Mapping(
            target = "originalFileName",
            source = "media.originalFileName"
    )
    @Mapping(
            target = "contentType",
            source = "media.contentType"
    )
    @Mapping(
            target = "fileSize",
            source = "media.fileSize"
    )
    @Mapping(
            target = "downloadUrl",
            source = "download.downloadUrl"
    )
    @Mapping(
            target = "downloadUrlExpiresAt",
            source = "download.expiresAt"
    )
    ChatMediaUpload toChatMediaUpload(
            MediaClientResponse media,
            DownloadUrlClientResponse download
    );
}
