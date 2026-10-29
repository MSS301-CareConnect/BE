package mss301.providerservice.service;

import mss301.providerservice.dto.request.CenterUpdateRequest;
import mss301.providerservice.client.dto.IdentityUserResponse;
import mss301.providerservice.client.BookingArchiveClient;
import mss301.providerservice.client.dto.BookingCountResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.dto.request.CenterCreateRequest;
import mss301.providerservice.dto.request.CenterStatusUpdateRequest;
import mss301.providerservice.dto.response.CenterResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.RoleCode;
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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;
import org.springframework.transaction.TransactionStatus;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareCenterServiceTests {

    @Mock
    CareCenterRepository careCenterRepository;

    @Mock
    CareCenterMapper careCenterMapper;

    @Mock
    IdentityAccountService identityAccountService;

    @Mock
    TransactionTemplate transactionTemplate;

    @Mock
    BookingArchiveClient bookingArchiveClient;

    @Mock
    ProviderOutboxService providerOutboxService;

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

        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<Object>>getArgument(0).doInTransaction(null));
        when(careCenterMapper.toCareCenter(request)).thenReturn(entity);
        when(careCenterRepository.save(entity)).thenReturn(entity);
        when(careCenterMapper.toResponse(entity)).thenReturn(response);

        careCenterService.create(actorId, request);

        assertEquals("contact@antam.vn", entity.getEmail());
        assertEquals("Trung tâm An Tâm", entity.getCenterName());
        assertEquals(CenterStatus.PENDING, entity.getCenterStatus());
        assertEquals(actorId, entity.getCreatedBy());
        verify(careCenterRepository).existsByEmailAndDeletedAtIsNull("contact@antam.vn");
        // Không có managerUserId thì không gọi IdentityService.
        verifyNoInteractions(identityAccountService);
    }

    @Test
    void createWithManagerChecksIdentityBeforeTransaction() {
        UUID managerUserId = UUID.randomUUID();
        CenterCreateRequest request = CenterCreateRequest.builder()
                .centerName("Trung tâm An Tâm")
                .email("contact@antam.vn")
                .address("Thành phố Hồ Chí Minh")
                .managerUserId(managerUserId)
                .build();
        doThrow(new AppException(ErrorCode.IDENTITY_USER_INVALID))
                .when(identityAccountService).requireActiveRole(managerUserId, RoleCode.CENTER_MANAGER);

        AppException exception = assertThrows(AppException.class,
                () -> careCenterService.create(UUID.randomUUID(), request));

        assertEquals(ErrorCode.IDENTITY_USER_INVALID, exception.getErrorCode());
        verifyNoInteractions(transactionTemplate, careCenterRepository);
    }

    // H-12: tạo trung tâm không gán manager được (manager đã thuộc trung tâm khác ở Identity); cập nhật thì phải khớp.
    @Test
    void managerMustBelongToTheSameCenterInIdentity() {
        UUID managerUserId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();
        CenterCreateRequest createRequest = CenterCreateRequest.builder()
                .centerName("Trung tâm An Tâm")
                .email("contact@antam.vn")
                .address("Thành phố Hồ Chí Minh")
                .managerUserId(managerUserId)
                .build();
        when(identityAccountService.requireActiveRole(managerUserId, RoleCode.CENTER_MANAGER))
                .thenReturn(IdentityUserResponse.builder().id(managerUserId).centerId(UUID.randomUUID()).build());

        AppException onCreate = assertThrows(AppException.class,
                () -> careCenterService.create(UUID.randomUUID(), createRequest));
        AppException onUpdate = assertThrows(AppException.class,
                () -> careCenterService.update(UUID.randomUUID(), centerId,
                        CenterUpdateRequest.builder().managerUserId(managerUserId).build()));

        assertEquals(ErrorCode.IDENTITY_CENTER_MISMATCH, onCreate.getErrorCode());
        assertEquals(ErrorCode.IDENTITY_CENTER_MISMATCH, onUpdate.getErrorCode());
        verifyNoInteractions(transactionTemplate, careCenterRepository);
    }

    @Test
    void suspendedStatusRequiresReason() {
        UUID centerId = UUID.randomUUID();
        CareCenter entity = new CareCenter();
        entity.setVersion(1L);
        entity.setCenterStatus(CenterStatus.ACTIVE);
        when(careCenterRepository.findByIdAndDeletedAtIsNull(centerId)).thenReturn(Optional.of(entity));
        when(careCenterRepository.findForUpdate(centerId)).thenReturn(Optional.of(entity));
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<Object>>getArgument(0).doInTransaction(null));
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
        entity.setCenterStatus(CenterStatus.ACTIVE);
        when(bookingArchiveClient.list(any(), anyString(), anyInt(), anyInt()))
                .thenReturn(RemoteApiResponse.<BookingCountResponse>builder()
                        .code(200).result(BookingCountResponse.builder().totalElements(0L).build()).build());
        when(careCenterRepository.findForUpdate(centerId)).thenReturn(Optional.of(entity));
        when(careCenterMapper.toStatusRequest(CenterStatus.ARCHIVED, "Lưu trữ bởi admin", 2L))
                .thenReturn(CenterStatusUpdateRequest.builder().status(CenterStatus.ARCHIVED).reason("Lưu trữ bởi admin").version(2L).build());
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        careCenterService.delete(actorId, centerId, 2L);

        assertEquals(CenterStatus.ARCHIVED, entity.getCenterStatus());
        assertEquals(actorId, entity.getUpdatedBy());
        verify(careCenterRepository).saveAndFlush(entity);
    }
}
