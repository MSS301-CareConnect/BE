package mss301.bookingservice.mapper;

import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.CenterBookingPolicyRequest;
import mss301.bookingservice.dto.response.CenterBookingPolicyResponse;
import mss301.bookingservice.entity.CenterBookingPolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface CenterBookingPolicyMapper {

    CenterBookingPolicyResponse toResponse(CenterBookingPolicy policy, boolean customized);

    // Policy mặc định (chưa lưu) cho trung tâm chưa cấu hình riêng.
    @IgnoreServerManagedFields
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    CenterBookingPolicy toDefaultPolicy(BookingProperties.PolicyDefaults defaults, UUID centerId);

    @IgnoreServerManagedFields
    @Mapping(target = "version", ignore = true)
    CenterBookingPolicy toCenterBookingPolicy(CenterBookingPolicyRequest request, UUID centerId, UUID updatedBy);

    // PUT thay toàn bộ giá trị; trung tâm và version không đổi qua request (version do JPA tăng).
    @IgnoreServerManagedFields
    @Mapping(target = "centerId", ignore = true)
    @Mapping(target = "version", ignore = true)
    void update(CenterBookingPolicyRequest request, UUID updatedBy, @MappingTarget CenterBookingPolicy policy);
}
