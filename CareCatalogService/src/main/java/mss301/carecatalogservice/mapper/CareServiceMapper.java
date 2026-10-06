package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.dto.request.CareServiceRequest;
import mss301.carecatalogservice.dto.response.CareServiceResponse;
import mss301.carecatalogservice.entity.CareService;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface CareServiceMapper {

    @IgnoreServerManagedFields
    CareService toEntity(CareServiceRequest request);

    CareServiceResponse toResponse(CareService entity);

    @IgnoreServerManagedFields
    void updateEntityFromRequest(CareServiceRequest request, @MappingTarget CareService entity);
}
