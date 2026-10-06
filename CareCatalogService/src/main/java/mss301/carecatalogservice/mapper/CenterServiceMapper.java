package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.dto.request.CenterServiceRequest;
import mss301.carecatalogservice.dto.response.CenterServiceResponse;
import mss301.carecatalogservice.entity.CenterService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class, uses = {CareServiceMapper.class})
public interface CenterServiceMapper {

    @IgnoreServerManagedFields
    @Mapping(target = "careService", ignore = true)
    CenterService toEntity(CenterServiceRequest request);

    CenterServiceResponse toResponse(CenterService entity);

    @IgnoreServerManagedFields
    @Mapping(target = "careService", ignore = true)
    @Mapping(target = "centerId", ignore = true) // Center ID shouldn't change
    void updateEntityFromRequest(CenterServiceRequest request, @MappingTarget CenterService entity);
}
