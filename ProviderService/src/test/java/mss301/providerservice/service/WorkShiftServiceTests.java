package mss301.providerservice.service;

import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.enums.*;
import mss301.providerservice.exception.*;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.mapper.WorkShiftMapper;
import mss301.providerservice.mapper.WorkShiftMapperImpl;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkShiftServiceTests {
    @Mock
    CareCenterRepository careCenterRepository;

    @Mock
    WorkShiftRepository workShiftRepository;

    @Mock
    ShiftRegistrationRepository shiftRegistrationRepository;

    @Mock
    ProviderAuthorization providerAuthorization;

    @Mock
    CaregiverProfileRepository caregiverProfileRepository;

    @Spy
    WorkShiftMapper workShiftMapper = new WorkShiftMapperImpl();

    // 2026-10-22 07:30 giờ Việt Nam (= 00:30 UTC).
    @Spy
    Clock clock = Clock.fixed(Instant.parse("2026-10-22T00:30:00Z"), ZoneOffset.UTC);

    @InjectMocks
    WorkShiftServiceImpl workShiftService;

    @Test
    void endTimeMustBeAfterStartTime() {
        UUID centerId = UUID.randomUUID();
        CareCenter center = new CareCenter();
        center.setCenterStatus(CenterStatus.ACTIVE);
        when(careCenterRepository.findByIdAndDeletedAtIsNull(centerId)).thenReturn(Optional.of(center));
        WorkShiftRequest request = WorkShiftRequest.builder()
                .workDate(LocalDate.now(ZoneOffset.UTC).plusDays(2))
                .startTime(LocalTime.of(16, 0)).endTime(LocalTime.of(8, 0))
                .shiftType("DAY").shiftStatus(ShiftStatus.OPEN).capacity(2).build();

        AppException error = assertThrows(AppException.class,
                () -> workShiftService.create(UUID.randomUUID(), centerId, request));

        assertEquals(ErrorCode.WORK_SHIFT_TIME_INVALID, error.getErrorCode());
    }

    @Test
    void shiftAtEightVietnamTimeIsCreatableAtSevenThirtyVietnamTime() {
        UUID centerId = UUID.randomUUID();
        CareCenter center = new CareCenter();
        center.setCenterStatus(CenterStatus.ACTIVE);
        when(careCenterRepository.findByIdAndDeletedAtIsNull(centerId)).thenReturn(Optional.of(center));
        when(workShiftRepository.save(any(WorkShift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WorkShiftRequest request = WorkShiftRequest.builder()
                .workDate(LocalDate.of(2026, 10, 22))
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0))
                .shiftType("morning").shiftStatus(ShiftStatus.OPEN).capacity(3).build();

        WorkShiftResponse response = workShiftService.create(UUID.randomUUID(), centerId, request).getResult();

        assertEquals(LocalTime.of(8, 0), response.getStartTime());
        assertEquals("MORNING", response.getShiftType());
    }

    @Test
    void shiftThatAlreadyStartedInVietnamTimeIsRejected() {
        UUID centerId = UUID.randomUUID();
        CareCenter center = new CareCenter();
        center.setCenterStatus(CenterStatus.ACTIVE);
        when(careCenterRepository.findByIdAndDeletedAtIsNull(centerId)).thenReturn(Optional.of(center));
        WorkShiftRequest request = WorkShiftRequest.builder()
                .workDate(LocalDate.of(2026, 10, 22))
                .startTime(LocalTime.of(7, 0)).endTime(LocalTime.of(12, 0))
                .shiftType("MORNING").shiftStatus(ShiftStatus.OPEN).capacity(3).build();

        AppException error = assertThrows(AppException.class,
                () -> workShiftService.create(UUID.randomUUID(), centerId, request));

        assertEquals(ErrorCode.WORK_SHIFT_DATE_INVALID, error.getErrorCode());
    }
}
