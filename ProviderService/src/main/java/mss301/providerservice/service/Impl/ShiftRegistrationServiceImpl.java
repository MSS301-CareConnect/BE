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
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CaregiverScheduleMapper;
import mss301.providerservice.mapper.ShiftRegistrationMapper;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.ShiftRegistrationService;
import mss301.providerservice.util.ProviderTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ShiftRegistrationServiceImpl implements ShiftRegistrationService {

    static int MAX_PAGE_SIZE = 100;
    static Set<ScheduleStatus> ACTIVE_SCHEDULES = EnumSet.of(ScheduleStatus.SCHEDULED);
    // Khớp unique một phần uk_shift_registrations_shift_caregiver_active (schema/provider.sql).
    static Set<RegistrationStatus> ACTIVE_REGISTRATIONS = EnumSet.of(RegistrationStatus.PENDING, RegistrationStatus.APPROVED);

    WorkShiftRepository workShiftRepository;
    ShiftRegistrationRepository shiftRegistrationRepository;
    CaregiverScheduleRepository caregiverScheduleRepository;
    CaregiverService caregiverService;
    ProviderAuthorization providerAuthorization;
    ShiftRegistrationMapper shiftRegistrationMapper;
    CaregiverScheduleMapper caregiverScheduleMapper;
    Clock clock;

    @Override
    @Transactional
    public APIResponse<ShiftRegistrationResponse> register(UUID actorId, UUID shiftId,
                                                            ShiftRegistrationRequest request) {
        CaregiverProfile caregiver = caregiverService.findActiveCaregiverByUser(actorId);
        if (caregiver.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_VERIFIED);
        }
        if (caregiver.getCaregiverStatus() != CaregiverStatus.ACTIVE) {
            throw new AppException(ErrorCode.CAREGIVER_NOT_ACTIVE);
        }
        WorkShift shift = workShiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        if (!caregiver.getCenter().getId().equals(shift.getCenter().getId())) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
        checkCenterActive(shift);
        if (shift.getShiftStatus() != ShiftStatus.OPEN) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOT_OPEN);
        }
        Instant now = clock.instant();
        // Ca không đặt hạn đăng ký vẫn phải chặn khi đã tới giờ bắt đầu.
        checkShiftNotStarted(shift, now);
        if (shift.getRegistrationDeadline() != null && !now.isBefore(shift.getRegistrationDeadline())) {
            throw new AppException(ErrorCode.REGISTRATION_DEADLINE_PASSED);
        }
        if (shiftRegistrationRepository.existsByWorkShiftIdAndCaregiverIdAndRegistrationStatusIn(
                shiftId, caregiver.getId(), ACTIVE_REGISTRATIONS)) {
            throw new AppException(ErrorCode.REGISTRATION_EXISTED);
        }
        if (countApproved(shiftId) >= shift.getCapacity()) {
            throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        }
        ShiftRegistration registration = shiftRegistrationMapper.toShiftRegistration(request, shift, caregiver);
        registration.setCaregiverNote(normalize(registration.getCaregiverNote()));
        // Đăng ký mới luôn chờ manager duyệt.
        registration.setRegistrationStatus(RegistrationStatus.PENDING);
        ShiftRegistration savedRegistration = shiftRegistrationRepository.save(registration);
        ShiftRegistrationResponse shiftRegistrationResponse = shiftRegistrationMapper.toResponse(savedRegistration);
        return APIResponse.success("Đăng ký ca làm việc thành công", shiftRegistrationResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listByShift(UUID actorId, UUID shiftId,
                                                                              int page, int size) {
        validatePage(page, size);
        WorkShift shift = workShiftRepository.findByIdAndDeletedAtIsNull(shiftId)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        providerAuthorization.requireCenterManagement(shift.getCenter(), actorId);
        Page<ShiftRegistration> registrationPage = shiftRegistrationRepository.findByWorkShiftIdAndDeletedAtIsNull(
                shiftId, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));
        List<ShiftRegistrationResponse> items = shiftRegistrationMapper.toResponseList(registrationPage.getContent());
        PageResponse<ShiftRegistrationResponse> pageResponse = PageResponse.of(registrationPage, items);
        return APIResponse.success("Lấy danh sách đăng ký ca thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listMine(UUID actorId, int page, int size) {
        validatePage(page, size);
        CaregiverProfile caregiver = caregiverService.findActiveCaregiverByUser(actorId);
        Page<ShiftRegistration> registrationPage = shiftRegistrationRepository.findByCaregiverIdAndDeletedAtIsNull(
                caregiver.getId(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ShiftRegistrationResponse> items = shiftRegistrationMapper.toResponseList(registrationPage.getContent());
        PageResponse<ShiftRegistrationResponse> pageResponse = PageResponse.of(registrationPage, items);
        return APIResponse.success("Lấy đăng ký ca của tôi thành công", pageResponse);
    }

    @Override
    @Transactional
    public APIResponse<ShiftRegistrationResponse> review(UUID actorId, UUID registrationId,
                                                          RegistrationReviewRequest request) {
        ShiftRegistration registration = shiftRegistrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
        WorkShift shift = workShiftRepository.findByIdForUpdate(registration.getWorkShift().getId())
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        providerAuthorization.requireCenterManagement(shift.getCenter(), actorId);
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
        registration.setReviewedAt(clock.instant());
        ShiftRegistration savedRegistration = shiftRegistrationRepository.save(registration);
        ShiftRegistrationResponse shiftRegistrationResponse = shiftRegistrationMapper.toResponse(savedRegistration);
        return APIResponse.success("Xử lý đăng ký ca thành công", shiftRegistrationResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> cancel(UUID actorId, UUID registrationId, Long version) {
        CaregiverProfile caregiver = caregiverService.findActiveCaregiverByUser(actorId);
        ShiftRegistration registration = shiftRegistrationRepository.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
        if (!caregiver.getId().equals(registration.getCaregiver().getId())) {
            throw new AppException(ErrorCode.CAREGIVER_ACCESS_DENIED);
        }
        checkVersion(registration, version);
        if (registration.getRegistrationStatus() != RegistrationStatus.PENDING) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_PENDING);
        }
        registration.setRegistrationStatus(RegistrationStatus.CANCELLED);
        shiftRegistrationRepository.save(registration);
        return APIResponse.success("Hủy đăng ký ca thành công");
    }

    private void approve(UUID actorId, ShiftRegistration registration, WorkShift shift, String note) {
        checkCenterActive(shift);
        if (shift.getShiftStatus() != ShiftStatus.OPEN && shift.getShiftStatus() != ShiftStatus.CLOSED) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOT_OPEN);
        }
        checkShiftNotStarted(shift, clock.instant());
        // Đếm TRƯỚC khi đổi trạng thái: count sau đó sẽ auto-flush chính đăng ký này → cộng thêm 1 là đếm trùng.
        long approvedBefore = countApproved(shift.getId());
        if (approvedBefore >= shift.getCapacity()) {
            throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        }
        CaregiverProfile caregiver = registration.getCaregiver();
        boolean overlap = caregiverScheduleRepository.hasOverlap(caregiver.getId(), shift.getWorkDate(),
                shift.getStartTime(), shift.getEndTime(), ACTIVE_SCHEDULES);
        if (overlap) {
            throw new AppException(ErrorCode.SCHEDULE_CONFLICT);
        }
        registration.setRegistrationStatus(RegistrationStatus.APPROVED);
        CaregiverSchedule schedule = caregiverScheduleMapper.toCaregiverSchedule(registration, shift, actorId,
                normalize(note));
        schedule.setScheduleStatus(ScheduleStatus.SCHEDULED);
        schedule.setAssignedAt(clock.instant());
        caregiverScheduleRepository.save(schedule);
        if (approvedBefore + 1 >= shift.getCapacity()) {
            shift.setShiftStatus(ShiftStatus.CLOSED);
            workShiftRepository.save(shift);
        }
    }

    private void checkCenterActive(WorkShift shift) {
        if (shift.getCenter().getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
    }

    private void checkShiftNotStarted(WorkShift shift, Instant now) {
        Instant shiftStart = ProviderTime.toInstant(shift.getWorkDate(), shift.getStartTime());
        if (!now.isBefore(shiftStart)) {
            throw new AppException(ErrorCode.WORK_SHIFT_ALREADY_STARTED);
        }
    }

    private long countApproved(UUID shiftId) {
        return shiftRegistrationRepository.countByWorkShiftIdAndRegistrationStatus(shiftId, RegistrationStatus.APPROVED);
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
