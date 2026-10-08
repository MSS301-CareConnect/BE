package mss301.providerservice.service;

import lombok.RequiredArgsConstructor;
import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.response.*;
import mss301.providerservice.entity.*;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.exception.*;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.security.ProviderAuthorization;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaregiverScheduleService {
    private static final int MAX_PAGE_SIZE = 100;
    private final CaregiverScheduleRepository scheduleRepository;
    private final CareCenterRepository centerRepository;
    private final CaregiverService caregiverService;
    private final ProviderAuthorization authorization;
    private final ProviderViewMapper mapper;

    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ScheduleResponse>> mySchedules(UUID actorId, int page, int size) {
        validatePage(page, size);
        CaregiverProfile caregiver = caregiverService.findByUser(actorId);
        Page<CaregiverSchedule> result = scheduleRepository.findByCaregiverIdAndDeletedAtIsNull(
                caregiver.getId(), PageRequest.of(page, size, Sort.by("workShift.workDate").ascending()));
        return APIResponse.success("Lấy lịch làm việc của tôi thành công",
                PageResponse.of(result, result.stream().map(mapper::schedule).toList()));
    }

    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ScheduleResponse>> centerSchedules(UUID actorId, UUID centerId,
                                                                        int page, int size) {
        validatePage(page, size);
        CareCenter center = centerRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        authorization.requireCenterManagement(center, actorId);
        Page<CaregiverSchedule> result = scheduleRepository.findByWorkShiftCenterIdAndDeletedAtIsNull(
                centerId, PageRequest.of(page, size, Sort.by("workShift.workDate").ascending()));
        return APIResponse.success("Lấy lịch người chăm sóc của trung tâm thành công",
                PageResponse.of(result, result.stream().map(mapper::schedule).toList()));
    }

    @Transactional
    public APIResponse<ScheduleResponse> updateStatus(UUID actorId, UUID id, ScheduleStatusRequest request) {
        CaregiverSchedule schedule = scheduleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_FOUND));
        authorization.requireCaregiverSelfOrCenterManagement(schedule.getCaregiver(), actorId);
        if (request.getVersion() == null || !request.getVersion().equals(schedule.getVersion())) {
            throw new AppException(ErrorCode.SCHEDULE_VERSION_CONFLICT);
        }
        if (!allowedTransition(schedule.getScheduleStatus(), request.getStatus())) {
            throw new AppException(ErrorCode.SCHEDULE_STATUS_INVALID);
        }
        if (request.getStatus() == ScheduleStatus.CANCELLED
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new AppException(ErrorCode.SCHEDULE_CANCELLATION_REASON_REQUIRED);
        }
        schedule.setScheduleStatus(request.getStatus());
        if (request.getStatus() == ScheduleStatus.CANCELLED) {
            schedule.setCancellationReason(request.getReason().trim());
            schedule.setCancelledAt(Instant.now());
            schedule.getRegistration().setRegistrationStatus(RegistrationStatus.CANCELLED);
            reopenShiftWhenPossible(schedule.getWorkShift());
        }
        return APIResponse.success("Cập nhật trạng thái lịch thành công",
                mapper.schedule(scheduleRepository.save(schedule)));
    }

    private void reopenShiftWhenPossible(WorkShift shift) {
        Instant now = Instant.now();
        boolean shiftInFuture = LocalDateTime.of(shift.getWorkDate(), shift.getStartTime())
                .toInstant(ZoneOffset.UTC).isAfter(now);
        boolean registrationStillAllowed = shift.getRegistrationDeadline() == null
                || now.isBefore(shift.getRegistrationDeadline());
        if (shift.getShiftStatus() == ShiftStatus.CLOSED && shiftInFuture && registrationStillAllowed) {
            shift.setShiftStatus(ShiftStatus.OPEN);
        }
    }

    private boolean allowedTransition(ScheduleStatus from, ScheduleStatus to) {
        return (from == ScheduleStatus.SCHEDULED
                && (to == ScheduleStatus.IN_PROGRESS || to == ScheduleStatus.CANCELLED))
                || (from == ScheduleStatus.IN_PROGRESS
                && (to == ScheduleStatus.COMPLETED || to == ScheduleStatus.CANCELLED));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw new AppException(ErrorCode.INVALID_PAGE);
    }
}
