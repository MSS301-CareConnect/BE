package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.dto.request.CatalogItemRequest;
import mss301.carecatalogservice.dto.response.CatalogItemResponse;
import mss301.carecatalogservice.entity.CatalogItem;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface CatalogItemMapper {

    @IgnoreServerManagedFields
    CatalogItem toEntity(CatalogItemRequest request);

    CatalogItemResponse toResponse(CatalogItem entity);

    @IgnoreServerManagedFields
    void updateEntityFromRequest(CatalogItemRequest request, @MappingTarget CatalogItem entity);
}
