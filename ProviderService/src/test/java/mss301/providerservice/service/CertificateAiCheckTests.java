package mss301.providerservice.service;

import mss301.providerservice.client.AiClient;
import mss301.providerservice.client.MediaClient;
import mss301.providerservice.client.dto.CertificateCheckClientRequest;
import mss301.providerservice.client.dto.CertificateCheckClientResponse;
import mss301.providerservice.client.dto.DownloadUrlClientResponse;
import mss301.providerservice.client.dto.MediaClientResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.config.ProviderProperties;
import mss301.providerservice.dto.request.CertificateRequest;
import mss301.providerservice.dto.request.CertificateVerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CertificateAiWarningResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.entity.ProviderMedia;
import mss301.providerservice.enums.CertificateAiCheckStatus;
import mss301.providerservice.enums.CertificateRiskLevel;
import mss301.providerservice.enums.CertificateVerificationStatus;
import mss301.providerservice.enums.MediaFileType;
import mss301.providerservice.enums.MediaOwnerType;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CertificateMapperImpl;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.repository.ProviderMediaRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.Impl.CertificateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Bước 3 → 4 → 5 của luồng duyệt chứng chỉ: trung tâm xác nhận xếp hàng AI, scheduler gọi AI, admin duyệt theo rủi ro.
class CertificateAiCheckTests {

    static final Instant NOW = Instant.parse("2026-11-09T03:00:00Z");

    final CertificateRepository certificateRepository = mock(CertificateRepository.class);
    final ProviderMediaRepository providerMediaRepository = mock(ProviderMediaRepository.class);
    final CaregiverService caregiverService = mock(CaregiverService.class);
    final ProviderAuthorization providerAuthorization = mock(ProviderAuthorization.class);
    final MediaClient mediaClient = mock(MediaClient.class);
    final AiClient aiClient = mock(AiClient.class);
    final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    final ProviderProperties properties = new ProviderProperties();
    final UUID managerId = UUID.randomUUID();
    CertificateServiceImpl service;
    Certificate certificate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new CertificateServiceImpl(certificateRepository, providerMediaRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), caregiverService, providerAuthorization, new CertificateMapperImpl(),
                mediaClient, aiClient, transactionTemplate, JsonMapper.builder().build(), properties);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
        doAnswer(invocation -> {
            ((Consumer<Object>) invocation.getArgument(0)).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        CareCenter center = CareCenter.builder().managerUserId(managerId).centerName("Trung tâm An Bình").build();
        center.setId(UUID.randomUUID());
        CaregiverProfile caregiver = CaregiverProfile.builder().fullName("Nguyễn Văn An").center(center).build();
        caregiver.setId(UUID.randomUUID());
        certificate = Certificate.builder().caregiver(caregiver).certificateName("Điều dưỡng cơ bản")
                .issuedBy("Bệnh viện Bạch Mai").credentialNumber("12/QĐ-BV")
                .expiryDate(LocalDate.of(2030, 1, 1))
                .verificationStatus(CertificateVerificationStatus.PENDING).version(3L).build();
        certificate.setId(UUID.randomUUID());
        when(certificateRepository.findForUpdate(certificate.getId())).thenReturn(Optional.of(certificate));
        when(certificateRepository.saveAndFlush(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(certificateRepository.save(any(Certificate.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void centerCannotConfirmCertificateWithoutPhoto() {
        when(providerMediaRepository.existsByOwnerTypeAndOwnerId(MediaOwnerType.CERTIFICATE, certificate.getId()))
                .thenReturn(false);

        AppException error = assertThrows(AppException.class,
                () -> service.verifyCenter(managerId, certificate.getId(), approve(null)));

        assertEquals(ErrorCode.CERTIFICATE_IMAGE_REQUIRED, error.getErrorCode());
    }

    @Test
    void centerConfirmationQueuesAiCheckAndHidesResultFromManager() {
        when(providerMediaRepository.existsByOwnerTypeAndOwnerId(MediaOwnerType.CERTIFICATE, certificate.getId()))
                .thenReturn(true);

        CertificateResponse response = service.verifyCenter(managerId, certificate.getId(), approve(null)).getResult();

        assertThat(certificate.getVerificationStatus()).isEqualTo(CertificateVerificationStatus.CENTER_VERIFIED);
        assertThat(certificate.getAiCheckStatus()).isEqualTo(CertificateAiCheckStatus.QUEUED);
        assertThat(certificate.getAiCheckId()).isNotNull();
        assertThat(certificate.getAiNextAttemptAt()).isEqualTo(NOW);
        assertThat(response.getAiCheckStatus()).isEqualTo(CertificateAiCheckStatus.QUEUED);
        assertThat(response.getCaregiverName()).isEqualTo("Nguyễn Văn An");
        assertThat(response.getCenterName()).isEqualTo("Trung tâm An Bình");
        assertThat(response.getAiRiskLevel()).isNull();
    }

    @Test
    void adminMustGiveReasonToApproveHighRiskCertificate() {
        certificate.setVerificationStatus(CertificateVerificationStatus.CENTER_VERIFIED);
        certificate.setAiCheckStatus(CertificateAiCheckStatus.COMPLETED);
        certificate.setAiRiskLevel(CertificateRiskLevel.HIGH);

        AppException error = assertThrows(AppException.class,
                () -> service.verify(UUID.randomUUID(), certificate.getId(), approve(null)));
        assertEquals(ErrorCode.CERTIFICATE_RISK_REASON_REQUIRED, error.getErrorCode());

        service.verify(UUID.randomUUID(), certificate.getId(), approve("Đã gọi trung tâm cấp xác nhận"));
        assertThat(certificate.getVerificationStatus()).isEqualTo(CertificateVerificationStatus.ADMIN_VERIFIED);
    }

    @Test
    void adminApprovesLowRiskWithoutReasonAndSeesAiResult() {
        certificate.setVerificationStatus(CertificateVerificationStatus.CENTER_VERIFIED);
        certificate.setAiCheckStatus(CertificateAiCheckStatus.COMPLETED);
        certificate.setAiRiskLevel(CertificateRiskLevel.LOW);
        certificate.setAiWarnings("[{\"code\":\"NOT_FOUND\",\"field\":\"ISSUED_DATE\",\"declaredValue\":\"2025-01-01\"}]");
        certificate.setAiExtracted("{\"holderName\":\"NGUYỄN VĂN AN\",\"expiryDate\":\"2030-01-01\"}");

        CertificateResponse response = service.verify(UUID.randomUUID(), certificate.getId(), approve(null)).getResult();

        assertThat(response.getAiRiskLevel()).isEqualTo(CertificateRiskLevel.LOW);
        assertThat(response.getAiWarnings()).extracting(CertificateAiWarningResponse::getField).containsExactly("ISSUED_DATE");
        assertThat(response.getAiExtracted().getHolderName()).isEqualTo("NGUYỄN VĂN AN");
        assertThat(response.getAiExtracted().getExpiryDate()).isEqualTo(LocalDate.of(2030, 1, 1));
        assertThat(response.getCertificateName()).isEqualTo("Điều dưỡng cơ bản");
    }

    @Test
    void editingCertificateRestartsReviewAndDropsAiResult() {
        certificate.setVerificationStatus(CertificateVerificationStatus.CENTER_VERIFIED);
        certificate.queueAiCheck(NOW);
        CertificateRequest request = CertificateRequest.builder().certificateName("Điều dưỡng")
                .issuedBy("Sở Y tế").version(3L).build();

        service.update(certificate.getCaregiver().getUserId(), certificate.getId(), request);

        assertThat(certificate.getVerificationStatus()).isEqualTo(CertificateVerificationStatus.PENDING);
        assertThat(certificate.getAiCheckStatus()).isNull();
        assertThat(certificate.getAiCheckId()).isNull();
    }

    @Test
    void schedulerSendsDeclaredFieldsAndPhotosThenStoresResult() {
        UUID checkId = prepareClaimedCheck();
        when(aiClient.checkCertificate(any())).thenReturn(new RemoteApiResponse<>(200, "ok",
                CertificateCheckClientResponse.builder().riskLevel(CertificateRiskLevel.MEDIUM)
                        .warnings(List.of(CertificateAiWarningResponse.builder().code("MISMATCH").field("ISSUED_BY").build()))
                        .model("vision").checkedAt(NOW).build()));
        when(certificateRepository.completeAiCheck(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        service.processAiCheck(certificate.getId());

        ArgumentCaptor<CertificateCheckClientRequest> request = ArgumentCaptor.forClass(CertificateCheckClientRequest.class);
        verify(aiClient).checkCertificate(request.capture());
        assertThat(request.getValue().getHolderName()).isEqualTo("Nguyễn Văn An");
        assertThat(request.getValue().getCredentialNumber()).isEqualTo("12/QĐ-BV");
        assertThat(request.getValue().getImages()).singleElement()
                .satisfies(image -> {
                    assertThat(image.getUrl()).isEqualTo("https://r2.test/cert.jpg");
                    assertThat(image.getMimeType()).isEqualTo("image/jpeg");
                });
        verify(certificateRepository).completeAiCheck(eq(certificate.getId()), eq(checkId),
                eq(CertificateAiCheckStatus.PROCESSING), eq(CertificateAiCheckStatus.COMPLETED),
                eq(CertificateRiskLevel.MEDIUM), anyString(), isNull(), eq("vision"), eq(NOW));
    }

    @Test
    void aiOutageIsRetriedAndRejectionStops() {
        UUID checkId = prepareClaimedCheck();
        when(aiClient.checkCertificate(any())).thenThrow(new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE));

        service.processAiCheck(certificate.getId());

        verify(certificateRepository).failAiCheck(eq(certificate.getId()), eq(checkId),
                eq(CertificateAiCheckStatus.PROCESSING), eq(CertificateAiCheckStatus.QUEUED), eq(1),
                eq(NOW.plusSeconds(30)), eq("AI_SERVICE_UNAVAILABLE"));

        doThrow(new AppException(ErrorCode.REMOTE_COMMAND_REJECTED)).when(aiClient).checkCertificate(any());
        service.processAiCheck(certificate.getId());

        verify(certificateRepository).failAiCheck(eq(certificate.getId()), eq(checkId),
                eq(CertificateAiCheckStatus.PROCESSING), eq(CertificateAiCheckStatus.FAILED), eq(1),
                isNull(), eq("REMOTE_COMMAND_REJECTED"));
    }

    @Test
    void checkAlreadyTakenByAnotherScanIsSkipped() {
        certificate.setVerificationStatus(CertificateVerificationStatus.CENTER_VERIFIED);
        certificate.queueAiCheck(NOW);
        when(certificateRepository.findWithCaregiverById(certificate.getId())).thenReturn(Optional.of(certificate));
        when(certificateRepository.claimAiCheck(any(), any(), anyCollection(), any(), any(), any())).thenReturn(0);

        service.processAiCheck(certificate.getId());

        verify(aiClient, never()).checkCertificate(any());
        verify(certificateRepository, never()).failAiCheck(any(), any(), any(), any(), anyInt(), any(), any());
    }

    private UUID prepareClaimedCheck() {
        certificate.setVerificationStatus(CertificateVerificationStatus.CENTER_VERIFIED);
        certificate.queueAiCheck(NOW);
        UUID checkId = certificate.getAiCheckId();
        UUID mediaFileId = UUID.randomUUID();
        when(certificateRepository.findWithCaregiverById(certificate.getId())).thenReturn(Optional.of(certificate));
        when(certificateRepository.claimAiCheck(eq(certificate.getId()), eq(checkId), anyCollection(),
                eq(CertificateAiCheckStatus.PROCESSING), eq(NOW), any())).thenReturn(1);
        when(providerMediaRepository.findAllByOwnerTypeAndOwnerIdOrderByDisplayOrderAsc(MediaOwnerType.CERTIFICATE,
                certificate.getId())).thenReturn(List.of(ProviderMedia.builder().mediaFileId(mediaFileId)
                .mediaFileType(MediaFileType.IMAGE).build()));
        when(mediaClient.get(mediaFileId)).thenReturn(APIResponse.success("ok",
                MediaClientResponse.builder().mediaFileId(mediaFileId).contentType("image/jpeg").build()));
        when(mediaClient.createDownloadUrl(mediaFileId)).thenReturn(APIResponse.success("ok",
                DownloadUrlClientResponse.builder().mediaFileId(mediaFileId).downloadUrl("https://r2.test/cert.jpg").build()));
        return checkId;
    }

    private CertificateVerificationRequest approve(String reason) {
        return CertificateVerificationRequest.builder().approved(true).reason(reason).version(3L).build();
    }
}
