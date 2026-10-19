package mss301.providerservice.service;

import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.entity.*;
import mss301.providerservice.enums.*;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.*;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.Impl.ShiftRegistrationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftRegistrationServiceTests {
    @Mock WorkShiftRepository shiftRepository;
    @Mock ShiftRegistrationRepository registrationRepository;
    @Mock CaregiverScheduleRepository scheduleRepository;
    @Mock CaregiverService caregiverService;
    @Mock ProviderAuthorization authorization;
    @Mock ProviderViewMapper mapper;
    @InjectMocks ShiftRegistrationServiceImpl service;

    @Test
    void approvingRegistrationCreatesScheduleAndClosesFullShift() {
        UUID registrationId = UUID.randomUUID();
        CareCenter center = new CareCenter();
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

        when(registrationRepository.findByIdForUpdate(registrationId)).thenReturn(Optional.of(registration));
        when(shiftRepository.findByIdForUpdate(shift.getId())).thenReturn(Optional.of(shift));
        when(registrationRepository.countByWorkShiftIdAndRegistrationStatus(
                shift.getId(), RegistrationStatus.APPROVED)).thenReturn(0L);
        when(scheduleRepository.hasOverlap(eq(caregiver.getId()), any(), any(), any(), any())).thenReturn(false);
        when(registrationRepository.save(registration)).thenReturn(registration);
        RegistrationReviewRequest request = RegistrationReviewRequest.builder()
                .status(RegistrationStatus.APPROVED).version(0L).build();

        service.review(UUID.randomUUID(), registrationId, request);

        assertEquals(RegistrationStatus.APPROVED, registration.getRegistrationStatus());
        assertEquals(ShiftStatus.CLOSED, shift.getShiftStatus());
        verify(scheduleRepository).save(any(CaregiverSchedule.class));
        verify(shiftRepository).save(shift);
    }
}
