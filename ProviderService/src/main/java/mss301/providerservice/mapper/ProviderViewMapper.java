package mss301.providerservice.mapper;

import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.entity.WorkShift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface ProviderViewMapper {

    @Mapping(target = "centerId", source = "center.id")
    @Mapping(target = "centerName", source = "center.centerName")
    @Mapping(target = "centerStatus", source = "center.centerStatus")
    CaregiverResponse caregiver(CaregiverProfile value);

    List<CaregiverResponse> toCaregiverResponseList(List<CaregiverProfile> caregivers);

    @Mapping(target = "caregiverId", source = "caregiver.id")
    CertificateResponse certificate(Certificate value);

    List<CertificateResponse> toCertificateResponseList(List<Certificate> certificates);

    @Mapping(target = "id", source = "value.id")
    @Mapping(target = "centerId", source = "value.center.id")
    @Mapping(target = "centerName", source = "value.center.centerName")
    @Mapping(target = "workDate", source = "value.workDate")
    @Mapping(target = "startTime", source = "value.startTime")
    @Mapping(target = "endTime", source = "value.endTime")
    @Mapping(target = "shiftType", source = "value.shiftType")
    @Mapping(target = "shiftStatus", source = "value.shiftStatus")
    @Mapping(target = "capacity", source = "value.capacity")
    @Mapping(target = "approvedCount", source = "approvedCount")
    @Mapping(target = "registrationDeadline", source = "value.registrationDeadline")
    @Mapping(target = "note", source = "value.note")
    @Mapping(target = "version", source = "value.version")
    @Mapping(target = "createdAt", source = "value.createdAt")
    @Mapping(target = "updatedAt", source = "value.updatedAt")
    WorkShiftResponse shift(WorkShift value, long approvedCount);

    @Mapping(target = "workShiftId", source = "workShift.id")
    @Mapping(target = "caregiverId", source = "caregiver.id")
    @Mapping(target = "caregiverName", source = "caregiver.fullName")
    ShiftRegistrationResponse registration(ShiftRegistration value);

    List<ShiftRegistrationResponse> toShiftRegistrationResponseList(List<ShiftRegistration> registrations);

    @Mapping(target = "registrationId", source = "registration.id")
    @Mapping(target = "caregiverId", source = "caregiver.id")
    @Mapping(target = "caregiverName", source = "caregiver.fullName")
    @Mapping(target = "workShiftId", source = "workShift.id")
    @Mapping(target = "centerId", source = "workShift.center.id")
    @Mapping(target = "workDate", source = "workShift.workDate")
    @Mapping(target = "startTime", source = "workShift.startTime")
    @Mapping(target = "endTime", source = "workShift.endTime")
    ScheduleResponse schedule(CaregiverSchedule value);

    List<ScheduleResponse> toScheduleResponseList(List<CaregiverSchedule> schedules);
}
