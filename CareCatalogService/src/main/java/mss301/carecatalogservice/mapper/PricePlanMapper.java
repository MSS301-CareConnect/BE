package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.entity.PricePlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface PricePlanMapper {

    @IgnoreServerManagedFields
    @Mapping(target = "centerService", ignore = true)
    PricePlan toEntity(PricePlanRequest request);

    @Mapping(target = "centerServiceId", source = "centerService.id")
    PricePlanResponse toResponse(PricePlan entity);

    @IgnoreServerManagedFields
    @Mapping(target = "centerService", ignore = true)
    void updateEntityFromRequest(PricePlanRequest request, @MappingTarget PricePlan entity);
}
