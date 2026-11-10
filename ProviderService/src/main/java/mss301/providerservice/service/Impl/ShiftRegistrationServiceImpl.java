package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.RegistrationBulkReviewRequest;
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
import mss301.providerservice.repository.CaregiverTimeOffRepository;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.service.ShiftRegistrationService;
import mss301.providerservice.util.ProviderTime;
import mss301.providerservice.util.WorkShiftStates;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    CaregiverTimeOffRepository caregiverTimeOffRepository;
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
        // Không đăng ký ca trùng giờ với ca đã được duyệt, hay rơi vào ngày nghỉ (kiểm lại khi duyệt).
        checkCaregiverFree(caregiver, shift);
        ShiftRegistration registration = shiftRegistrationMapper.toShiftRegistration(request, shift, caregiver);
        registration.setCaregiverNote(normalize(registration.getCaregiverNote()));
        // Đăng ký mới luôn chờ manager duyệt.
        registration.setRegistrationStatus(RegistrationStatus.PENDING);
        ShiftRegistration savedRegistration = shiftRegistrationRepository.save(registration);
        return APIResponse.success("Đăng ký ca làm việc thành công", respond(savedRegistration));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listByShift(UUID actorId, UUID shiftId,
                                                                              RegistrationStatus status,
                                                                              int page, int size) {
        validatePage(page, size);
        WorkShift shift = workShiftRepository.findByIdAndDeletedAtIsNull(shiftId)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        providerAuthorization.requireCenterManagement(shift.getCenter(), actorId);
        Page<ShiftRegistration> registrationPage = shiftRegistrationRepository.findByShift(
                shiftId, status, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));
        List<ShiftRegistrationResponse> items = respond(registrationPage.getContent());
        PageResponse<ShiftRegistrationResponse> pageResponse = PageResponse.of(registrationPage, items);
        return APIResponse.success("Lấy danh sách đăng ký ca thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listMine(UUID actorId, RegistrationStatus status,
                                                                           int page, int size) {
        validatePage(page, size);
        CaregiverProfile caregiver = caregiverService.findActiveCaregiverByUser(actorId);
        Page<ShiftRegistration> registrationPage = shiftRegistrationRepository.findByCaregiver(
                caregiver.getId(), status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<ShiftRegistrationResponse> items = respond(registrationPage.getContent());
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
        checkReviewRequest(request.getStatus(), request.getReason());
        ShiftRegistration savedRegistration = reviewLocked(actorId, registration, shift, request.getVersion(),
                request.getStatus(), request.getReason(), request.getScheduleNote());
        return APIResponse.success("Xử lý đăng ký ca thành công", respond(savedRegistration));
    }

    /**
     * Duyệt / từ chối nhiều đăng ký của một ca, tất cả hoặc không. Khóa đăng ký trước rồi mới khóa ca — cùng thứ tự với
     * review() để hai thao tác song song không khóa chéo nhau.
     */
    @Override
    @Transactional
    public APIResponse<List<ShiftRegistrationResponse>> bulkReview(UUID actorId, UUID shiftId,
                                                                    RegistrationBulkReviewRequest request) {
        checkReviewRequest(request.getStatus(), request.getReason());
        Map<UUID, Long> versions = request.getItems().stream()
                .collect(Collectors.toMap(RegistrationBulkReviewRequest.Item::getRegistrationId,
                        RegistrationBulkReviewRequest.Item::getVersion, (first, second) -> {
                            throw new AppException(ErrorCode.REGISTRATION_BULK_INVALID);
                        }));
        List<ShiftRegistration> registrations = new ArrayList<>();
        for (UUID registrationId : versions.keySet().stream().sorted().toList()) {
            ShiftRegistration registration = shiftRegistrationRepository.findByIdForUpdate(registrationId)
                    .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_NOT_FOUND));
            if (!registration.getWorkShift().getId().equals(shiftId)) {
                throw new AppException(ErrorCode.REGISTRATION_BULK_INVALID);
            }
            registrations.add(registration);
        }
        WorkShift shift = workShiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        providerAuthorization.requireCenterManagement(shift.getCenter(), actorId);
        // Người đăng ký sớm được xử lý trước — quan trọng khi ca sắp đủ người.
        registrations.sort(Comparator.comparing(ShiftRegistration::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<ShiftRegistration> saved = new ArrayList<>();
        for (ShiftRegistration registration : registrations) {
            saved.add(reviewLocked(actorId, registration, shift, versions.get(registration.getId()),
                    request.getStatus(), request.getReason(), null));
        }
        return APIResponse.success("Xử lý " + saved.size() + " đăng ký ca thành công", respond(saved));
    }

    private void checkReviewRequest(RegistrationStatus status, String reason) {
        if (status != RegistrationStatus.APPROVED && status != RegistrationStatus.REJECTED) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (status == RegistrationStatus.REJECTED && isBlank(reason)) {
            throw new AppException(ErrorCode.REGISTRATION_REJECTION_REASON_REQUIRED);
        }
    }

    // Đăng ký và ca đã được caller khóa dòng.
    private ShiftRegistration reviewLocked(UUID actorId, ShiftRegistration registration, WorkShift shift, Long version,
                                           RegistrationStatus status, String reason, String scheduleNote) {
        checkVersion(registration, version);
        if (registration.getRegistrationStatus() != RegistrationStatus.PENDING) {
            throw new AppException(ErrorCode.REGISTRATION_NOT_PENDING);
        }
        if (status == RegistrationStatus.APPROVED) {
            approve(actorId, registration, shift, scheduleNote);
        } else {
            registration.setRegistrationStatus(RegistrationStatus.REJECTED);
            registration.setRejectionReason(reason.trim());
        }
        registration.setReviewedBy(actorId);
        registration.setReviewedAt(clock.instant());
        return shiftRegistrationRepository.save(registration);
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
        checkCaregiverFree(registration.getCaregiver(), shift);
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

    // Trùng giờ với lịch đã duyệt (SCHEDULED) → 4401; giao với ngày nghỉ → 4355.
    private void checkCaregiverFree(CaregiverProfile caregiver, WorkShift shift) {
        boolean overlap = caregiverScheduleRepository.hasOverlap(caregiver.getId(), shift.getWorkDate(),
                shift.getStartTime(), shift.getEndTime(), ACTIVE_SCHEDULES);
        if (overlap) {
            throw new AppException(ErrorCode.SCHEDULE_CONFLICT);
        }
        if (caregiverTimeOffRepository.existsOverlapping(caregiver.getId(), WorkShiftStates.start(shift),
                WorkShiftStates.end(shift))) {
            throw new AppException(ErrorCode.REGISTRATION_TIME_OFF_CONFLICT);
        }
    }

    private ShiftRegistrationResponse respond(ShiftRegistration registration) {
        return respond(List.of(registration)).getFirst();
    }

    // Điền trạng thái hiệu lực của ca và lịch sinh ra khi duyệt (một query cho cả danh sách).
    private List<ShiftRegistrationResponse> respond(List<ShiftRegistration> registrations) {
        Set<UUID> reviewedIds = new HashSet<>();
        for (ShiftRegistration registration : registrations) {
            if (registration.getRegistrationStatus() == RegistrationStatus.APPROVED
                    || registration.getRegistrationStatus() == RegistrationStatus.CANCELLED) {
                reviewedIds.add(registration.getId());
            }
        }
        Map<UUID, CaregiverSchedule> schedules = reviewedIds.isEmpty() ? Map.of()
                : caregiverScheduleRepository.findByRegistrationIdIn(reviewedIds).stream()
                .collect(Collectors.toMap(schedule -> schedule.getRegistration().getId(), Function.identity(),
                        (first, second) -> first));
        Instant now = clock.instant();
        List<ShiftRegistrationResponse> responses = new ArrayList<>();
        for (ShiftRegistration registration : registrations) {
            ShiftRegistrationResponse response = shiftRegistrationMapper.toResponse(registration);
            response.setShiftStatus(WorkShiftStates.effective(registration.getWorkShift(), now));
            CaregiverSchedule schedule = schedules.get(registration.getId());
            if (schedule != null) {
                response.setScheduleId(schedule.getId());
                response.setScheduleStatus(schedule.getScheduleStatus());
                response.setScheduleVersion(schedule.getVersion());
            }
            responses.add(response);
        }
        return responses;
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
