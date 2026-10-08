package mss301.providerservice.service;

import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CareCenterMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.service.Impl.CareCenterServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareCenterServiceTests {

    @Mock
    CareCenterRepository careCenterRepository;

    @Mock
    CareCenterMapper careCenterMapper;

    @InjectMocks
    CareCenterServiceImpl careCenterService;

    @Test
    void createNormalizesEmailAndStartsPending() {
        UUID actorId = UUID.randomUUID();
        CenterCreateRequest request = CenterCreateRequest.builder()
                .centerName("  Trung tâm An Tâm  ")
                .email("  CONTACT@ANTAM.VN  ")
                .address("  Thành phố Hồ Chí Minh  ")
                .build();
        CareCenter entity = new CareCenter();
        entity.setCenterName(request.getCenterName());
        entity.setEmail(request.getEmail());
        entity.setAddress(request.getAddress());
        CenterResponse response = new CenterResponse();

        when(careCenterMapper.toCareCenter(request)).thenReturn(entity);
        when(careCenterRepository.save(entity)).thenReturn(entity);
        when(careCenterMapper.toResponse(entity)).thenReturn(response);

        careCenterService.create(actorId, request);

        assertEquals("contact@antam.vn", entity.getEmail());
        assertEquals("Trung tâm An Tâm", entity.getCenterName());
        assertEquals(CenterStatus.PENDING, entity.getCenterStatus());
        assertEquals(actorId, entity.getCreatedBy());
        verify(careCenterRepository).existsByEmailAndDeletedAtIsNull("contact@antam.vn");
    }

    @Test
    void suspendedStatusRequiresReason() {
        UUID centerId = UUID.randomUUID();
        CareCenter entity = new CareCenter();
        entity.setVersion(1L);
        when(careCenterRepository.findByIdAndDeletedAtIsNull(centerId)).thenReturn(Optional.of(entity));
        CenterStatusUpdateRequest request = CenterStatusUpdateRequest.builder()
                .status(CenterStatus.SUSPENDED)
                .version(1L)
                .build();

        AppException exception = assertThrows(AppException.class,
                () -> careCenterService.updateStatus(UUID.randomUUID(), centerId, request));

        assertEquals(ErrorCode.CENTER_STATUS_REASON_REQUIRED, exception.getErrorCode());
    }

    @Test
    void deleteArchivesInsteadOfPhysicalDelete() {
        UUID actorId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        CareCenter entity = new CareCenter();
        entity.setVersion(2L);
        when(careCenterRepository.findByIdAndDeletedAtIsNull(centerId)).thenReturn(Optional.of(entity));
        when(careCenterRepository.save(any(CareCenter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        careCenterService.delete(actorId, centerId, 2L);

        assertEquals(CenterStatus.ARCHIVED, entity.getCenterStatus());
        assertEquals(actorId, entity.getUpdatedBy());
        verify(careCenterRepository).save(entity);
    }
}
