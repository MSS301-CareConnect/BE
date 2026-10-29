package mss301.providerservice.service;

import mss301.providerservice.client.dto.IdentityUserResponse;
import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.RoleCode;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CaregiverMapper;
import mss301.providerservice.mapper.CaregiverMapperImpl;
import mss301.providerservice.mapper.CertificateMapperImpl;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.Impl.CaregiverServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaregiverServiceFlowTests {

    @Mock
    CareCenterRepository careCenterRepository;

    @Mock
    CaregiverProfileRepository caregiverProfileRepository;

    @Mock
    ProviderAuthorization providerAuthorization;

    @Spy
    CaregiverMapper caregiverMapper = new CaregiverMapperImpl(new CertificateMapperImpl());

    @Mock
    IdentityAccountService identityAccountService;

    @Mock
    TransactionTemplate transactionTemplate;

    @InjectMocks
    CaregiverServiceImpl caregiverService;

    @Test
    void createChecksIdentityBeforeTransactionAndStartsPendingAndInactive() {
        UUID actorId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        CareCenter center = new CareCenter();
        center.setCenterStatus(CenterStatus.ACTIVE);
        CaregiverCreateRequest request = CaregiverCreateRequest.builder()
                .userId(UUID.randomUUID())
                .fullName("  Nguyễn An  ")
                .bio("   ")
                .experienceYears(3)
                .build();
        runTransactionCallbacks();
        when(identityAccountService.requireActiveRole(request.getUserId(), RoleCode.CAREGIVER))
                .thenReturn(IdentityUserResponse.builder().id(request.getUserId()).centerId(centerId).build());
        when(careCenterRepository.findByIdAndDeletedAtIsNull(centerId)).thenReturn(Optional.of(center));
        when(caregiverProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        caregiverService.create(actorId, centerId, request);

        InOrder callOrder = inOrder(identityAccountService, transactionTemplate);
        callOrder.verify(identityAccountService).requireActiveRole(request.getUserId(), RoleCode.CAREGIVER);
        callOrder.verify(transactionTemplate).execute(any());
        ArgumentCaptor<CaregiverProfile> captor = ArgumentCaptor.forClass(CaregiverProfile.class);
        verify(caregiverProfileRepository).save(captor.capture());
        CaregiverProfile saved = captor.getValue();
        assertEquals(VerificationStatus.PENDING, saved.getVerificationStatus());
        assertEquals(CaregiverStatus.INACTIVE, saved.getCaregiverStatus());
        assertEquals("Nguyễn An", saved.getFullName());
        assertNull(saved.getBio());
        assertEquals(BigDecimal.ZERO, saved.getRatingAverage());
        assertEquals(0, saved.getRatingCount());
        assertSame(center, saved.getCenter());
        assertEquals(actorId, saved.getCreatedBy());
        assertEquals(actorId, saved.getUpdatedBy());
    }

    @Test
    void createStopsBeforeTransactionWhenIdentityAccountInvalid() {
        CaregiverCreateRequest request = CaregiverCreateRequest.builder()
                .userId(UUID.randomUUID())
                .fullName("Nguyễn An")
                .experienceYears(3)
                .build();
        doThrow(new AppException(ErrorCode.IDENTITY_USER_INVALID))
                .when(identityAccountService).requireActiveRole(request.getUserId(), RoleCode.CAREGIVER);

        AppException error = assertThrows(AppException.class,
                () -> caregiverService.create(UUID.randomUUID(), UUID.randomUUID(), request));

        assertEquals(ErrorCode.IDENTITY_USER_INVALID, error.getErrorCode());
        verifyNoInteractions(transactionTemplate, careCenterRepository, caregiverProfileRepository);
    }

    // H-12: caregiver được tạo cho trung tâm khác ở Identity → không gắn vào trung tâm này.
    @Test
    void createRejectsAccountOfAnotherCenter() {
        CaregiverCreateRequest request = CaregiverCreateRequest.builder()
                .userId(UUID.randomUUID())
                .fullName("Nguyễn An")
                .experienceYears(3)
                .build();
        when(identityAccountService.requireActiveRole(request.getUserId(), RoleCode.CAREGIVER))
                .thenReturn(IdentityUserResponse.builder().id(request.getUserId()).centerId(UUID.randomUUID()).build());

        AppException error = assertThrows(AppException.class,
                () -> caregiverService.create(UUID.randomUUID(), UUID.randomUUID(), request));

        assertEquals(ErrorCode.IDENTITY_CENTER_MISMATCH, error.getErrorCode());
        verifyNoInteractions(transactionTemplate, careCenterRepository, caregiverProfileRepository);
    }

    @SuppressWarnings("unchecked")
    private void runTransactionCallbacks() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<Object>>getArgument(0).doInTransaction(null));
    }
}
