package mss301.providerservice.service;

import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.exception.*;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.Impl.CertificateServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTests {
    @Mock CertificateRepository repository;
    @Mock CaregiverService caregiverService;
    @Mock ProviderAuthorization authorization;
    @Mock ProviderViewMapper mapper;
    @InjectMocks CertificateServiceImpl service;

    @Test
    void expiryDateCannotBeBeforeIssuedDate() {
        UUID caregiverId = UUID.randomUUID();
        when(caregiverService.find(caregiverId)).thenReturn(new CaregiverProfile());
        CertificateRequest request = CertificateRequest.builder()
                .certificateName("Điều dưỡng").issuedBy("Sở Y tế")
                .issuedDate(LocalDate.of(2026, 5, 1))
                .expiryDate(LocalDate.of(2026, 4, 30)).build();

        AppException error = assertThrows(AppException.class,
                () -> service.create(UUID.randomUUID(), caregiverId, request));

        assertEquals(ErrorCode.CERTIFICATE_DATE_INVALID, error.getErrorCode());
    }
}
