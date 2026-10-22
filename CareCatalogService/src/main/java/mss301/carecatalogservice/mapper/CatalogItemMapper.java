package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.dto.request.CatalogItemRequest;
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

    @IgnoreServerManagedFields
    void updateEntityFromRequest(CatalogItemRequest request, @MappingTarget CatalogItem entity);
}
