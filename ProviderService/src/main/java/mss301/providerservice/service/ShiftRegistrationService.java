package mss301.providerservice.service;

import lombok.RequiredArgsConstructor;
import mss301.providerservice.dto.request.*;
import mss301.providerservice.dto.response.*;
import mss301.providerservice.entity.*;
import mss301.providerservice.enums.*;
import mss301.providerservice.exception.*;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.*;
import mss301.providerservice.security.ProviderAuthorization;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ShiftRegistrationService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<ScheduleStatus> ACTIVE_SCHEDULES =
            EnumSet.of(ScheduleStatus.SCHEDULED, ScheduleStatus.IN_PROGRESS);
    private final WorkShiftRepository shiftRepository;
    private final ShiftRegistrationRepository registrationRepository;
    private final CaregiverScheduleRepository scheduleRepository;
    private final CaregiverService caregiverService;
    private final ProviderAuthorization authorization;
    private final ProviderViewMapper mapper;

    @Transactional
    public APIResponse<ShiftRegistrationResponse> register(UUID actorId, UUID shiftId,
                                                            ShiftRegistrationRequest request) {
        CaregiverProfile caregiver = caregiverService.findByUser(actorId);
        if (caregiver.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_VERIFIED);
        }
        if (caregiver.getCaregiverStatus() != CaregiverStatus.ACTIVE) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_ACTIVE);
        }
        WorkShift shift = shiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        if (!caregiver.getCenter().getId().equals(shift.getCenter().getId())) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
        if (shift.getShiftStatus() != ShiftStatus.OPEN) throw new AppException(ErrorCode.WORK_SHIFT_NOT_OPEN);
        if (shift.getRegistrationDeadline() != null && !Instant.now().isBefore(shift.getRegistrationDeadline())) {
            throw new AppException(ErrorCode.REGISTRATION_DEADLINE_PASSED);
        }
        if (registrationRepository.existsByWorkShiftIdAndCaregiverId(shiftId, caregiver.getId())) {
            throw new AppException(ErrorCode.REGISTRATION_EXISTED);
        }
        if (approvedCount(shiftId) >= shift.getCapacity()) throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        ShiftRegistration registration = ShiftRegistration.builder()
                .workShift(shift).caregiver(caregiver).registrationStatus(RegistrationStatus.PENDING)
                .caregiverNote(normalize(request.getCaregiverNote())).build();
        return APIResponse.success("Đăng ký ca làm việc thành công",
                mapper.registration(registrationRepository.save(registration)));
    }

    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listForShift(UUID actorId, UUID shiftId,
                                                                              int page, int size) {
        validatePage(page, size);
        WorkShift shift = shiftRepository.findByIdAndDeletedAtIsNull(shiftId)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        authorization.requireCenterManagement(shift.getCenter(), actorId);
        Page<ShiftRegistration> result = registrationRepository.findByWorkShiftIdAndDeletedAtIsNull(
                shiftId, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));
        return APIResponse.success("Lấy danh sách đăng ký ca thành công",
                PageResponse.of(result, result.stream().map(mapper::registration).toList()));
    }

    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ShiftRegistrationResponse>> myRegistrations(UUID actorId, int page, int size) {
        validatePage(page, size);
        CaregiverProfile caregiver = caregiverService.findByUser(actorId);
        Page<ShiftRegistration> result = registrationRepository.findByCaregiverIdAndDeletedAtIsNull(
                caregiver.getId(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return APIResponse.success("Lấy đăng ký ca của tôi thành công",
                PageResponse.of(result, result.stream().map(mapper::registration).toList()));
    }

    @Transactional
    public APIResponse<ShiftRegistrationResponse> review(UUID actorId, UUID registrationId,
                                                          RegistrationReviewRequest request) {
        ShiftRegistration registration = registrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
        WorkShift shift = shiftRepository.findByIdForUpdate(registration.getWorkShift().getId())
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        authorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(registration, request.getVersion());
        if (registration.getRegistrationStatus() != RegistrationStatus.PENDING) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_PENDING);
        }
        if (request.getStatus() != RegistrationStatus.APPROVED
                && request.getStatus() != RegistrationStatus.REJECTED) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getStatus() == RegistrationStatus.REJECTED && isBlank(request.getReason())) {
            throw new AppException(ErrorCode.REGISTRATION_REJECTION_REASON_REQUIRED);
        }
        if (request.getStatus() == RegistrationStatus.APPROVED) {
            approve(actorId, registration, shift, request.getScheduleNote());
        } else {
            registration.setRegistrationStatus(RegistrationStatus.REJECTED);
            registration.setRejectionReason(request.getReason().trim());
        }
        registration.setReviewedBy(actorId);
        registration.setReviewedAt(Instant.now());
        return APIResponse.success("Xử lý đăng ký ca thành công",
                mapper.registration(registrationRepository.save(registration)));
    }

    @Transactional
    public APIResponse<Void> cancelOwn(UUID actorId, UUID registrationId, Long version) {
        CaregiverProfile caregiver = caregiverService.findByUser(actorId);
        ShiftRegistration registration = registrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
        if (!caregiver.getId().equals(registration.getCaregiver().getId())) {
            throw new AppException(ErrorCode.CAREGIVER_ACCESS_DENIED);
        }
        checkVersion(registration, version);
        if (registration.getRegistrationStatus() != RegistrationStatus.PENDING) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_PENDING);
        }
        registration.setRegistrationStatus(RegistrationStatus.CANCELLED);
        registrationRepository.save(registration);
        return APIResponse.success("Hủy đăng ký ca thành công");
    }

    private void approve(UUID actorId, ShiftRegistration registration, WorkShift shift, String note) {
        if (shift.getShiftStatus() != ShiftStatus.OPEN && shift.getShiftStatus() != ShiftStatus.CLOSED) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOT_OPEN);
        }
        if (approvedCount(shift.getId()) >= shift.getCapacity()) throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        CaregiverProfile caregiver = registration.getCaregiver();
        boolean overlap = scheduleRepository.hasOverlap(caregiver.getId(), shift.getWorkDate(),
                shift.getStartTime(), shift.getEndTime(), ACTIVE_SCHEDULES);
        if (overlap) throw new AppException(ErrorCode.SCHEDULE_CONFLICT);
        registration.setRegistrationStatus(RegistrationStatus.APPROVED);
        CaregiverSchedule schedule = CaregiverSchedule.builder()
                .registration(registration).caregiver(caregiver).workShift(shift)
                .scheduleStatus(ScheduleStatus.SCHEDULED).assignedBy(actorId)
                .assignedAt(Instant.now()).note(normalize(note)).build();
        scheduleRepository.save(schedule);
        if (approvedCount(shift.getId()) + 1 >= shift.getCapacity()) {
            shift.setShiftStatus(ShiftStatus.CLOSED);
            shiftRepository.save(shift);
        }
    }

    private long approvedCount(UUID shiftId) {
        return registrationRepository.countByWorkShiftIdAndRegistrationStatus(shiftId, RegistrationStatus.APPROVED);
    }
    private void checkVersion(ShiftRegistration value, Long version) {
        if (version == null || !version.equals(value.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }
    }
    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw new AppException(ErrorCode.INVALID_PAGE);
    }
    private String normalize(String value) { return isBlank(value) ? null : value.trim(); }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }
}
