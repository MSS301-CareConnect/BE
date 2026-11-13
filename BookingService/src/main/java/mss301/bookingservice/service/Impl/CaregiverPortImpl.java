package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.ProviderServiceClient;
import mss301.bookingservice.client.dto.AvailabilityCheckResponse;
import mss301.bookingservice.client.dto.ProviderCaregiverCardPageResponse;
import mss301.bookingservice.client.dto.ProviderCaregiverCardResponse;
import mss301.bookingservice.client.dto.ProviderCaregiverResponse;
import mss301.bookingservice.client.dto.ProviderScheduleResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.service.CaregiverPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverPortImpl implements CaregiverPort {

    static int MAX_SLOTS_PER_CHECK = 60;
    // Trang lớn nhất Provider cho phép ở /caregivers/search.
    static int MAX_SEARCH_RESULTS = 50;
    static String SLOT_SEPARATOR = "/";

    ProviderServiceClient providerServiceClient;
    OccurrenceMapper occurrenceMapper;

    // Kiểm caregiver thuộc trung tâm, đang hoạt động và có đủ kỹ năng dịch vụ.
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

    // Kiểm caregiver rảnh mọi khung giờ (hỏi ProviderService theo lô).
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

    // Lấy ca làm việc caregiver đã nhận để đặt booking theo ca.
    @Override
    public ProviderScheduleResponse findBookableSchedule(UUID centerId, UUID caregiverId, UUID scheduleId) {
        RemoteApiResponse<ProviderScheduleResponse> response;
        try {
            response = providerServiceClient.getSchedule(scheduleId);
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            throw toScheduleException(scheduleId, e);
        }
        ProviderScheduleResponse schedule = response == null ? null : response.getResult();
        if (schedule == null || schedule.getWorkDate() == null || schedule.getStartTime() == null
                || schedule.getEndTime() == null) {
            throw new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
        }
        if (!centerId.equals(schedule.getCenterId()) || !caregiverId.equals(schedule.getCaregiverId())) {
            throw new AppException(ErrorCode.CAREGIVER_SCHEDULE_MISMATCH);
        }
        if (!ProviderScheduleResponse.STATUS_SCHEDULED.equals(schedule.getScheduleStatus())) {
            throw new AppException(ErrorCode.CAREGIVER_SCHEDULE_NOT_AVAILABLE);
        }
        return schedule;
    }

    // Tìm caregiver của trung tâm có kỹ năng và rảnh mọi khung giờ.
    @Override
    public List<ProviderCaregiverCardResponse> findFreeCaregivers(UUID centerId, Collection<UUID> careServiceIds,
                                                                  List<OccurrenceSlot> slots) {
        // Provider nhận tối đa 60 khung / lần → booking dài hơn: giao các tập ứng viên của từng phần.
        Map<UUID, ProviderCaregiverCardResponse> candidates = null;
        for (int from = 0; from < slots.size(); from += MAX_SLOTS_PER_CHECK) {
            List<String> chunk = slots.subList(from, Math.min(from + MAX_SLOTS_PER_CHECK, slots.size())).stream()
                    .map(slot -> slot.getStartAt() + SLOT_SEPARATOR + slot.getEndAt())
                    .toList();
            Map<UUID, ProviderCaregiverCardResponse> found = new LinkedHashMap<>();
            for (ProviderCaregiverCardResponse card : requestCandidates(centerId, careServiceIds, chunk)) {
                found.put(card.getCaregiverId(), card);
            }
            if (candidates == null) {
                candidates = found;
            } else {
                candidates.keySet().retainAll(found.keySet());
            }
            if (candidates.isEmpty()) {
                break;
            }
        }
        return candidates == null ? List.of() : new ArrayList<>(candidates.values());
    }

    // Ứng viên đủ điều kiện tĩnh (không gửi khung giờ thì Provider bỏ qua lọc lịch).
    @Override
    public List<ProviderCaregiverCardResponse> findQualifiedCaregivers(UUID centerId, Collection<UUID> careServiceIds) {
        return requestCandidates(centerId, careServiceIds, List.of());
    }

    // Buổi vi phạm lịch làm việc / ngày nghỉ theo từng lô 60 buổi.
    @Override
    public Set<Instant> findUnavailableStarts(UUID caregiverId, List<OccurrenceSlot> slots) {
        Set<Instant> unavailable = new HashSet<>();
        for (int from = 0; from < slots.size(); from += MAX_SLOTS_PER_CHECK) {
            AvailabilityCheckResponse availability = requestAvailability(caregiverId,
                    slots.subList(from, Math.min(from + MAX_SLOTS_PER_CHECK, slots.size())));
            if (availability.getViolations() != null) {
                availability.getViolations().forEach(violation -> unavailable.add(violation.getStartAt()));
            }
        }
        return unavailable;
    }

    // Gọi ProviderService tìm caregiver phù hợp cho một lô khung giờ.
    private List<ProviderCaregiverCardResponse> requestCandidates(UUID centerId, Collection<UUID> careServiceIds,
                                                                  List<String> slots) {
        RemoteApiResponse<ProviderCaregiverCardPageResponse> response;
        try {
            response = providerServiceClient.searchCaregivers(centerId, List.copyOf(careServiceIds), slots,
                    MAX_SEARCH_RESULTS);
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Provider caregiver search failed for center {} ({})", centerId, e.getClass().getSimpleName());
            throw new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
        }
        if (response == null || response.getResult() == null) {
            throw new AppException(ErrorCode.PROVIDER_UNAVAILABLE);
        }
        return response.getResult().getItems() == null ? List.of() : response.getResult().getItems();
    }

    // Gọi ProviderService kiểm tra caregiver rảnh các khung giờ.
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

    // Provider trả 404 cho lịch không tồn tại → CAREGIVER_SCHEDULE_NOT_FOUND (ErrorDecoder chỉ biết mã chung của caregiver).
    private AppException toScheduleException(UUID scheduleId, RuntimeException exception) {
        AppException appException = toProviderException(scheduleId, exception);
        if (appException.getErrorCode() == ErrorCode.CAREGIVER_NOT_FOUND) {
            return new AppException(ErrorCode.CAREGIVER_SCHEDULE_NOT_FOUND);
        }
        return appException;
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
