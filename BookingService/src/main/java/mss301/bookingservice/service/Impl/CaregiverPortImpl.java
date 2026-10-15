package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.ProviderClient;
import mss301.bookingservice.client.dto.ProviderCaregiverResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.CaregiverPort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverPortImpl implements CaregiverPort {

    ProviderClient providerClient;

    @Override
    public AssignedCaregiver resolveAssignable(UUID centerId, UUID caregiverId, Collection<UUID> careServiceIds) {
        RemoteApiResponse<ProviderCaregiverResponse> response = providerClient.getCaregiver(caregiverId);
        ProviderCaregiverResponse caregiver = response == null ? null : response.getResult();
        if (caregiver == null || caregiver.getUserId() == null) {
            throw new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
        }
        if (!centerId.equals(caregiver.getCenterId())) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_IN_CENTER);
        }
        if (!ProviderCaregiverResponse.STATUS_VERIFIED.equals(caregiver.getVerificationStatus())) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_VERIFIED);
        }
        if (!ProviderCaregiverResponse.STATUS_ACTIVE.equals(caregiver.getCaregiverStatus())) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_ACTIVE);
        }
        checkSupportsServices(caregiver, careServiceIds);
        // TODO(Provider): kiểm khung giờ làm việc (caregiver_schedules) khi Provider có API; trùng lịch kiểm ở Booking.
        return AssignedCaregiver.builder()
                .caregiverId(caregiver.getCaregiverId())
                .caregiverUserId(caregiver.getUserId())
                .fullName(caregiver.getFullName())
                .build();
    }

    // null = Provider chưa quản lý kỹ năng → bỏ qua (WARN); danh sách (kể cả rỗng) → phải chứa đủ dịch vụ đã chọn.
    private void checkSupportsServices(ProviderCaregiverResponse caregiver, Collection<UUID> careServiceIds) {
        if (caregiver.getCareServiceIds() == null) {
            // TODO(caregiver_services): bỏ nhánh này khi ProviderService có bảng kỹ năng + API quản lý.
            log.warn("Provider has no skill data for caregiver {}, skipping service check", caregiver.getCaregiverId());
            return;
        }
        if (!caregiver.getCareServiceIds().containsAll(careServiceIds)) {
            throw new AppException(ErrorCode.CAREGIVER_SERVICE_NOT_SUPPORTED);
        }
    }
}
