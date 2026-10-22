package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;
import mss301.carecatalogservice.entity.CatalogItem;
import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.dto.response.PriceSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class, uses = {CatalogItemMapper.class, PricePlanMapper.class})
public interface CenterOfferingMapper {

    @IgnoreServerManagedFields
    @Mapping(target = "catalogItem", ignore = true)
    CenterOffering toEntity(CenterOfferingRequest request);

    // Kết quả tạo / sửa: không kèm bảng giá (null → ẩn khỏi JSON).
    @Mapping(target = "pricePlans", ignore = true)
    CenterOfferingResponse toResponse(CenterOffering entity);

    // Khi duyệt dịch vụ trung tâm: kèm các gói giá service đã lọc theo người xem (khách chỉ thấy gói active).
    CenterOfferingResponse toResponse(CenterOffering centerOffering, List<PricePlan> pricePlans);

    PriceSummaryResponse toPriceSummaryResponse(UUID centerId, BigDecimal minPrice);

    // Một dòng báo giá: chỉ trả bản ghi đã được service kiểm ACTIVE nên active luôn true.
    @Mapping(target = "careServiceId", source = "catalogItem.id")
    @Mapping(target = "centerServiceId", source = "centerOffering.id")
    @Mapping(target = "pricePlanId", source = "pricePlan.id")
    @Mapping(target = "serviceName", source = "catalogItem.name")
    @Mapping(target = "active", constant = "true")
    ServiceQuoteResponse toQuoteResponse(CatalogItem catalogItem, CenterOffering centerOffering, PricePlan pricePlan);

    @IgnoreServerManagedFields
    @Mapping(target = "catalogItem", ignore = true)
    @Mapping(target = "centerId", ignore = true)
    void updateEntityFromRequest(CenterOfferingRequest request, @MappingTarget CenterOffering entity);
}
