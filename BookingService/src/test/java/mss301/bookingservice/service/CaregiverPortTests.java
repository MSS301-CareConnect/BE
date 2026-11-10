package mss301.bookingservice.service;

import mss301.bookingservice.client.ProviderServiceClient;
import mss301.bookingservice.client.dto.ProviderCaregiverResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.Impl.CaregiverPortImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Xác minh caregiver qua Provider. Kỹ năng: null, [] hoặc thiếu dịch vụ → từ chối (PRV-01).
 */
@ExtendWith(MockitoExtension.class)
class CaregiverPortTests {

    static final UUID CENTER_ID = UUID.randomUUID();
    static final UUID CAREGIVER_ID = UUID.randomUUID();
    static final UUID USER_ID = UUID.randomUUID();
    static final UUID SERVICE_A = UUID.randomUUID();
    static final UUID SERVICE_B = UUID.randomUUID();

    @Mock
    ProviderServiceClient providerServiceClient;

    @InjectMocks
    CaregiverPortImpl caregiverPort;

    void givenCaregiver(UUID centerId, String verification, String status, List<UUID> careServiceIds) {
        ProviderCaregiverResponse caregiver = ProviderCaregiverResponse.builder()
                .caregiverId(CAREGIVER_ID)
                .userId(USER_ID)
                .centerId(centerId)
                .fullName("Chị Hoa")
                .verificationStatus(verification)
                .caregiverStatus(status)
                .careServiceIds(careServiceIds)
                .build();
        RemoteApiResponse<ProviderCaregiverResponse> response = new RemoteApiResponse<>();
        response.setResult(caregiver);
        when(providerServiceClient.getCaregiver(CAREGIVER_ID)).thenReturn(response);
    }

    // PRV-01: caregiver chưa được gán kỹ năng nào (Provider trả null / []) không nhận được dịch vụ nào.
    @Test
    void nullSkillsAreTreatedAsNoSkills() {
        givenCaregiver(CENTER_ID, "VERIFIED", "ACTIVE", null);
        assertError(ErrorCode.CAREGIVER_SERVICE_NOT_SUPPORTED, List.of(SERVICE_A));
    }

    @Test
    void emptySkillsRejectsAnyService() {
        givenCaregiver(CENTER_ID, "VERIFIED", "ACTIVE", List.of());
        assertError(ErrorCode.CAREGIVER_SERVICE_NOT_SUPPORTED, List.of(SERVICE_A));
    }

    @Test
    void missingOneServiceIsRejected() {
        givenCaregiver(CENTER_ID, "VERIFIED", "ACTIVE", List.of(SERVICE_A));
        assertError(ErrorCode.CAREGIVER_SERVICE_NOT_SUPPORTED, List.of(SERVICE_A, SERVICE_B));
    }

    @Test
    void allServicesSupportedIsAccepted() {
        givenCaregiver(CENTER_ID, "VERIFIED", "ACTIVE", List.of(SERVICE_B, SERVICE_A, UUID.randomUUID()));
        assertThat(caregiverPort.resolveAssignable(CENTER_ID, CAREGIVER_ID, List.of(SERVICE_A, SERVICE_B))
                .getCaregiverUserId()).isEqualTo(USER_ID);
    }

    @Test
    void otherCenterIsRejected() {
        givenCaregiver(UUID.randomUUID(), "VERIFIED", "ACTIVE", null);
        assertError(ErrorCode.CAREGIVER_NOT_IN_CENTER, List.of(SERVICE_A));
    }

    @Test
    void unverifiedIsRejected() {
        givenCaregiver(CENTER_ID, "PENDING", "ACTIVE", null);
        assertError(ErrorCode.CAREGIVER_NOT_VERIFIED, List.of(SERVICE_A));
    }

    @Test
    void inactiveIsRejected() {
        givenCaregiver(CENTER_ID, "VERIFIED", "SUSPENDED", null);
        assertError(ErrorCode.CAREGIVER_NOT_ACTIVE, List.of(SERVICE_A));
    }

    @Test
    void emptyProviderResponseIsUnavailable() {
        when(providerServiceClient.getCaregiver(CAREGIVER_ID)).thenReturn(new RemoteApiResponse<>());
        assertError(ErrorCode.PROVIDER_UNAVAILABLE, List.of(SERVICE_A));
    }

    private void assertError(ErrorCode expected, List<UUID> careServiceIds) {
        assertThatThrownBy(() -> caregiverPort.resolveAssignable(CENTER_ID, CAREGIVER_ID, careServiceIds))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
