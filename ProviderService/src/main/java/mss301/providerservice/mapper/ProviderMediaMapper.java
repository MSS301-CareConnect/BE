package mss301.providerservice.mapper;

import mss301.providerservice.client.dto.DownloadUrlClientResponse;
import mss301.providerservice.client.dto.MediaClientResponse;
import mss301.providerservice.dto.response.ProviderMediaResponse;
import mss301.providerservice.entity.ProviderMedia;
import mss301.providerservice.enums.MediaOwnerType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface ProviderMediaMapper {

    // Metadata file + link tải lấy từ MediaService; loại file và thứ tự hiển thị lấy từ bản ghi Provider.
    @Mapping(target = "mediaFileId", source = "providerMedia.mediaFileId")
    @Mapping(target = "downloadUrlExpiresAt", source = "download.expiresAt")
    ProviderMediaResponse toResponse(ProviderMedia providerMedia, MediaClientResponse metadata,
                                     DownloadUrlClientResponse download);

    // ProviderMedia không kế thừa BaseEntity: id + createdAt do JPA / @PrePersist sinh.
    @Mapping(target = "providerMediaId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "mediaFileId", source = "uploaded.mediaFileId")
    @Mapping(target = "mediaFileType", source = "uploaded.fileType")
    ProviderMedia toProviderMedia(MediaOwnerType ownerType, UUID ownerId, MediaClientResponse uploaded,
                                  Integer displayOrder, UUID createdBy);
}
