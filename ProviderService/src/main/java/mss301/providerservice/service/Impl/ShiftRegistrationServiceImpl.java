package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.ShiftRegistrationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ShiftRegistrationServiceImpl implements ShiftRegistrationService {

    static int MAX_PAGE_SIZE = 100;
    static Set<ScheduleStatus> ACTIVE_SCHEDULES = EnumSet.of(ScheduleStatus.SCHEDULED, ScheduleStatus.IN_PROGRESS);

    WorkShiftRepository shiftRepository;
    ShiftRegistrationRepository registrationRepository;
    CaregiverScheduleRepository scheduleRepository;
    CaregiverService caregiverService;
    ProviderAuthorization authorization;
    ProviderViewMapper mapper;

    @Override
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
        if (shift.getShiftStatus() != ShiftStatus.OPEN) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOT_OPEN);
        }
        if (shift.getRegistrationDeadline() != null && !Instant.now().isBefore(shift.getRegistrationDeadline())) {
            throw new AppException(ErrorCode.REGISTRATION_DEADLINE_PASSED);
        }
        if (registrationRepository.existsByWorkShiftIdAndCaregiverId(shiftId, caregiver.getId())) {
            throw new AppException(ErrorCode.REGISTRATION_EXISTED);
        }
        if (approvedCount(shiftId) >= shift.getCapacity()) {
            throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        }
        ShiftRegistration registration = ShiftRegistration.builder()
                .workShift(shift)
                .caregiver(caregiver)
                .registrationStatus(RegistrationStatus.PENDING)
                .caregiverNote(normalize(request.getCaregiverNote()))
                .build();
        return APIResponse.success("Đăng ký ca làm việc thành công",
                mapper.registration(registrationRepository.save(registration)));
    }

    @Override
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

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ShiftRegistrationResponse>> myRegistrations(UUID actorId, int page, int size) {
        validatePage(page, size);
        CaregiverProfile caregiver = caregiverService.findByUser(actorId);
        Page<ShiftRegistration> result = registrationRepository.findByCaregiverIdAndDeletedAtIsNull(
                caregiver.getId(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return APIResponse.success("Lấy đăng ký ca của tôi thành công",
                PageResponse.of(result, result.stream().map(mapper::registration).toList()));
    }

    @Override
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

    @Override
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
        if (approvedCount(shift.getId()) >= shift.getCapacity()) {
            throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        }
        CaregiverProfile caregiver = registration.getCaregiver();
        boolean overlap = scheduleRepository.hasOverlap(caregiver.getId(), shift.getWorkDate(),
                shift.getStartTime(), shift.getEndTime(), ACTIVE_SCHEDULES);
        if (overlap) {
            throw new AppException(ErrorCode.SCHEDULE_CONFLICT);
        }
        registration.setRegistrationStatus(RegistrationStatus.APPROVED);
        CaregiverSchedule schedule = CaregiverSchedule.builder()
                .registration(registration)
                .caregiver(caregiver)
                .workShift(shift)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .assignedBy(actorId)
                .assignedAt(Instant.now())
                .note(normalize(note))
                .build();
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
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
