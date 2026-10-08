package mss301.providerservice.service;

import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.entity.*;
import mss301.providerservice.enums.*;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.*;
import mss301.providerservice.security.ProviderAuthorization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaregiverServiceFlowTests {
    @Mock CareCenterRepository centerRepository;
    @Mock CaregiverProfileRepository caregiverRepository;
    @Mock ProviderAuthorization authorization;
    @Mock ProviderViewMapper mapper;
    @InjectMocks CaregiverService service;

    @Test
    void createStartsPendingAndInactive() {
        UUID actorId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        CareCenter center = new CareCenter();
        center.setCenterStatus(CenterStatus.ACTIVE);
        CaregiverCreateRequest request = CaregiverCreateRequest.builder()
                .userId(UUID.randomUUID()).fullName("  Nguyễn An  ").experienceYears(3).build();
        when(centerRepository.findByIdAndDeletedAtIsNull(centerId)).thenReturn(Optional.of(center));
        when(caregiverRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(actorId, centerId, request);

        ArgumentCaptor<CaregiverProfile> captor = ArgumentCaptor.forClass(CaregiverProfile.class);
        verify(caregiverRepository).save(captor.capture());
        assertEquals(VerificationStatus.PENDING, captor.getValue().getVerificationStatus());
        assertEquals(CaregiverStatus.INACTIVE, captor.getValue().getCaregiverStatus());
        assertEquals("Nguyễn An", captor.getValue().getFullName());
        assertEquals(actorId, captor.getValue().getCreatedBy());
    }
}
