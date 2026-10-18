package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;
import mss301.carecatalogservice.entity.CenterOffering;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class, uses = {CatalogItemMapper.class})
public interface CenterOfferingMapper {

    @IgnoreServerManagedFields
    @Mapping(target = "catalogItem", ignore = true)
    CenterOffering toEntity(CenterOfferingRequest request);

    CenterOfferingResponse toResponse(CenterOffering entity);

    @IgnoreServerManagedFields
    @Mapping(target = "catalogItem", ignore = true)
    @Mapping(target = "centerId", ignore = true)
    void updateEntityFromRequest(CenterOfferingRequest request, @MappingTarget CenterOffering entity);
}
