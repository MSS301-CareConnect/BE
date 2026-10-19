package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.WorkShiftService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WorkShiftServiceImpl implements WorkShiftService {

    static int MAX_PAGE_SIZE = 100;

    CareCenterRepository centerRepository;
    WorkShiftRepository shiftRepository;
    ShiftRegistrationRepository registrationRepository;
    ProviderAuthorization authorization;
    ProviderViewMapper mapper;

    @Override
    @Transactional
    public APIResponse<WorkShiftResponse> create(UUID actorId, UUID centerId, WorkShiftRequest request) {
        CareCenter center = centerRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        authorization.requireCenterManagement(center, actorId);
        if (center.getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
        validate(request, false);
        WorkShift shift = WorkShift.builder()
                .center(center)
                .workDate(request.getWorkDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .shiftType(request.getShiftType().trim().toUpperCase(Locale.ROOT))
                .shiftStatus(request.getShiftStatus())
                .capacity(request.getCapacity())
                .registrationDeadline(request.getRegistrationDeadline())
                .note(normalize(request.getNote()))
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
        WorkShift saved = shiftRepository.save(shift);
        return APIResponse.success("Tạo ca làm việc thành công", mapper.shift(saved, 0));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<WorkShiftResponse>> list(UUID centerId, ShiftStatus status,
                                                             LocalDate fromDate, LocalDate toDate,
                                                             int page, int size) {
        validatePage(page, size);
        Page<WorkShift> result = shiftRepository.search(centerId, status, fromDate, toDate,
                PageRequest.of(page, size, Sort.by("workDate").ascending().and(Sort.by("startTime").ascending())));
        List<WorkShiftResponse> items = result.stream().map(this::toResponse).toList();
        return APIResponse.success("Lấy danh sách ca làm việc thành công", PageResponse.of(result, items));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WorkShiftResponse> get(UUID id) {
        return APIResponse.success("Lấy ca làm việc thành công", toResponse(find(id)));
    }

    @Override
    @Transactional
    public APIResponse<WorkShiftResponse> update(UUID actorId, UUID id, WorkShiftRequest request) {
        WorkShift shift = find(id);
        authorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(shift, request.getVersion());
        long approved = approvedCount(id);
        validateStatusTransition(shift, request.getShiftStatus(), approved);
        if (approved > 0 && scheduleFieldsChanged(shift, request)) {
            throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
        }
        validate(request, true);
        if (request.getCapacity() < approved) {
            throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        }
        shift.setWorkDate(request.getWorkDate());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setShiftType(request.getShiftType().trim().toUpperCase(Locale.ROOT));
        shift.setShiftStatus(request.getShiftStatus());
        shift.setCapacity(request.getCapacity());
        shift.setRegistrationDeadline(request.getRegistrationDeadline());
        shift.setNote(normalize(request.getNote()));
        shift.setUpdatedBy(actorId);
        WorkShift saved = shiftRepository.save(shift);
        return APIResponse.success("Cập nhật ca làm việc thành công", mapper.shift(saved, approved));
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        WorkShift shift = find(id);
        authorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(shift, version);
        if (approvedCount(id) > 0) {
            throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
        }
        shift.setShiftStatus(ShiftStatus.CANCELLED);
        shift.setDeletedAt(Instant.now());
        shift.setUpdatedBy(actorId);
        shiftRepository.save(shift);
        return APIResponse.success("Hủy ca làm việc thành công");
    }

    @Override
    public WorkShift find(UUID id) {
        return shiftRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
    }

    @Override
    public long approvedCount(UUID shiftId) {
        return registrationRepository.countByWorkShiftIdAndRegistrationStatus(shiftId, RegistrationStatus.APPROVED);
    }

    private WorkShiftResponse toResponse(WorkShift shift) {
        return mapper.shift(shift, approvedCount(shift.getId()));
    }

    private void validate(WorkShiftRequest request, boolean update) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new AppException(ErrorCode.WORK_SHIFT_TIME_INVALID);
        }
        LocalDateTime shiftStart = LocalDateTime.of(request.getWorkDate(), request.getStartTime());
        if (!update && !shiftStart.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new AppException(ErrorCode.WORK_SHIFT_DATE_INVALID);
        }
        if (request.getRegistrationDeadline() != null
                && !request.getRegistrationDeadline().isBefore(shiftStart.toInstant(ZoneOffset.UTC))) {
            throw new AppException(ErrorCode.WORK_SHIFT_DATE_INVALID);
        }
        if (!update && request.getShiftStatus() != ShiftStatus.DRAFT && request.getShiftStatus() != ShiftStatus.OPEN) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }

    private boolean scheduleFieldsChanged(WorkShift shift, WorkShiftRequest request) {
        return !shift.getWorkDate().equals(request.getWorkDate())
                || !shift.getStartTime().equals(request.getStartTime())
                || !shift.getEndTime().equals(request.getEndTime());
    }

    private void validateStatusTransition(WorkShift shift, ShiftStatus target, long approved) {
        ShiftStatus current = shift.getShiftStatus();
        if ((current == ShiftStatus.CANCELLED || current == ShiftStatus.COMPLETED) && target != current) {
            throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
        }
        if (target == ShiftStatus.CANCELLED && approved > 0) {
            throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
        }
        if (target == ShiftStatus.COMPLETED) {
            Instant end = LocalDateTime.of(shift.getWorkDate(), shift.getEndTime()).toInstant(ZoneOffset.UTC);
            if (end.isAfter(Instant.now())) {
                throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
            }
        }
    }

    private void checkVersion(WorkShift value, Long version) {
        if (version == null || !version.equals(value.getVersion())) {
            throw new AppException(ErrorCode.WORK_SHIFT_VERSION_CONFLICT);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
