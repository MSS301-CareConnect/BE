package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.ProviderServiceClient;
import mss301.bookingservice.client.dto.AvailabilityCheckResponse;
import mss301.bookingservice.client.dto.ProviderCaregiverResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.service.CaregiverPort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverPortImpl implements CaregiverPort {

    static int MAX_SLOTS_PER_CHECK = 60;

    ProviderServiceClient providerServiceClient;
    OccurrenceMapper occurrenceMapper;

    @Override
    public AssignedCaregiver resolveAssignable(UUID centerId, UUID caregiverId, Collection<UUID> careServiceIds) {
        RemoteApiResponse<ProviderCaregiverResponse> response;
        try {
            response = providerServiceClient.getCaregiver(caregiverId);
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            throw toProviderException(caregiverId, e);
        }
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

    @Override
    public void checkAvailable(UUID caregiverId, List<OccurrenceSlot> slots) {
        // Provider nhận tối đa 60 khoảng / lần — booking dài hơn thì hỏi nhiều lần.
        for (int from = 0; from < slots.size(); from += MAX_SLOTS_PER_CHECK) {
            List<OccurrenceSlot> chunk = slots.subList(from, Math.min(from + MAX_SLOTS_PER_CHECK, slots.size()));
            AvailabilityCheckResponse availability = requestAvailability(caregiverId, chunk);
            if (!availability.isAvailable()) {
                // Chi tiết buổi vi phạm chỉ vào log (không lộ lịch nghỉ của caregiver cho khách).
                log.info("Caregiver {} not available: {}", caregiverId, availability.getViolations());
                throw new AppException(ErrorCode.CAREGIVER_NOT_AVAILABLE);
            }
        }
    }

    private AvailabilityCheckResponse requestAvailability(UUID caregiverId, List<OccurrenceSlot> slots) {
        RemoteApiResponse<AvailabilityCheckResponse> response;
        try {
            response = providerServiceClient.checkAvailability(occurrenceMapper.toAvailabilityCheckRequest(caregiverId, slots));
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Provider availability check failed for caregiver {} ({})", caregiverId, e.getClass().getSimpleName());
            throw new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
        }
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
        }
        return response.getResult();
    }

    // Kỹ năng do ProviderService quản lý (caregiver_services, PRV-01): caregiver phải làm được TẤT CẢ dịch vụ đã chọn.
    // Không có dữ liệu kỹ năng (null) coi như chưa được gán dịch vụ nào → từ chối, không bỏ qua.
    private void checkSupportsServices(ProviderCaregiverResponse caregiver, Collection<UUID> careServiceIds) {
        List<UUID> supportedServiceIds = caregiver.getCareServiceIds() == null ? List.of() : caregiver.getCareServiceIds();
        if (!supportedServiceIds.containsAll(careServiceIds)) {
            throw new AppException(ErrorCode.CAREGIVER_SERVICE_NOT_SUPPORTED);
        }
    }

    // Circuit breaker bọc lỗi ErrorDecoder (vd 404 CAREGIVER_NOT_FOUND) trong exception của nó → lấy lại AppException;
    // lỗi kết nối / timeout → 503, không đoán caregiver hợp lệ.
    private AppException toProviderException(UUID caregiverId, RuntimeException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof AppException appException) {
                return appException;
            }
        }
        log.warn("Provider caregiver lookup failed for {} ({})", caregiverId, exception.getClass().getSimpleName());
        return new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
    }
}
