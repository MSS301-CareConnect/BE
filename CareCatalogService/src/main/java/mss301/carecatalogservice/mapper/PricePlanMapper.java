package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.entity.PricePlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface PricePlanMapper {

    // isActive do service quyết định (mặc định true khi tạo, giữ giá trị cũ khi sửa mà không gửi).
    @IgnoreServerManagedFields
    @Mapping(target = "centerOffering", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    PricePlan toEntity(PricePlanRequest request);

    @Mapping(target = "centerOfferingId", source = "centerOffering.id")
    PricePlanResponse toResponse(PricePlan entity);

    List<PricePlanResponse> toResponseList(List<PricePlan> entities);

    @IgnoreServerManagedFields
    @Mapping(target = "centerOffering", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    void updateEntityFromRequest(PricePlanRequest request, @MappingTarget PricePlan entity);
}
