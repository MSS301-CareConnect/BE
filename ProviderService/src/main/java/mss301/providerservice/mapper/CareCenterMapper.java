package mss301.providerservice.mapper;

import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.client.dto.CenterPriceSummaryClientResponse;
import mss301.providerservice.dto.response.CenterCardResponse;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.entity.CareCenter;
import org.mapstruct.Mapper;
import mss301.providerservice.dto.response.ProviderStatisticsResponse;
import java.util.Map;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.enums.CenterStatus;
import org.mapstruct.BeanMapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import mss301.providerservice.dto.request.CenterSelfUpdateRequest;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface CareCenterMapper {
    ProviderStatisticsResponse toStatisticsResponse(Map<String, Long> centersByStatus, Map<String, Long> caregiversByStatus, Map<String, Long> caregiversByVerification);

    CenterStatusUpdateRequest toStatusRequest(CenterStatus status, String reason, Long version);


    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "centerName", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "phoneNumber")
    @Mapping(target = "address")
    @Mapping(target = "operatingHours")
    void update(CenterSelfUpdateRequest request, @MappingTarget CareCenter center);

    CenterResponse toResponse(CareCenter careCenter);

    List<CenterResponse> toResponseList(List<CareCenter> careCenters);

    // Thẻ công khai (SRCH-02): giá từ Catalog (priceSummary có thể null), ảnh presigned do service lấy qua Media.
    @Mapping(target = "centerId", source = "careCenter.id")
    @Mapping(target = "minPrice", source = "priceSummary.minPrice")
    @Mapping(target = "serviceCount", source = "priceSummary.serviceCount")
    CenterCardResponse toCardResponse(CareCenter careCenter, CenterPriceSummaryClientResponse priceSummary,
                                      List<String> imageUrls);

    @IgnoreServerManagedFields
    @IgnoreServerManagedCenterFields
    CareCenter toCareCenter(CenterCreateRequest request);

    @IgnoreServerManagedFields
    @IgnoreServerManagedCenterFields
    void update(CenterUpdateRequest request, @MappingTarget CareCenter careCenter);
}
