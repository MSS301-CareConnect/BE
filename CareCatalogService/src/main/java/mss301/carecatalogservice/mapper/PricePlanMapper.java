package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.dto.request.PricePlanDraftRequest;
import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.entity.PricePlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface PricePlanMapper {

    // isActive / discountPercent do service quyết định (mặc định khi tạo, giữ giá trị cũ khi sửa mà không gửi).
    @IgnoreServerManagedFields
    @Mapping(target = "centerOffering", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "discountPercent", ignore = true)
    PricePlan toEntity(PricePlanRequest request);

    // Gói giá gửi kèm lúc tạo dịch vụ trung tâm (chưa có centerOfferingId).
    @IgnoreServerManagedFields
    @Mapping(target = "centerOffering", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "discountPercent", ignore = true)
    PricePlan toEntity(PricePlanDraftRequest request);

    @Mapping(target = "centerOfferingId", source = "centerOffering.id")
    @Mapping(target = "finalPrice", expression = "java(entity.finalPrice())")
    PricePlanResponse toResponse(PricePlan entity);

    List<PricePlanResponse> toResponseList(List<PricePlan> entities);

    @IgnoreServerManagedFields
    @Mapping(target = "centerOffering", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "discountPercent", ignore = true)
    void updateEntityFromRequest(PricePlanRequest request, @MappingTarget PricePlan entity);
}
