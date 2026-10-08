package mss301.providerservice.mapper;

import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.entity.CareCenter;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface CareCenterMapper {

    CenterResponse toResponse(CareCenter careCenter);

    List<CenterResponse> toResponseList(List<CareCenter> careCenters);

    @IgnoreServerManagedFields
    @IgnoreServerManagedCenterFields
    CareCenter toCareCenter(CenterCreateRequest request);

    @IgnoreServerManagedFields
    @IgnoreServerManagedCenterFields
    void update(CenterUpdateRequest request, @MappingTarget CareCenter careCenter);
}
