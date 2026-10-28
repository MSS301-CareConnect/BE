package mss301.providerservice.mapper;

import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.entity.WorkShift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface CaregiverScheduleMapper {

    @Mapping(target = "registrationId", source = "registration.id")
    @Mapping(target = "caregiverId", source = "caregiver.id")
    @Mapping(target = "caregiverName", source = "caregiver.fullName")
    @Mapping(target = "workShiftId", source = "workShift.id")
    @Mapping(target = "centerId", source = "workShift.center.id")
    @Mapping(target = "workDate", source = "workShift.workDate")
    @Mapping(target = "startTime", source = "workShift.startTime")
    @Mapping(target = "endTime", source = "workShift.endTime")
    ScheduleResponse toResponse(CaregiverSchedule caregiverSchedule);

    List<ScheduleResponse> toResponseList(List<CaregiverSchedule> caregiverSchedules);

    // Lịch sinh ra từ đăng ký vừa duyệt: caregiver lấy từ registration; workShift là bản đã khóa dòng do service
    // truyền vào (chỉ rõ nguồn vì registration cũng có workShift); note chỉ rõ nguồn vì WorkShift cũng có note.
    // Trạng thái + thời điểm phân công do service gán.
    @IgnoreServerManagedFields
    @Mapping(target = "registration", source = "registration")
    @Mapping(target = "caregiver", source = "registration.caregiver")
    @Mapping(target = "workShift", source = "workShift")
    @Mapping(target = "note", source = "note")
    @Mapping(target = "scheduleStatus", ignore = true)
    @Mapping(target = "assignedAt", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    CaregiverSchedule toCaregiverSchedule(ShiftRegistration registration, WorkShift workShift, UUID assignedBy,
                                          String note);
}
