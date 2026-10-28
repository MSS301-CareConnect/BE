package mss301.providerservice.mapper;

import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.entity.WorkShift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface ShiftRegistrationMapper {

    @Mapping(target = "workShiftId", source = "workShift.id")
    @Mapping(target = "caregiverId", source = "caregiver.id")
    @Mapping(target = "caregiverName", source = "caregiver.fullName")
    ShiftRegistrationResponse toResponse(ShiftRegistration shiftRegistration);

    List<ShiftRegistrationResponse> toResponseList(List<ShiftRegistration> shiftRegistrations);

    // workShift (đã khóa dòng) và caregiver do service truyền vào; trạng thái + kết quả duyệt do service quản lý.
    @IgnoreServerManagedFields
    @Mapping(target = "registrationStatus", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "version", ignore = true)
    ShiftRegistration toShiftRegistration(ShiftRegistrationRequest request, WorkShift workShift,
                                          CaregiverProfile caregiver);
}
