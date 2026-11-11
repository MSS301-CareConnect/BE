package mss301.providerservice.service;

import mss301.providerservice.repository.CaregiverTimeOffRepository;
import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.mapper.CaregiverScheduleMapper;
import mss301.providerservice.mapper.CaregiverScheduleMapperImpl;
import mss301.providerservice.mapper.ShiftRegistrationMapper;
import mss301.providerservice.mapper.ShiftRegistrationMapperImpl;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.repository.CaregiverTimeOffRepository;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.Impl.ShiftRegistrationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
class ShiftRegistrationServiceTests {
    @Mock
    WorkShiftRepository workShiftRepository;

    @Mock
    ShiftRegistrationRepository shiftRegistrationRepository;

    @Mock
    CaregiverScheduleRepository caregiverScheduleRepository;

    @Mock
    CaregiverTimeOffRepository caregiverTimeOffRepository;

    @Mock
    CaregiverService caregiverService;

    @Mock
    ProviderAuthorization providerAuthorization;

    @Spy
    ShiftRegistrationMapper shiftRegistrationMapper = new ShiftRegistrationMapperImpl();

    @Spy
    CaregiverScheduleMapper caregiverScheduleMapper = new CaregiverScheduleMapperImpl();

    @Spy
    Clock clock = Clock.systemUTC();

    @InjectMocks
    ShiftRegistrationServiceImpl shiftRegistrationService;

    @Test
    void approvingRegistrationCreatesScheduleAndClosesFullShift() {
        UUID actorId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        CareCenter center = new CareCenter();
        center.setCenterStatus(CenterStatus.ACTIVE);
        CaregiverProfile caregiver = new CaregiverProfile();
        caregiver.setId(UUID.randomUUID());
        WorkShift shift = new WorkShift();
        shift.setId(UUID.randomUUID());
        shift.setCenter(center);
        shift.setWorkDate(LocalDate.now().plusDays(2));
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(12, 0));
        shift.setShiftStatus(ShiftStatus.OPEN);
        shift.setCapacity(1);
        ShiftRegistration registration = new ShiftRegistration();
        registration.setWorkShift(shift);
        registration.setCaregiver(caregiver);
        registration.setRegistrationStatus(RegistrationStatus.PENDING);
        registration.setVersion(0L);

        when(shiftRegistrationRepository.findByIdForUpdate(registrationId)).thenReturn(Optional.of(registration));
        when(workShiftRepository.findByIdForUpdate(shift.getId())).thenReturn(Optional.of(shift));
        when(shiftRegistrationRepository.countByWorkShiftIdAndRegistrationStatus(
                shift.getId(), RegistrationStatus.APPROVED)).thenReturn(0L);
        when(caregiverScheduleRepository.hasOverlap(eq(caregiver.getId()), any(), any(), any(), any())).thenReturn(false);
        when(shiftRegistrationRepository.save(registration)).thenReturn(registration);
        RegistrationReviewRequest request = RegistrationReviewRequest.builder()
                .status(RegistrationStatus.APPROVED).version(0L).build();

        shiftRegistrationService.review(actorId, registrationId, request);

        assertEquals(RegistrationStatus.APPROVED, registration.getRegistrationStatus());
        assertEquals(ShiftStatus.CLOSED, shift.getShiftStatus());
        ArgumentCaptor<CaregiverSchedule> scheduleCaptor = ArgumentCaptor.forClass(CaregiverSchedule.class);
        verify(caregiverScheduleRepository).save(scheduleCaptor.capture());
        CaregiverSchedule schedule = scheduleCaptor.getValue();
        assertEquals(ScheduleStatus.SCHEDULED, schedule.getScheduleStatus());
        assertSame(registration, schedule.getRegistration());
        assertSame(caregiver, schedule.getCaregiver());
        assertSame(shift, schedule.getWorkShift());
        assertEquals(actorId, schedule.getAssignedBy());
        assertNotNull(schedule.getAssignedAt());
        verify(workShiftRepository).save(shift);
    }
}
