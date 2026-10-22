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
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.RoleCode;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.WorkShiftMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.repository.ShiftApprovedCount;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.WorkShiftService;
import mss301.providerservice.util.ProviderTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WorkShiftServiceImpl implements WorkShiftService {

    static int MAX_PAGE_SIZE = 100;

    CareCenterRepository careCenterRepository;
    WorkShiftRepository workShiftRepository;
    ShiftRegistrationRepository shiftRegistrationRepository;
    CaregiverProfileRepository caregiverProfileRepository;
    ProviderAuthorization providerAuthorization;
    WorkShiftMapper workShiftMapper;
    Clock clock;

    @Override
    @Transactional
    public APIResponse<WorkShiftResponse> create(UUID actorId, UUID centerId, WorkShiftRequest request) {
        CareCenter center = careCenterRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        providerAuthorization.requireCenterManagement(center, actorId);
        if (center.getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
        validateShiftRequest(request, false);
        WorkShift shift = workShiftMapper.toWorkShift(request, center);
        normalizeFields(shift);
        shift.setCreatedBy(actorId);
        shift.setUpdatedBy(actorId);
        WorkShift savedShift = workShiftRepository.save(shift);
        // Ca vừa tạo chưa có đăng ký nào được duyệt.
        WorkShiftResponse workShiftResponse = workShiftMapper.toResponse(savedShift, 0);
        return APIResponse.success("Tạo ca làm việc thành công", workShiftResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<WorkShiftResponse>> list(UUID actorId, UUID centerId, ShiftStatus status,
                                                             LocalDate fromDate, LocalDate toDate,
                                                             int page, int size) {
        validatePage(page, size);
        UUID scopedCenterId = resolveListCenterId(actorId, centerId);
        // Ca DRAFT là bản nháp của trung tâm — caregiver chỉ thấy ca đã công bố.
        ShiftStatus excludedStatus = isCaregiverOnly() ? ShiftStatus.DRAFT : null;
        Sort sort = Sort.by("workDate").ascending().and(Sort.by("startTime").ascending());
        Page<WorkShift> shiftPage = workShiftRepository.search(scopedCenterId, status, excludedStatus, fromDate, toDate,
                PageRequest.of(page, size, sort));
        Map<UUID, Long> approvedCounts = countApprovedByShift(shiftPage.getContent());
        List<WorkShiftResponse> items = shiftPage.stream()
                .map(shift -> workShiftMapper.toResponse(shift, approvedCounts.getOrDefault(shift.getId(), 0L)))
                .toList();
        PageResponse<WorkShiftResponse> pageResponse = PageResponse.of(shiftPage, items);
        return APIResponse.success("Lấy danh sách ca làm việc thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WorkShiftResponse> get(UUID actorId, UUID id) {
        WorkShift shift = findActiveShift(id);
        checkReadScope(actorId, shift);
        WorkShiftResponse workShiftResponse = mapWithApprovedCount(shift);
        return APIResponse.success("Lấy ca làm việc thành công", workShiftResponse);
    }

    @Override
    @Transactional
    public APIResponse<WorkShiftResponse> update(UUID actorId, UUID id, WorkShiftRequest request) {
        WorkShift shift = findActiveShift(id);
        providerAuthorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(shift, request.getVersion());
        long approved = countApproved(id);
        validateStatusTransition(shift, request.getShiftStatus(), approved);
        if (approved > 0 && hasScheduleFieldsChanged(shift, request)) {
            throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
        }
        validateShiftRequest(request, true);
        if (request.getCapacity() < approved) {
            throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        }
        workShiftMapper.update(request, shift);
        normalizeFields(shift);
        shift.setUpdatedBy(actorId);
        WorkShift savedShift = workShiftRepository.save(shift);
        WorkShiftResponse workShiftResponse = workShiftMapper.toResponse(savedShift, approved);
        return APIResponse.success("Cập nhật ca làm việc thành công", workShiftResponse);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        WorkShift shift = findActiveShift(id);
        providerAuthorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(shift, version);
        if (countApproved(id) > 0) {
            throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
        }
        shift.setShiftStatus(ShiftStatus.CANCELLED);
        shift.setDeletedAt(clock.instant());
        shift.setUpdatedBy(actorId);
        workShiftRepository.save(shift);
        return APIResponse.success("Hủy ca làm việc thành công");
    }

    @Override
    public WorkShift findActiveShift(UUID id) {
        return workShiftRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
    }

    @Override
    public long countApproved(UUID shiftId) {
        return shiftRegistrationRepository.countByWorkShiftIdAndRegistrationStatus(
                shiftId, RegistrationStatus.APPROVED);
    }

    private WorkShiftResponse mapWithApprovedCount(WorkShift shift) {
        return workShiftMapper.toResponse(shift, countApproved(shift.getId()));
    }

    /**
     * Phạm vi GET /work-shifts theo role (SEC-08): ADMIN mọi trung tâm (lọc theo centerId nếu có); manager chỉ trung tâm
     * mình quản lý; caregiver chỉ trung tâm của mình. Gửi centerId khác phạm vi → 403, không âm thầm đổi bộ lọc.
     */
    private UUID resolveListCenterId(UUID actorId, UUID requestedCenterId) {
        if (providerAuthorization.isAdmin()) {
            return requestedCenterId;
        }
        UUID ownCenterId;
        if (providerAuthorization.hasRole(RoleCode.CENTER_MANAGER)) {
            CareCenter center = careCenterRepository.findByManagerUserIdAndDeletedAtIsNull(actorId)
                    .orElseThrow(() -> new AppException(ErrorCode.CENTER_ACCESS_DENIED));
            ownCenterId = center.getId();
        } else if (providerAuthorization.hasRole(RoleCode.CAREGIVER)) {
            CaregiverProfile caregiver = caregiverProfileRepository.findByUserIdAndDeletedAtIsNull(actorId)
                    .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
            ownCenterId = caregiver.getCenter().getId();
        } else {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (requestedCenterId != null && !requestedCenterId.equals(ownCenterId)) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
        return ownCenterId;
    }

    /**
     * Cùng phạm vi với GET /work-shifts: ca ngoài phạm vi (trung tâm khác, hoặc DRAFT với caregiver) trả 404 như không
     * tồn tại — không lộ ca nháp / ca của trung tâm khác qua đoán id.
     */
    private void checkReadScope(UUID actorId, WorkShift shift) {
        if (providerAuthorization.isAdmin()) {
            return;
        }
        UUID ownCenterId;
        try {
            ownCenterId = resolveListCenterId(actorId, null);
        } catch (AppException exception) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND);
        }
        boolean hiddenDraft = isCaregiverOnly() && shift.getShiftStatus() == ShiftStatus.DRAFT;
        if (!shift.getCenter().getId().equals(ownCenterId) || hiddenDraft) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND);
        }
    }

    // Caregiver thuần (không kiêm manager/admin) mới bị ẩn ca DRAFT.
    private boolean isCaregiverOnly() {
        return providerAuthorization.hasRole(RoleCode.CAREGIVER)
                && !providerAuthorization.isAdmin()
                && !providerAuthorization.hasRole(RoleCode.CENTER_MANAGER);
    }

    // Một query gom nhóm cho cả trang thay vì một count mỗi ca (N+1).
    private Map<UUID, Long> countApprovedByShift(List<WorkShift> shifts) {
        Map<UUID, Long> approvedCounts = new HashMap<>();
        if (shifts.isEmpty()) {
            return approvedCounts;
        }
        List<UUID> shiftIds = shifts.stream()
                .map(WorkShift::getId)
                .toList();
        for (ShiftApprovedCount count : shiftRegistrationRepository.countByShiftIds(shiftIds, RegistrationStatus.APPROVED)) {
            approvedCounts.put(count.getShiftId(), count.getApprovedCount());
        }
        return approvedCounts;
    }

    // shiftType lưu dạng chữ hoa để lọc/so sánh ổn định; note rỗng → null.
    private void normalizeFields(WorkShift shift) {
        shift.setShiftType(shift.getShiftType().trim().toUpperCase(Locale.ROOT));
        shift.setNote(normalize(shift.getNote()));
    }

    private void validateShiftRequest(WorkShiftRequest request, boolean update) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new AppException(ErrorCode.WORK_SHIFT_TIME_INVALID);
        }
        // Giờ ca là giờ Việt Nam: so với "bây giờ" theo cùng múi giờ, không theo UTC (lệch 7 giờ).
        Instant shiftStart = ProviderTime.toInstant(request.getWorkDate(), request.getStartTime());
        if (!update && !shiftStart.isAfter(clock.instant())) {
            throw new AppException(ErrorCode.WORK_SHIFT_DATE_INVALID);
        }
        if (request.getRegistrationDeadline() != null
                && !request.getRegistrationDeadline().isBefore(shiftStart)) {
            throw new AppException(ErrorCode.WORK_SHIFT_DATE_INVALID);
        }
        if (!update && request.getShiftStatus() != ShiftStatus.DRAFT && request.getShiftStatus() != ShiftStatus.OPEN) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }

    private boolean hasScheduleFieldsChanged(WorkShift shift, WorkShiftRequest request) {
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
            Instant end = ProviderTime.toInstant(shift.getWorkDate(), shift.getEndTime());
            if (end.isAfter(clock.instant())) {
                throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
            }
        }
    }

    private void checkVersion(WorkShift shift, Long version) {
        if (version == null || !version.equals(shift.getVersion())) {
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
