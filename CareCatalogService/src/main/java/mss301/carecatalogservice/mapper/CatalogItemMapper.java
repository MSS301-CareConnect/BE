package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.client.dto.MediaClientResponse;
import mss301.carecatalogservice.dto.request.CatalogItemRequest;
import mss301.carecatalogservice.dto.response.CatalogImageResponse;
import mss301.carecatalogservice.dto.response.CatalogItemResponse;
import mss301.carecatalogservice.entity.CatalogItem;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface CatalogItemMapper {

    @IgnoreServerManagedFields
    CatalogItem toEntity(CatalogItemRequest request);

    CatalogItemResponse toResponse(CatalogItem entity);

    List<CatalogItemResponse> toResponseList(List<CatalogItem> entities);

    // imageUrl do service dựng (đường dẫn cố định của Catalog), không lấy từ MediaService.
    CatalogImageResponse toImageResponse(MediaClientResponse media, String imageUrl);

    @IgnoreServerManagedFields
    void updateEntityFromRequest(CatalogItemRequest request, @MappingTarget CatalogItem entity);
}
