package mss301.mediaservice.mapper;

import mss301.mediaservice.dto.response.MediaResponse;
import mss301.mediaservice.entity.MediaFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface MediaMapper {

    @Mapping(target = "mediaFileId", source = "id")
    MediaResponse toResponse(MediaFile mediaFile);
}
