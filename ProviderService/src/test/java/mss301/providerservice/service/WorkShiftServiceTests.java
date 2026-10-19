package mss301.providerservice.service;

import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.enums.*;
import mss301.providerservice.exception.*;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.*;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.Impl.WorkShiftServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkShiftServiceTests {
    @Mock CareCenterRepository centerRepository;
    @Mock WorkShiftRepository shiftRepository;
    @Mock ShiftRegistrationRepository registrationRepository;
    @Mock ProviderAuthorization authorization;
    @Mock ProviderViewMapper mapper;
    @InjectMocks WorkShiftServiceImpl service;

    @Test
    void endTimeMustBeAfterStartTime() {
        UUID centerId = UUID.randomUUID();
        CareCenter center = new CareCenter();
        center.setCenterStatus(CenterStatus.ACTIVE);
        when(centerRepository.findByIdAndDeletedAtIsNull(centerId)).thenReturn(Optional.of(center));
        WorkShiftRequest request = WorkShiftRequest.builder()
                .workDate(LocalDate.now(ZoneOffset.UTC).plusDays(2))
                .startTime(LocalTime.of(16, 0)).endTime(LocalTime.of(8, 0))
                .shiftType("DAY").shiftStatus(ShiftStatus.OPEN).capacity(2).build();

        AppException error = assertThrows(AppException.class,
                () -> service.create(UUID.randomUUID(), centerId, request));

        assertEquals(ErrorCode.WORK_SHIFT_TIME_INVALID, error.getErrorCode());
    }
}
