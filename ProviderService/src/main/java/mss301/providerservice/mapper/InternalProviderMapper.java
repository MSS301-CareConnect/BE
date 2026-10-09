package mss301.providerservice.mapper;

import mss301.providerservice.dto.response.InternalCaregiverResponse;
import mss301.providerservice.dto.response.InternalCenterResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// Phục vụ API nội bộ /internal/** (Booking gọi) — giữ dù chỉ có một caller.
@Mapper(config = MapStructConfig.class)
public interface InternalProviderMapper {

    @Mapping(target = "caregiverId", source = "id")
    @Mapping(target = "centerId", source = "center.id")
    @Mapping(target = "centerStatus", source = "center.centerStatus")
    // Provider chưa có dữ liệu kỹ năng → luôn null (Booking hiểu là "chưa quản lý", không phải "không làm được").
    @Mapping(target = "careServiceIds", ignore = true)
    InternalCaregiverResponse toInternalCaregiverResponse(CaregiverProfile caregiver);

    @Mapping(target = "centerId", source = "id")
    InternalCenterResponse toInternalCenterResponse(CareCenter center);
}
